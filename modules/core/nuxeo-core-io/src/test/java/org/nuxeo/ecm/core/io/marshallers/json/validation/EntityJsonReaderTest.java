/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.nuxeo.ecm.core.io.marshallers.json.validation;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;

import org.junit.Test;
import org.nuxeo.ecm.core.io.marshallers.json.EntityJsonReader;
import org.nuxeo.ecm.core.io.registry.MarshallingException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 2023.15
 */
public class EntityJsonReaderTest {

    @Test
    public void testBadEntityType() throws IOException {
        var jn = new ObjectMapper().readTree("{\"entity-type\":\"wrongType\"}");
        var t = assertThrows(MarshallingException.class, () -> new EntityJsonReader<>("expectedType") {
            @Override
            protected Object readEntity(JsonNode jn) {
                return null;
            }
        }.read(jn));
        assertEquals("Json object entity-type is wrong. Expected is expectedType but was wrongType", t.getMessage());
        assertEquals(SC_BAD_REQUEST, t.getStatusCode());
    }
}
