/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *       Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.bulk.action.SetPropertiesAction.ACTION_NAME;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;

import java.time.Duration;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 10.3
 */
@RunWith(FeaturesRunner.class)
@Features({ CoreBulkFeature.class, RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
public class BulkActionFrameworkTest {

    @Inject
    protected BulkService bulkService;

    @Inject
    protected CoreSession session;

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void testGetBulkStatus() throws Exception {
        // submit a bulk command to get its status
        String query = "SELECT * FROM Document WHERE ecm:isVersion = 0";
        String user = session.getPrincipal().getName();
        BulkCommand command = new BulkCommand.Builder(ACTION_NAME, query, user).repository(session.getRepositoryName())
                                                                               .param("dc:description",
                                                                                       "new description")
                                                                               .build();
        String commandId = bulkService.submit(command);
        assertTrue("Bulk action didn't finish", bulkService.await(commandId, Duration.ofSeconds(10)));

        // compute some variable to assert
        long count = session.query("SELECT * FROM Document WHERE ecm:isVersion = 0", null, 1, 0, true).totalSize();

        httpClient.buildGetRequest("bulk/" + commandId).executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals(count, node.get("total").asLong());
            assertEquals(count, node.get("processed").asLong());
            assertEquals(COMPLETED.name(), node.get("state").asText());
        });
    }

    @Test
    public void testGetNonExistingBulkStatus() {
        UUID commandId = UUID.randomUUID();
        httpClient.buildGetRequest("bulk/" + commandId)
                  .executeAndConsume(new JsonNodeHandler(SC_NOT_FOUND),
                          node -> assertEquals("Bulk command with id=" + commandId + " doesn't exist",
                                  node.get("message").asText()));
    }

}
