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

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * SCIM 2.0 endpoint tests, verifying calls made as a non administrator user.
 *
 * @since 2023.14
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
public class ScimV2NotAdminTest {

    protected static final String USERNAME = "joe";

    protected static final String PASSWORD = "jack";

    protected static final JsonNodeHandler FORBIDDEN_HANDLER = new JsonNodeHandler(SC_FORBIDDEN);

    @Inject
    protected UserManager userManager;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Inject
    protected ScimV2Feature scimV2Feature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.builder()
                                                                   .url(() -> scimV2Feature.getScimV2URL())
                                                                   .credentials(USERNAME, PASSWORD)
                                                                   .build();

    @Before
    public void init() {
        DocumentModel model = userManager.getBareUserModel();
        model.setPropertyValue("user:username", USERNAME);
        model.setPropertyValue("user:password", PASSWORD);
        userManager.createUser(model);
        transactionalFeature.nextTransaction();
    }

    @After
    public void tearDown() {
        userManager.deleteUser(USERNAME);
    }

    // -------------------- /Users --------------------
    @Test
    public void testCreateUserForbidden() {
        httpClient.buildPostRequest("/Users").executeAndConsume(FORBIDDEN_HANDLER, ScimV2Feature::assertError);
    }

    @Test
    public void testUpdateUserForbidden() {
        httpClient.buildPutRequest("/Users/joe").executeAndConsume(FORBIDDEN_HANDLER, ScimV2Feature::assertError);
    }

    @Test
    public void testDeleteUserForbidden() {
        httpClient.buildDeleteRequest("/Users/joe").executeAndConsume(FORBIDDEN_HANDLER, ScimV2Feature::assertError);
    }

    // -------------------- /Groups --------------------
    @Test
    public void testCreateGroupForbidden() {
        httpClient.buildPostRequest("/Groups").executeAndConsume(FORBIDDEN_HANDLER, ScimV2Feature::assertError);
    }

    @Test
    public void testUpdateGroupForbidden() {
        httpClient.buildPutRequest("/Groups/people").executeAndConsume(FORBIDDEN_HANDLER, ScimV2Feature::assertError);
    }

    @Test
    public void testDeleteGroupForbidden() {
        httpClient.buildDeleteRequest("/Groups/people")
                  .executeAndConsume(FORBIDDEN_HANDLER, ScimV2Feature::assertError);
    }

}
