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
 *     Guillaume Renard
 */
package org.nuxeo.scim.v2.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * SCIM 2.0 /Users and /Groups endpoint included and excluded attributes tests.
 *
 * @since 2023.14
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
public class ScimV2IncludedExcludedAttributesTest {

    @Inject
    protected ScimV2Feature scimV2Feature;

    @Inject
    protected UserManager userManager;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.builder()
                                                                   .url(() -> scimV2Feature.getScimV2URL())
                                                                   .adminCredentials()
                                                                   .build();

    @Test
    public void testGetUser() {
        httpClient.buildGetRequest("/Users/Administrator?attributes=userName,groups")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode schemas = node.get("schemas"); // NOSONAR
                      assertTrue(schemas.isArray());
                      assertEquals(1, schemas.size());
                      assertEquals("urn:ietf:params:scim:schemas:core:2.0:User", schemas.get(0).asText());
                      assertNotNull(node.get("userName"));
                      assertNotNull(node.get("groups"));
                      assertNull(node.get("emails"));
                  });
        httpClient.buildGetRequest("/Users/Administrator?excludedAttributes=groups")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode schemas = node.get("schemas");
                      assertTrue(schemas.isArray());
                      assertEquals(1, schemas.size());
                      assertEquals("urn:ietf:params:scim:schemas:core:2.0:User", schemas.get(0).asText());
                      assertNull(node.get("groups"));
                      assertNotNull(node.get("emails"));
                  });
    }

    @Test
    public void testGetGroup() {
        httpClient.buildGetRequest("/Groups/administrators?attributes=id,members")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode schemas = node.get("schemas");
                      assertTrue(schemas.isArray());
                      assertEquals(1, schemas.size());
                      assertEquals("urn:ietf:params:scim:schemas:core:2.0:Group", schemas.get(0).asText());
                      assertNotNull(node.get("id"));
                      assertNotNull(node.get("members")); // NOSONAR
                  });
        httpClient.buildGetRequest("/Groups/members?excludedAttributes=members")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode schemas = node.get("schemas");
                      assertTrue(schemas.isArray());
                      assertEquals(1, schemas.size());
                      assertEquals("urn:ietf:params:scim:schemas:core:2.0:Group", schemas.get(0).asText());
                      assertNotNull(node.get("id"));
                      assertNull(node.get("members"));
                  });
    }

    @Test
    public void testSearchGroups() {
        httpClient.buildGetRequest("/Groups?filter=id%20eq%20%22administrators%22&attributes=displayName,members")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      var resources = node.get("Resources");
                      assertTrue(resources.isArray());
                      assertTrue(resources.hasNonNull(0));
                      assertNotNull(resources.get(0).get("displayName"));
                      assertNotNull(resources.get(0).get("members"));
                  });
        httpClient.buildGetRequest("/Groups?filter=id%20eq%20%22administrators%22&excludedAttributes=members")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      var resources = node.get("Resources");
                      assertTrue(resources.isArray());
                      assertTrue(resources.hasNonNull(0));
                      assertNotNull(resources.get(0).get("displayName"));
                      assertNull(resources.get(0).get("members"));
                  });
    }
}
