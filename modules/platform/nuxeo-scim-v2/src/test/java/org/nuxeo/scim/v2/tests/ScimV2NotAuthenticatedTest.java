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

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * SCIM 2.0 endpoint tests, verifying calls made as a non authenticated user.
 *
 * @since 2023.14
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
@Deploy("org.nuxeo.scim.v2:test-scim-v2-authentication-contrib.xml")
public class ScimV2NotAuthenticatedTest {

    @Inject
    protected ScimV2Feature scimV2Feature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.builder()
                                                                   .url(() -> scimV2Feature.getScimV2URL())
                                                                   .build();

    @Test
    public void testUnauthorized() {
        httpClient.buildGetRequest("/Users")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_UNAUTHORIZED, status.intValue()));
    }

}
