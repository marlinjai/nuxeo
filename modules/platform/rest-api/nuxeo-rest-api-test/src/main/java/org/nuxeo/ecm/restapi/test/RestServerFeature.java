/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     dmetzler
 */
package org.nuxeo.ecm.restapi.test;

import javax.inject.Inject;

import org.nuxeo.ecm.automation.test.AutomationServerFeature;
import org.nuxeo.ecm.webengine.test.WebEngineFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.ServletContainerFeature;

/**
 * @since 5.7.2
 */
@Features({ WebEngineFeature.class, AutomationServerFeature.class })
@Deploy("org.nuxeo.ecm.automation.test")
@Deploy("org.nuxeo.ecm.platform.url")
@Deploy("org.nuxeo.ecm.platform.types")
@Deploy("org.nuxeo.ecm.platform.restapi.io")
@Deploy("org.nuxeo.ecm.platform.restapi.test")
@Deploy("org.nuxeo.ecm.platform.restapi.server")
@Deploy("org.nuxeo.ecm.platform.tag")
public class RestServerFeature implements RunnerFeature {

    @Inject
    protected ServletContainerFeature servletContainerFeature;

    /**
     * @since 2023.13
     */
    public String getRestApiUrl() {
        return servletContainerFeature.getHttpUrl() + "/api/v1";
    }
}
