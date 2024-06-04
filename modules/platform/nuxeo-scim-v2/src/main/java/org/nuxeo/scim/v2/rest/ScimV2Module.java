/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thierry Delprat
 *     Antoine Taillefer
 */
package org.nuxeo.scim.v2.rest;

import java.util.HashSet;
import java.util.Set;

import org.nuxeo.ecm.webengine.app.JsonNuxeoExceptionWriter;
import org.nuxeo.ecm.webengine.app.WebEngineExceptionMapper;
import org.nuxeo.ecm.webengine.app.WebEngineModule;
import org.nuxeo.ecm.webengine.jaxrs.coreiodelegate.CoreIODelegate;

/**
 * @since 2023.13
 */
public class ScimV2Module extends WebEngineModule {

    @Override
    public Set<Object> getSingletons() {
        Set<Object> result = new HashSet<>();
        result.add(new CoreIODelegate());
        result.add(new JsonNuxeoExceptionWriter());
        result.add(new WebEngineExceptionMapper());
        return result;
    }

}
