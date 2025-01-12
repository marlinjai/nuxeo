/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.forms.layout.export;

import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;

import org.apache.commons.collections4.CollectionUtils;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriter;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.platform.forms.layout.api.LayoutTypeDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.impl.LayoutTypeDefinitionComparator;

import com.fasterxml.jackson.core.JsonGenerator;

/**
 * @since 6.0
 * @since 10.1 converted to a marshaller
 */
@Setup(mode = SINGLETON, priority = REFERENCE)
public class LayoutTypeDefinitionsJsonWriter extends AbstractJsonWriter<LayoutTypeDefinitions> {

    @Override
    public void write(LayoutTypeDefinitions entities, JsonGenerator jg) throws IOException {
        jg.writeStartObject();
        if (CollectionUtils.isNotEmpty(entities)) {
            // sort so that order is deterministic
            entities.sort(new LayoutTypeDefinitionComparator());
            for (LayoutTypeDefinition entity : entities) {
                writeEntityField(entity.getName(), entity, jg);
            }
        }
        jg.writeEndObject();
    }

}
