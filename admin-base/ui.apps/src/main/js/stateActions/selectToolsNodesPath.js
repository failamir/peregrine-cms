/*-
 * #%L
 * admin base - UI Apps
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
import { LoggerFactory } from '../logger'
let log = LoggerFactory.logger('selectToolsNodesPath').setLevelDebug()

import {set} from '../utils'

export default function(me, target) {

    log.fine(target)

    let view = me.getView()

    return new Promise( (resolve, reject) => { 
        if(target.selected.startsWith('/content/sites')) {
            set(view, '/state/tools/page', null)
        } else {
            set(view, '/state/tools/template', null)
        }
    
        me.getApi().populateNodesForBrowser(target.selected).then( () => {
            set(me.getView(), target.path, target.selected)
            let path = document.location.pathname
            let html = path.indexOf('.html')
            let newPath = path.slice(0,html) + '.html/path:'+target.selected
            history.pushState({peregrinevue:true, path: newPath}, newPath, newPath)
            resolve()
        })
    })
}
