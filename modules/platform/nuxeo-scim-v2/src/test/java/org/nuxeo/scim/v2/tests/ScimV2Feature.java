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
package org.nuxeo.scim.v2.tests;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.inject.Inject;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.test.AutomationServerFeature;
import org.nuxeo.ecm.webengine.test.WebEngineFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.ServletContainerFeature;
import org.skyscreamer.jsonassert.JSONAssert;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * SCIM 2.0 test feature.
 *
 * @since 2023.14
 */
@Features({ AutomationServerFeature.class, WebEngineFeature.class })
@Deploy("org.nuxeo.scim.v2")
@Deploy("org.nuxeo.usermapper")
@Deploy("org.nuxeo.scim.v2:test-scim-v2-runtime-server-contrib.xml")
public class ScimV2Feature implements RunnerFeature {

    @Inject
    protected ServletContainerFeature servletContainerFeature;

    public static void assertError(JsonNode node) {
        JsonNode schemas = node.get("schemas");
        assertTrue(schemas.isArray());
        assertEquals(1, schemas.size());
        assertEquals("urn:ietf:params:scim:api:messages:2.0:Error", schemas.get(0).asText());
    }

    public String getScimV2URL() {
        return servletContainerFeature.getHttpUrl() + "/scim/v2";
    }

    public void assertJSON(String expectedFile, JsonNode actualNode) throws IOException {
        var file = FileUtils.getResourceFileFromContext(expectedFile);
        var expected = readFileToString(file, UTF_8).replace("$PORT",
                String.valueOf(servletContainerFeature.getPort()));
        JSONAssert.assertEquals(expected, actualNode.toString(), true);
    }

}
