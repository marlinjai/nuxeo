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
 *     Antoine Taillefer
 */
package org.nuxeo.scim.v2.rest.marshalling;

import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.ext.Provider;

import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriter;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;

import com.fasterxml.jackson.core.JsonGenerator;
import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.utils.JsonUtils;

/**
 * SCIM 2.0 {@link ScimResource} list JSON writer.
 *
 * @since 2023.13
 */
@Provider
@Setup(mode = SINGLETON, priority = REFERENCE)
public class ResourceListWriter extends AbstractJsonWriter<List<ScimResource>> {

    @Override
    public void write(List<ScimResource> entity, JsonGenerator jg) throws IOException {
        jg.writeStartArray();
        for (ScimResource resource : entity) {
            jg.writeTree(JsonUtils.valueToNode(resource));
        }
        jg.writeEndArray();
    }

}
