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
package org.nuxeo.scim.v2.tests.compliance;

import info.wso2.scim2.compliance.protocol.ComplianceTestMetaDataHolder;
import info.wso2.scim2.compliance.tests.UserTest;

/**
 * SCIM 2.0 /Users endpoint compliance test.
 *
 * @since 2023.13
 */
public class ScimV2UserTest extends UserTest implements ScimV2EndpointTest {

    public ScimV2UserTest(ComplianceTestMetaDataHolder complianceTestMetaDataHolder) {
        super(complianceTestMetaDataHolder);
    }

}