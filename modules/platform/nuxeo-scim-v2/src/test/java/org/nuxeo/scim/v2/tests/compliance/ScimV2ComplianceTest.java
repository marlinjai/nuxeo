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
package org.nuxeo.scim.v2.tests.compliance;

import static org.junit.Assert.assertTrue;
import static org.nuxeo.common.test.logging.NuxeoLoggingConstants.APPENDER_CONSOLE_INFO;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LoggerLevel;
import org.nuxeo.scim.v2.api.ScimV2MappingService;
import org.nuxeo.scim.v2.tests.ScimV2Feature;
import org.wso2.scim2.testsuite.core.entities.TestResult;
import org.wso2.scim2.testsuite.core.entities.Wire;
import org.wso2.scim2.testsuite.core.exception.ComplianceException;
import org.wso2.scim2.testsuite.core.exception.GeneralComplianceException;
import org.wso2.scim2.testsuite.core.protocol.EndpointFactory;
import org.wso2.scim2.testsuite.core.tests.ResourceType;
import org.wso2.scim2.testsuite.core.utils.ComplianceConstants;

/**
 * SCIM 2.0 compliance tests, relying on the default {@link ConfigurableUserMapper}.
 * <p>
 * Based on the <a href="https://github.com/wso2-incubator/scim2-compliance-test-suite">SCIM 2.0 Compliance Test
 * Suite</a>.
 *
 * @since 2023.14
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
@Deploy("org.nuxeo.scim.v2:test-scim-v2-user-schema-override.xml")
@Deploy("org.nuxeo.scim.v2:test-scim-v2-mapping-contrib.xml")
@LoggerLevel(name = "org.nuxeo.scim.v2.tests.compliance", level = "DEBUG", appenders = APPENDER_CONSOLE_INFO)
public class ScimV2ComplianceTest {

    private static final Logger log = LogManager.getLogger(ScimV2ComplianceTest.class);

    protected static List<TestResult> testResults;

    @Inject
    protected ScimV2Feature scimV2Feature;

    protected ResourceType serviceProviderConfig;

    protected ResourceType schema;

    protected ResourceType resourceType;

    protected ResourceType user;

    protected ResourceType group;

    @BeforeClass
    public static void begin() {
        testResults = new ArrayList<>();
        log.info("Running SCIM 2.0 compliancy tests");
        log.info("Mapper class: {}",
                Framework.getService(ScimV2MappingService.class).getMapping().getClass().getSimpleName());
        log.info("---------------------------------");
    }

    @AfterClass
    public static void end() {
        int nbRun = testResults.size();
        int nbSuccess = 0;
        int nbSkipped = 0;
        int nbFailed = 0;
        for (TestResult testResult : testResults) {
            if (TestResult.SUCCESS == testResult.getStatus()) {
                nbSuccess++;
            } else if (TestResult.SKIPPED == testResult.getStatus()) {
                nbSkipped++;
            } else if (TestResult.ERROR == testResult.getStatus()) {
                nbFailed++;
            }
        }
        log.info("---------------------------------");
        log.info("Ran {} SCIM 2.0 compliancy tests: ", nbRun);
        log.info("  {} success ", nbSuccess);
        log.info("  {} skipped ", nbSkipped);
        log.info("  {} failed ", nbFailed);
    }

    @Before
    public void init() {
        var url = scimV2Feature.getScimV2URL();

        var endpointFactory = new EndpointFactory(url, "Administrator", "Administrator", "");
        serviceProviderConfig = endpointFactory.getInstance(
                ComplianceConstants.EndPointConstants.SERVICEPROVIDERCONFIG);
        schema = endpointFactory.getInstance(ComplianceConstants.EndPointConstants.SCHEMAS);
        resourceType = endpointFactory.getInstance(ComplianceConstants.EndPointConstants.RESOURCETYPE);
        user = endpointFactory.getInstance(ComplianceConstants.EndPointConstants.USER);
        group = endpointFactory.getInstance(ComplianceConstants.EndPointConstants.GROUP);
    }

    protected static void verifyTests(List<TestResult> results) {
        for (TestResult result : results) {
            ReadableTestResult testResult = new ReadableTestResult(result);
            log.info(testResult.getDisplay());
            Wire wire = testResult.getWire();
            if (wire != null) {
                log.debug("Request: {}", wire.getToServer());
                log.debug("Response: {}", wire.getFromServer());
            }
            assertTrue(testResult.getErrorMessage(), !testResult.isFailed());
        }
    }

    protected void runTest(Callable<List<TestResult>> test) {
        try {
            List<TestResult> results = test.call();
            testResults.addAll(results);
            verifyTests(results);
        } catch (Exception e) {
            String message;
            if (e instanceof ComplianceException ce) {
                message = ce.getDetail();
            } else if (e instanceof GeneralComplianceException gce) {
                message = gce.getResult().getMessage();
            } else {
                message = e.getMessage();
            }
            throw new AssertionError(message, e);
        }
    }

    @Test
    public void testServiceProviderConfig() {
        runTest(serviceProviderConfig::getMethodTest);
    }

    @Test
    public void testSchemas() {
        runTest(schema::getMethodTest);
    }

    @Test
    public void testResourceTypes() {
        runTest(resourceType::getMethodTest);
    }

    @Test
    public void testUsers() {
        runTest(user::postMethodTest);
        runTest(user::getByIdMethodTest);
        runTest(user::putMethodTest);
        runTest(user::deleteMethodTest);
        runTest(user::getMethodTest);
        runTest(user::searchMethodTest);
        runTest(user::patchMethodTest);
    }

    @Test
    public void testGroups() {
        runTest(group::postMethodTest);
        runTest(group::getByIdMethodTest);
        runTest(group::putMethodTest);
        runTest(group::deleteMethodTest);
        runTest(group::getMethodTest);
        runTest(group::searchMethodTest);
        runTest(group::patchMethodTest);
    }

}
