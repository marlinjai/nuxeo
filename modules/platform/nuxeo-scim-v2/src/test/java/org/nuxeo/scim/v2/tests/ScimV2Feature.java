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

import javax.inject.Inject;

import org.nuxeo.ecm.webengine.test.WebEngineFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.ServletContainerFeature;

/**
 * SCIM 2.0 test feature.
 *
 * @since 2023.13
 */
@Features(WebEngineFeature.class)
@Deploy("org.nuxeo.usermapper")
@Deploy("org.nuxeo.ecm.automation.scripting")
@Deploy("org.nuxeo.ecm.automation.core")
@Deploy("org.nuxeo.scim.v2")
@Deploy("org.nuxeo.scim.v2:test-scim-v2-runtime-server-contrib.xml")
public class ScimV2Feature implements RunnerFeature {

    @Inject // NOSONAR
    protected ServletContainerFeature servletContainerFeature;

    public String getScimV2URL() {
        int port = servletContainerFeature.getPort();
        return "http://localhost:" + port + "/scim/v2";
    }
}
