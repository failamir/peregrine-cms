package com.uswds.models;

import com.peregrine.nodetypes.models.AbstractComponent;
import com.peregrine.nodetypes.models.IComponent;
import com.peregrine.nodetypes.models.Container;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;

import javax.inject.Inject;
import javax.inject.Named;

/*
    //GEN[:DATA
    {
  "definitions": {
    "Docslayout": {
      "type": "object",
      "x-type": "container",
      "properties": {}
    }
  },
  "name": "Docslayout",
  "componentPath": "uswds/components/docslayout",
  "package": "com.uswds.models",
  "modelName": "Docslayout",
  "classNameParent": "AbstractComponent"
}
//GEN]
*/

//GEN[:DEF
@Model(
        adaptables = Resource.class,
        resourceType = "uswds/components/docslayout",
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
        adapters = IComponent.class
)
@Exporter(
        name = "jackson",
        extensions = "json"
)

//GEN]
public class DocslayoutModel extends Container {

    public DocslayoutModel(Resource r) { super(r); }

    //GEN[:INJECT
    
//GEN]

    //GEN[:GETTERS
    
//GEN]

}
