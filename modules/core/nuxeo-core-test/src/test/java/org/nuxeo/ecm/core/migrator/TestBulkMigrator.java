/*
 * (C) Copyright 2023 Nuxeo (http://nuxeo.com/) and others.
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
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.ecm.core.migrator;

import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.SYSTEM_USERNAME;
import static org.nuxeo.ecm.core.migrator.AbstractBulkMigrator.MIGRATION_PROCESSOR_NAME;
import static org.nuxeo.ecm.core.migrator.AbstractBulkMigrator.PARAM_MIGRATION_ID;
import static org.nuxeo.ecm.core.migrator.AbstractBulkMigrator.PARAM_MIGRATION_STEP;

import java.time.Duration;
import java.util.Objects;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.migrator.AbstractBulkMigrator.MigrationAction;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.migration.MigrationService;
import org.nuxeo.runtime.migration.MigrationServiceImpl;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 2023.0
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-dummy-bulk-migrator.xml")
public class TestBulkMigrator {

    @Inject
    protected CoreSession session;

    @Inject
    protected BulkService bulkService;

    @Inject
    protected MigrationService migrationService;

    @Inject
    protected StreamService streamService;

    @Inject
    protected TransactionalFeature txFeature;

    @Before
    public void setup() {
        for (var i = 0; i < 20; i++) {
            var doc = session.createDocumentModel("/", String.format("File%03d", i), "File");
            doc.setPropertyValue("dc:title", "Content to migrate");
            if (i == 15) {
                doc.setPropertyValue("dc:description", "pause");
            }
            session.createDocument(doc);
        }
        session.save();
        txFeature.nextTransaction();
    }

    @Test
    public void testBulkMigration() {
        // assert before state (ie: there are documents with dc:title = 'Content to migrate')
        var beforeState = migrationService.probeAndSetState(DummyBulkMigrator.MIGRATION_ID);
        assertEquals(DummyBulkMigrator.MIGRATION_BEFORE_STATE, beforeState);

        // run the migration
        migrationService.runStep(DummyBulkMigrator.MIGRATION_ID, "before-to-after");

        // await its end
        await().atMost(ONE_MINUTE).until(() -> !migrationService.getStatus(DummyBulkMigrator.MIGRATION_ID).isRunning());

        // assert after state (ie: there are no documents with dc:title = 'Content to migrate')
        var afterState = migrationService.probeAndSetState(DummyBulkMigrator.MIGRATION_ID);
        assertEquals(DummyBulkMigrator.MIGRATION_AFTER_STATE, afterState);
    }

    @Test
    public void testBulkMigrationWithRestart() throws InterruptedException {
        // assert before state (ie: there are documents with dc:title = 'Content to migrate')
        var beforeState = migrationService.probeAndSetState(DummyBulkMigrator.MIGRATION_ID);
        assertEquals(DummyBulkMigrator.MIGRATION_BEFORE_STATE, beforeState);

        // Run the migration
        migrationService.runStep(DummyBulkMigrator.MIGRATION_ID, "before-to-after");
        Thread.sleep(500);
        // It's not possible to run the same step concurrently
        assertThrows(IllegalArgumentException.class,
                () -> migrationService.runStep(DummyBulkMigrator.MIGRATION_ID, "before-to-after"));

        // Stop the processor while migration is in progress
        streamService.getStreamManager().getProcessor(MIGRATION_PROCESSOR_NAME).stop(Duration.ZERO);
        // migration stays in running state
        assertTrue(migrationService.getStatus(DummyBulkMigrator.MIGRATION_ID).isRunning());
        // Simulate a service restart
        MigrationServiceImpl impl = (MigrationServiceImpl) migrationService;
        impl.restartExecutor();

        // Resume migration
        migrationService.runStep(DummyBulkMigrator.MIGRATION_ID, "before-to-after");

        // Await its end
        await().atMost(ONE_MINUTE).until(() -> !migrationService.getStatus(DummyBulkMigrator.MIGRATION_ID).isRunning());
        // assert after state (ie: there are no documents with dc:title = 'Content to migrate')
        var afterState = migrationService.probeAndSetState(DummyBulkMigrator.MIGRATION_ID);
        assertEquals(DummyBulkMigrator.MIGRATION_AFTER_STATE, afterState);
    }

    @Test
    public void testBulkActionFrameworkBinding() {
        // run the migration
        migrationService.probeAndRun(DummyBulkMigrator.MIGRATION_ID);

        // retrieve the bulk status for migration action, that will assert the migration is running on top of BAF
        var bulkStatus = await().atMost(ONE_MINUTE)
                                .until(() -> bulkService.getStatuses(SYSTEM_USERNAME)
                                                        .stream()
                                                        .filter(s -> MigrationAction.ACTION_NAME.equals(s.getAction()))
                                                        .findFirst()
                                                        .orElse(null),
                                        Objects::nonNull);
        // assert command
        var bulkCommand = bulkService.getCommand(bulkStatus.getId());
        assertEquals("SELECT * FROM Document WHERE dc:title = 'Content to migrate'", bulkCommand.getQuery());
        assertEquals(DummyBulkMigrator.MIGRATION_ID, bulkCommand.getParam(PARAM_MIGRATION_ID));
        assertEquals("before-to-after", bulkCommand.getParam(PARAM_MIGRATION_STEP));

        // await its end
        await().atMost(ONE_MINUTE).until(() -> !migrationService.getStatus(DummyBulkMigrator.MIGRATION_ID).isRunning());

        // refresh the status
        bulkStatus = bulkService.getStatus(bulkStatus.getId());
        assertEquals(BulkStatus.State.COMPLETED, bulkStatus.getState());
        assertEquals(20, bulkStatus.getProcessed());
    }

    @Test
    public void testBulkMigrationStartStopProcessor() {
        var processor = streamService.getStreamManager().getProcessor(MIGRATION_PROCESSOR_NAME);
        // processor could not exist (test is first to run) or could be terminated (test is run another one)
        assertTrue(processor == null || processor.isTerminated());

        // run the migration
        migrationService.probeAndRun(DummyBulkMigrator.MIGRATION_ID);

        // only assert that processor exists, its terminated state could be random
        processor = streamService.getStreamManager().getProcessor(MIGRATION_PROCESSOR_NAME);
        assertNotNull(processor);

        // await its end
        await().atMost(ONE_MINUTE).until(() -> !migrationService.getStatus(DummyBulkMigrator.MIGRATION_ID).isRunning());

        // assert it is terminated
        assertTrue(processor.isTerminated());
    }

    @Test
    public void testBulkMigrationFailingScroll() {
        var processor = streamService.getStreamManager().getProcessor(MIGRATION_PROCESSOR_NAME);
        // processor could not exist (test is first to run) or could be terminated (test is run another one)
        assertTrue(processor == null || processor.isTerminated());
        migrationService.probeAndRun(DummyFailingBulkMigrator.MIGRATION_ID);
        // await its failure
        await().dontCatchUncaughtExceptions().atMost(ONE_MINUTE).until(() -> {
            var status = migrationService.getStatus(DummyFailingBulkMigrator.MIGRATION_ID);
            return !status.isRunning() && status.hasError();
        });
        // assert before state because there was a failure
        var afterState = migrationService.probeAndSetState(DummyFailingBulkMigrator.MIGRATION_ID);
        assertEquals(DummyFailingBulkMigrator.MIGRATION_BEFORE_STATE, afterState);
    }
}
