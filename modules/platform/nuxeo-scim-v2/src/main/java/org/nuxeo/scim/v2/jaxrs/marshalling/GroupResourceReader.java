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
package org.nuxeo.scim.v2.jaxrs.marshalling;

import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;

import javax.ws.rs.ext.Provider;

import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonReader;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;

import com.fasterxml.jackson.databind.JsonNode;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.utils.JsonUtils;

/**
 * SCIM 2.0 {@link GroupResource} JSON reader.
 *
 * @since 2023.13
 */
@Provider
@Setup(mode = SINGLETON, priority = REFERENCE)
public class GroupResourceReader extends AbstractJsonReader<GroupResource> {

    @Override
    public GroupResource read(JsonNode jn) throws IOException {
        return JsonUtils.nodeToValue(jn, GroupResource.class);
    }

}
