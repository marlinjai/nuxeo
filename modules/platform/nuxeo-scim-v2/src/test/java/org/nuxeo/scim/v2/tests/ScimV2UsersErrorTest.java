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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * SCIM 2.0 /Users endpoint error tests.
 *
 * @since 2023.13
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
public class ScimV2UsersErrorTest {

    @Inject
    protected ScimV2Feature scimV2Feature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(() -> scimV2Feature.getScimV2URL());

    @Test
    public void testCreateUserNoUserResource() {
        httpClient.buildPostRequest("/Users") // NOSONAR
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), ScimV2Feature::assertError);
    }

    @Test
    public void testCreateUserAttributeUndefined() {
        String userResource = """
                {
                    "foo":"bar"
                }""";
        httpClient.buildPostRequest("/Users")
                  .entity(userResource)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), ScimV2Feature::assertError);
    }

    @Test
    public void testCreateUserNoUsername() {
        httpClient.buildPostRequest("/Users")
                  .entity("{}")
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), ScimV2Feature::assertError);
    }

    @Test
    public void testGetUserNotFound() {
        httpClient.buildGetRequest("/Users/joe") // NOSONAR
                  .executeAndConsume(new JsonNodeHandler(SC_NOT_FOUND), ScimV2Feature::assertError);
    }

    @Test
    public void testUpdateUserNoUserResource() {
        httpClient.buildPutRequest("/Users/joe")
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), ScimV2Feature::assertError);
    }

    @Test
    public void testUpdateUserNotFound() {
        String userResource = """
                {
                    "displayName":"Joe Doe"
                }""";
        httpClient.buildPutRequest("/Users/joe")
                  .entity(userResource)
                  .executeAndConsume(new JsonNodeHandler(SC_NOT_FOUND), ScimV2Feature::assertError);
    }

    @Test
    public void testDeleteUserNotFound() {
        httpClient.buildDeleteRequest("/Users/joe")
                  .executeAndConsume(new JsonNodeHandler(SC_NOT_FOUND), ScimV2Feature::assertError);
    }

}