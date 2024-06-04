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
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.scim.v2.mapper.ConfigurableUserMapper;
import org.nuxeo.scim.v2.mapper.UserMapperFactory;
import org.nuxeo.scim.v2.tests.ScimV2Feature;
import org.wso2.charon3.core.attributes.ComplexAttribute;
import org.wso2.charon3.core.attributes.SimpleAttribute;
import org.wso2.charon3.core.exceptions.CharonException;
import org.wso2.charon3.core.schema.SCIMDefinitions.DataType;

import info.wso2.scim2.compliance.entities.TestResult;
import info.wso2.scim2.compliance.entities.Wire;
import info.wso2.scim2.compliance.exception.ComplianceException;
import info.wso2.scim2.compliance.exception.CriticalComplianceException;
import info.wso2.scim2.compliance.objects.SCIMServiceProviderConfig;
import info.wso2.scim2.compliance.protocol.ComplianceTestMetaDataHolder;

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
public class ScimV2ComplianceTest {

    private static final Logger log = LogManager.getLogger(ScimV2ComplianceTest.class);

    protected static List<TestResult> testResults;

    @Inject
    protected ScimV2Feature scimV2Feature;

    protected ComplianceTestMetaDataHolder complianceTestMetaDataHolder;

    @BeforeClass
    public static void begin() {
        testResults = new ArrayList<>();
        log.info("Running SCIM 2.0 compliancy tests");
        log.info("Mapper class: {}", UserMapperFactory.getMapperClass());
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
        log.info("   {} success ", nbSuccess);
        log.info("   {} skipped ", nbSkipped);
        log.info("   {} failed ", nbFailed);
    }

    @Before
    public void init() throws CharonException {
        var url = scimV2Feature.getScimV2URL();

        complianceTestMetaDataHolder = new ComplianceTestMetaDataHolder();
        complianceTestMetaDataHolder.setUrl(url);
        complianceTestMetaDataHolder.setUsername("Administrator");
        complianceTestMetaDataHolder.setPassword("Administrator");

        SCIMServiceProviderConfig serviceProviderConfig = new SCIMServiceProviderConfig();

        // TODO: support PATCH
        ComplexAttribute patchAttribute = new ComplexAttribute("patch");
        SimpleAttribute supportedAttribute = new SimpleAttribute("supported", false);
        supportedAttribute.setType(DataType.BOOLEAN);
        patchAttribute.setSubAttribute(supportedAttribute);
        serviceProviderConfig.setAttribute(patchAttribute);

        complianceTestMetaDataHolder.setScimServiceProviderConfig(serviceProviderConfig);
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

    protected <T extends ScimV2EndpointTest> void runTest(Class<T> testClass) {
        try {
            ScimV2EndpointTest test = testClass.getConstructor(ComplianceTestMetaDataHolder.class)
                                               .newInstance(complianceTestMetaDataHolder);
            List<TestResult> results = test.performTest();
            testResults.addAll(results);
            verifyTests(results);
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException
                | CriticalComplianceException e) {
            throw new NuxeoException(e);
        } catch (ComplianceException e) {
            fail(e.getDetail());
        }
    }

    @Ignore
    @Test
    public void testFilter() {
        runTest(ScimV2FilterTest.class);
    }

    @Test
    public void testGroups() {
        runTest(ScimV2GroupTest.class);
    }

    @Ignore
    @Test
    public void testList() {
        runTest(ScimV2ListTest.class);
    }

    @Ignore
    @Test
    public void testPagination() {
        runTest(ScimV2PaginationTest.class);
    }

    @Ignore
    @Test
    public void testResourceTypes() {
        runTest(ScimV2ResourceTypesTest.class);
    }

    @Test
    public void testSchemas() {
        runTest(ScimV2SchemaTest.class);
    }

    @Ignore
    @Test
    public void testServiceProviderConfig() {
        runTest(ScimV2ServiceProviderConfigTest.class);
    }

    @Test
    public void testUsers() {
        runTest(ScimV2UserTest.class);
    }

    @Ignore
    @Test
    public void testSort() {
        runTest(ScimV2SortTest.class);
    }

    @Ignore
    @Test
    public void testBulk() {
        runTest(ScimV2BulkTest.class);
    }

}
