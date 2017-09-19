package com.peregrine.admin.replication.impl;

/*-
 * #%L
 * admin base - Core
 * %%
 * Copyright (C) 2017 headwire inc.
 * %%
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */

import com.peregrine.admin.replication.AbstractionReplicationService;
import com.peregrine.admin.replication.ReferenceLister;
import com.peregrine.admin.replication.Replication;
import com.peregrine.admin.replication.ReplicationUtil;
import com.peregrine.commons.util.PerUtil;
import com.peregrine.commons.util.PerUtil.MatchingResourceChecker;
import com.peregrine.commons.util.PerUtil.MissingOrOutdatedResourceChecker;
import com.peregrine.commons.util.PerUtil.ResourceChecker;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.peregrine.admin.replication.ReplicationUtil.updateReplicationProperties;
import static com.peregrine.commons.util.PerConstants.PER_REPLICATED;
import static com.peregrine.commons.util.PerConstants.PER_REPLICATED_BY;
import static com.peregrine.commons.util.PerConstants.PER_REPLICATION_REF;
import static com.peregrine.commons.util.PerUtil.getModifiableProperties;
import static com.peregrine.commons.util.PerUtil.getProperties;
import static com.peregrine.commons.util.PerUtil.getResource;

/**
 * Created by schaefa on 5/25/17.
 */
