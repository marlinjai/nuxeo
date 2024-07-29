/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Abstract Work to find the ids of documents for which some process must be executed in batch, based on a NXQL query.
 *
 * @since 9.10
 */
public abstract class BatchFinderWork extends AbstractWork {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LogManager.getLogger(BatchFinderWork.class);

    protected static final int SCROLL_KEEPALIVE_SECONDS = 60;

    protected String nxql;

    /** @since 2023.17 */
    protected static final int TRANSACTION_TIMEOUT_SECONDS = 3_600 * 48; // 2 days

    public BatchFinderWork(String repositoryName, String nxql, String originatingUsername) {
        this.repositoryName = repositoryName;
        this.nxql = nxql;
        setOriginatingUsername(originatingUsername);
    }

    @Override
    public int getRetryCount() {
        // even read-only threads may encounter concurrent update exceptions when trying to read
        // a previously deleted complex property due to read committed semantics (see NXP-17384)
        return 1;
    }

    /**
     * The batch size to use.
     */
    public abstract int getBatchSize();

    @Override
    public void work() {
        int batchSize = getBatchSize();
        log.debug("{}: Starting batch find for query: {} with batch size: {}", this::getTitle, () -> nxql,
                () -> batchSize);
        openSystemSession();
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            // Use a long transaction for the duration of the scroll
            TransactionHelper.startTransaction(TRANSACTION_TIMEOUT_SECONDS);
        }
        setProgress(Progress.PROGRESS_INDETERMINATE);
        setStatus("Searching");
        long batchCount = 0;
        long documentCount = 0;
        ScrollResult<String> scroll = session.scroll(nxql, batchSize, SCROLL_KEEPALIVE_SECONDS);
        while (scroll.hasResults()) {
            List<String> docIds = scroll.getResults();
            // schedule the batch
            if (!docIds.isEmpty()) {
                Framework.getService(WorkManager.class).schedule(getBatchProcessorWork(docIds));
            }
            batchCount += 1;
            documentCount += docIds.size();
            setProgress(new Progress(documentCount, -1));
            scroll = session.scroll(scroll.getScrollId());
        }

        var documentCountF = documentCount;
        var batchCountF = batchCount;
        log.debug("{}: Submitted {} documents in {} batch processor workers", this::getTitle, () -> documentCountF,
                () -> batchCountF);
        setProgress(new Progress(documentCount, documentCount));
        setStatus("Done");
    }

    public abstract Work getBatchProcessorWork(List<String> docIds);

}
