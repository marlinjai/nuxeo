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
package org.nuxeo.scim.v2.tests;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.function.ThrowableConsumer;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.scim.v2.tests.compliance.ScimV2ComplianceTest;

/**
 * SCIM 2.0 /Schemas endpoint tests not covered by {@link ScimV2ComplianceTest}.
 *
 * @since 2023.14
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
public class ScimV2SchemasTest {

    @Inject
    protected ScimV2Feature scimV2Feature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(() -> scimV2Feature.getScimV2URL());

    @Test
    public void testGetSchemaNotFound() {
        httpClient.buildGetRequest("/Schemas/foo")
                  .executeAndConsume(new JsonNodeHandler(SC_NOT_FOUND), ScimV2Feature::assertError);
    }

    @Test
    public void testGetSchemaUser() {
        httpClient.buildGetRequest("/Schemas/urn:ietf:params:scim:schemas:core:2.0:User")
                  .executeAndConsume(new JsonNodeHandler(),
                          ThrowableConsumer.asConsumer(node -> scimV2Feature.assertJSON("schema_user.json", node)));
    }

    @Test
    public void testGetSchemaGroup() {
        httpClient.buildGetRequest("/Schemas/urn:ietf:params:scim:schemas:core:2.0:Group")
                  .executeAndConsume(new JsonNodeHandler(),
                          ThrowableConsumer.asConsumer(node -> scimV2Feature.assertJSON("schema_group.json", node)));
    }

}