@Component(
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    service = Replication.class,
    immediate = true
)
@Designate(ocd = LocalReplicationService.Configuration.class, factory = true)
public class LocalReplicationService
    extends AbstractionReplicationService
{
    @ObjectClassDefinition(
        name = "Peregrine: Local Replication Service",
        description = "Each instance provides the configuration for a Local Replication"
    )
    @interface Configuration {
        @AttributeDefinition(
            name = "Name",
            description = "Name of the Replication Service",
            required = true
        )
        String name();
        @AttributeDefinition(
            name = "Description",
            description = "Description of this Replication Service",
            required = true
        )
        String description();
        @AttributeDefinition(
            name = "Local Mapping",
            description = "JCR Root Path Mapping: <source path>=<target path> (only used if this is local). Anything outside will not be copied.",
            required = true
        )
        String localMapping();
    }
    @Activate
    @SuppressWarnings("unused")
    void activate(Configuration configuration) { setup(configuration); }
    @Modified
    @SuppressWarnings("unused")
    void modified(Configuration configuration) { setup(configuration); }

    private String localSource;
    private String localTarget;
    private String destinationUrl;

    private void setup(Configuration configuration) {
        init(configuration.name(), configuration.description());
        localSource = localTarget = null;
        String mapping = configuration.localMapping();
        String[] tokens = mapping.split("=");
        if(tokens.length == 2) {
            localSource = tokens[0];
            localTarget = tokens[1];
        } else {
            throw new IllegalArgumentException("Local Mapping has the wrong format: '" + mapping + "'");
        }
        destinationUrl = null;
        if(localSource == null || localSource.isEmpty() || !localSource.startsWith("/")) {
            throw new IllegalArgumentException("For local Replication local mapping needs to provide a local source (before =) that starts with a '/'. Mapping was: " + mapping);
        }
        if(localTarget == null || localTarget.isEmpty() || !localTarget.startsWith("/")) {
            throw new IllegalArgumentException("For local Replication local mapping needs to provide a local target (after =) that starts with a '/'. Mapping was: " + mapping);
        }
        if(localSource.endsWith("/")) {
            localSource = localSource.substring(0, localSource.length() - 1);
        }
        if(localTarget.endsWith("/")) {
            localTarget = localTarget.substring(0, localTarget.length() - 1);
        }
        log.trace("Local Replication Service Name: '{}' created", getName());
        log.trace("Local Source: '{}', Target: '{}'", localSource, localTarget);
    }

    @Reference
    @SuppressWarnings("unused")
    ResourceResolverFactory resourceResolverFactory;
    @Reference
    @SuppressWarnings("unused")
    private ReferenceLister referenceLister;

    @Override
    public List<Resource> replicate(Resource startingResource, boolean deep)
        throws ReplicationException
    {
        ResourceResolver resourceResolver = startingResource.getResourceResolver();
        Resource source = resourceResolver.getResource(localSource);
        if(source == null) {
            throw new ReplicationException("Local Source: '" + localSource + "' not found. Please fix the local mapping.");
        }
        Resource target = resourceResolver.getResource(localTarget);
        if(target == null) {
            throw new ReplicationException("Local Target: '" + localTarget + "' not found. Please fix the local mapping or create the local target.");
        }
        List<Resource> referenceList = referenceLister.getReferenceList(startingResource, true, source, target);
        List<Resource> replicationList = new ArrayList<>();
        ResourceChecker resourceChecker = new MissingOrOutdatedResourceChecker(source, target);
        // Need to check this list of they need to be replicated first
        for(Resource resource: referenceList) {
            if(resourceChecker.doAdd(resource)) {
                replicationList.add(resource);
            }
        }
        // This only returns the referenced resources. Now we need to check if there are any JCR Content nodes to be added as well
        for(Resource reference: new ArrayList<Resource>(replicationList)) {
            PerUtil.listMissingResources(reference, replicationList, resourceChecker, false);
        }
        PerUtil.listMissingParents(startingResource, replicationList, source, resourceChecker);
        PerUtil.listMissingResources(startingResource, replicationList, resourceChecker, deep);
        return replicate(replicationList);
    }

    @Override
    public List<Resource> deactivate(Resource startingResource)
        throws ReplicationException
    {
        ResourceResolver resourceResolver = startingResource.getResourceResolver();
        Resource source = resourceResolver.getResource(localSource);
        if(source == null) {
            throw new ReplicationException("Local Source: '" + localSource + "' not found. Please fix the local mapping.");
        }
        Resource target = resourceResolver.getResource(localTarget);
        if(target == null) {
            throw new ReplicationException("Local Target: '" + localTarget + "' not found. Please fix the local mapping or create the local target.");
        }

        List<Resource> replicationList = new ArrayList<>(Arrays.asList(startingResource));
        ResourceChecker resourceChecker = new MatchingResourceChecker(source, target);
        PerUtil.listMatchingResources(startingResource, replicationList, resourceChecker, true);
        return deactivate(startingResource, replicationList);
    }

    @Override
    public List<Resource> replicate(List<Resource> resourceList) throws ReplicationException {
        List<Resource> answer = new ArrayList<>();
        // Replicate the resources
        ResourceResolver resourceResolver = null;
        for(Resource item: resourceList) {
            if(item != null) {
                resourceResolver = item.getResourceResolver();
                break;
            }
        }
        if(resourceResolver != null) {
            Resource source = resourceResolver.getResource(localSource);
            if(source == null) {
                throw new ReplicationException("Local Source: '" + localSource + "' not found. Please fix the local mapping.");
            }
            Resource target = resourceResolver.getResource(localTarget);
            if(target == null) {
                throw new ReplicationException("Local Target: '" + localTarget + "' not found. Please fix the local mapping or create the local target.");
            }
            // Prepare the Mappings for the Properties mapping
            Map<String, String> pathMapping = new HashMap<>();
            for(Resource item: resourceList) {
                if(item != null) {
                    String relativePath = PerUtil.relativePath(source, item);
                    if(relativePath != null) {
                        String targetPath = localTarget + '/' + relativePath;
                        log.trace("Add to Path mappings Source Path: '{}', Target Path: '{}'", item.getPath(), targetPath);
                        pathMapping.put(item.getPath(), targetPath);
                        // References need to be updated through the Path Mappings therefore we revisit them here
                        List<Resource> referenceList = referenceLister.getReferenceList(item, true, source, target);
                        for(Resource reference: referenceList) {
                            relativePath = PerUtil.relativePath(source, reference);
                            if(relativePath != null) {
                                targetPath = localTarget + '/' + relativePath;
                                log.trace("Add to Path mappings Reference Source Path: '{}', Target Path: '{}'", reference.getPath(), targetPath);
                                pathMapping.put(reference.getPath(), targetPath);
                            }
                        }
                    } else {
                        log.warn("Given Resource: '{}' path does not start with local source path: '{}' -> ignore", item, localSource);
                    }
                }
            }
            Session session = resourceResolver.adaptTo(Session.class);
            for(Resource item: resourceList) {
                if(item != null) {
                    handleParents(item, answer, pathMapping, resourceResolver);
                }
            }
            try {
                session.save();
            } catch(RepositoryException e) {
                log.warn("Failed to save changes replicate parents", e);
            }
        }
        return answer;
    }

    /**
     * This method deactivates the given resource to deactivate it and then updates
     * the given list of source resources with the replication properties
     *
     * @param toBeDeleted The staring resource to be removed which removes all its children
     * @param resourceList The list of the source dependencies to be updated
     * @return List of all updated source dependencies
     * @throws ReplicationException
     */
    public List<Resource> deactivate(Resource toBeDeleted, List<Resource> resourceList) throws ReplicationException {
        List<Resource> answer = new ArrayList<>();
        // Replicate the resources
        ResourceResolver resourceResolver = null;
        for(Resource item: resourceList) {
            if(item != null) {
                resourceResolver = item.getResourceResolver();
                break;
            }
        }
        if(resourceResolver != null) {
            Resource source = resourceResolver.getResource(localSource);
            if(source == null) {
                throw new ReplicationException("Local Source: '" + localSource + "' not found. Please fix the local mapping.");
            }
            Resource target = resourceResolver.getResource(localTarget);
            if(target == null) {
                throw new ReplicationException("Local Target: '" + localTarget + "' not found. Please fix the local mapping or create the local target.");
            }
            // Update all replication targets by setting the new Replication Date, User and remove the Ref to indicate the deactivation
            for(Resource item: resourceList) {
                boolean replicationMixin = ReplicationUtil.supportsReplicationProperties(item);
                if(replicationMixin) {
                    ModifiableValueMap properties = getModifiableProperties(item, false);
                    Calendar replicated = Calendar.getInstance();
                    properties.put(PER_REPLICATED_BY, source.getResourceResolver().getUserID());
                    properties.put(PER_REPLICATED, replicated);
                    properties.put(PER_REPLICATION_REF, "");
                }
            }
            // Delete the replicated target resource
            String relativePath = PerUtil.relativePath(source, toBeDeleted);
            if(relativePath != null) {
                String targetPath = localTarget + '/' + relativePath;
                Resource targetResource = getResource(resourceResolver, targetPath);
                if(targetResource != null) {
                    try {
                        resourceResolver.delete(targetResource);
                    } catch(PersistenceException e) {
                        throw new ReplicationException("Failed to delete a target resource: " + targetPath, e);
                    }
                }
            } else {
                log.warn("Given Resource: '{}' path does not start with local source path: '{}' -> ignore", toBeDeleted, localSource);
            }
            Session session = resourceResolver.adaptTo(Session.class);
            try {
                session.save();
            } catch(RepositoryException e) {
                log.warn("Failed to save changes replicate parents", e);
            }
        }
        return answer;
    }

    private boolean handleParents(Resource resource, List<Resource> resourceList, Map<String, String> pathMapping, ResourceResolver resourceResolver) {
        String targetPath = pathMapping.get(resource.getPath());
        log.trace("Handle Parents, Resource: '{}', Target Path: '{}'", resource.getPath(), targetPath);
        if(targetPath != null) {
            try {
                //AS TODO: If the parent is not found because the are intermediate missing parents
                //AS TODO: we need to recursively go up the parents until we either find an existing parent and then create all its children on the way out
                //AS TODO: or we fail and ignore it
                String targetParent = PerUtil.getParent(targetPath);
                log.trace("Target Parent: '{}'", targetParent);
                if(targetParent == null) {
                    // No more parent -> handling parents failed
                    return false;
                }
                Resource targetParentResource = resourceResolver.getResource(targetParent);
                log.trace("Target Parent Resource: '{}'", targetParentResource == null ? "null" : targetParentResource.getPath());
                if(targetParentResource == null) {
                    // Parent does not exist so try with its parent
                    Resource parent = resource.getParent();
                    log.trace("Source Parent: '{}'", parent == null ? "null" : parent.getPath());
                    if(parent == null) {
                        // No more parent -> handling parents failed
                        return false;
                    }
                    log.trace("Recursive Handle Parents: '{}'", resource.getParent().getPath());
                    boolean ok = handleParents(resource.getParent(), resourceList, pathMapping, resourceResolver);
                    if(!ok) {
                        // Handling of parent failed -> leaving as failure
                        return false;
                    } else {
                        targetParentResource = resourceResolver.getResource(targetParent);
                        log.trace("Target Parent Resource(2): '{}'", targetParentResource == null ? "null" : targetParentResource.getPath());
                        if(targetParentResource == null) {
                            log.error("Target Parent:'" + targetParent + "' is still not found even after all parents were handled");
                        }
                    }
                }
                log.trace("Copy Resource: '{}' to Target: '{}'", resource.getPath(), targetParentResource.getPath());
                Resource copy = copy(resource, targetParentResource, pathMapping);
                resourceList.add(copy);
            } catch(PersistenceException e) {
                log.error("Failed to replicate resource: '{}' -> ignored", resource, e);
            }
        }
        return true;
    }

    /**
     * Copy of a Single Resource rather than the ResourceResolver's copy which copies the entire sub tree
     *
     * @param source Source to be copied
     * @param targetParent Parent Resource which the copy will be added to
     * @param pathMapping Path Mappings used to check and rewrite references
     * @return The newly created (copied) resource
     *
     * @throws PersistenceException If the resource could not be created like it already exists or parent missing
     */
    private Resource copy(Resource source, Resource targetParent, Map<String, String> pathMapping)
        throws PersistenceException
    {
        log.trace("Copy Resource: '{}', Target Parent Resource: '{}', Path Mappings: '{}'", source.getPath(), targetParent, pathMapping);
        Resource answer = null;
        Map<String, Object> newProperties = new HashMap<>();
        ModifiableValueMap properties = PerUtil.getModifiableProperties(source, false);
        for(String key : properties.keySet()) {
            if("jcr:uuid".equals(key)) {
                // UUIDs cannot be copied over -> ignore them
                continue;
            }
            Object value = properties.get(key);
            if(value instanceof String) {
                String stringValue = (String) value;
                String targetValue = pathMapping.get(value);
                log.trace("Is Property to be adjusted, name: '{}', value: '{}', mapped: '{}'", key, stringValue, targetValue);
                if(targetValue != null) {
                    log.trace("Adjusted Property. Key: '{}', Old Value: '{}', New Value: '{}'", new String[]{key, stringValue, targetValue});
                    value = targetValue;
                }
            }
            newProperties.put(key, value);
        }
        answer = targetParent.getChild(source.getName());
        if(answer == null) {
            ValueMap sourceProperties = getProperties(source, false);
            Map<String, Object> targetProperties = new HashMap<>();
            for(String key: sourceProperties.keySet()) {
                try {
                    targetProperties.put(key, newProperties.get(key));
                } catch(Exception e) {
                    // Ignore
                }
            }
            log.trace("Create Resource: '{}' on Parent: '{}', Properties: '{}'", source.getName(), targetParent.getPath(), targetProperties);
            answer = source.getResourceResolver().create(targetParent, source.getName(), targetProperties);
        }
        updateReplicationProperties(source, null, answer);
//        boolean replicationMixin = ReplicationUtil.supportsReplicationProperties(source);
//        if(replicationMixin) {
//            Calendar replicated = Calendar.getInstance();
//            properties.put(PER_REPLICATED_BY, source.getResourceResolver().getUserID());
//            properties.put(PER_REPLICATED, replicated);
//            properties.put(PER_REPLICATION_REF, targetParent.getPath());
//            newProperties.put(PER_REPLICATED_BY, source.getResourceResolver().getUserID());
//            newProperties.put(PER_REPLICATED, replicated);
//            newProperties.put(PER_REPLICATION_REF, source.getParent().getPath());
//        }
//        Resource newTarget = targetParent.getChild(source.getName());
//        if(newTarget != null) {
//            ModifiableValueMap newTargetProperties = PerUtil.getModifiableProperties(newTarget, false);
//            for(String key: newProperties.keySet()) {
//                try {
//                    newTargetProperties.put(key, newProperties.get(key));
//                } catch(Exception e) {
//                    // Ignore
//                }
//            }
//            answer = newTarget;
//        } else {
//            answer = source.getResourceResolver().create(targetParent, source.getName(), newProperties);
//        }
        return answer;
    }
}
