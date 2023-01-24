/*
 * (C) Copyright 2017 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     bdelbosc
 */
package org.nuxeo.ecm.platform.audit.listener;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.naming.NamingException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.io.registry.MarshallerHelper;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.StreamManager;
import org.nuxeo.lib.stream.computation.Watermark;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * An events collector that write log entries as json record into a stream.
 *
 * @since 9.3
 */
public class StreamAuditEventListener implements EventListener, Synchronization {

    private static final Logger log = LogManager.getLogger(StreamAuditEventListener.class);

    protected static final ThreadLocal<Boolean> isEnlisted = ThreadLocal.withInitial(() -> Boolean.FALSE);

    protected static final ThreadLocal<List<LogEntry>> entries = ThreadLocal.withInitial(ArrayList::new);

    public static final String STREAM_AUDIT_ENABLED_PROP = "nuxeo.stream.audit.enabled";

    public static final String STREAM_NAME = "audit/audit";

    protected static final AtomicInteger writeCounter = new AtomicInteger(0);

    @Deprecated
    // @deprecated since 11.1 log config is not needed anymore
    public static final String DEFAULT_LOG_CONFIG = "audit";

    @Override
    public void handleEvent(Event event) {
        AuditLogger logger = Framework.getService(AuditLogger.class);
        if (logger == null) {
            return;
        }
        if (!isEnlisted.get()) {
            isEnlisted.set(registerSynchronization(this));
            entries.get().clear();
            log.debug("AuditEventListener collecting entries for the tx");
        }
        if (logger.getAuditableEventNames().contains(event.getName())) {
            entries.get().add(logger.buildEntryFromEvent(event));
        }
        if (!isEnlisted.get()) {
            // there is no transaction so don't wait for a commit
            afterCompletion(Status.STATUS_COMMITTED);
        }

    }

    @Override
    public void beforeCompletion() {
        log.debug("AuditEventListener going to write {} entries.", () -> entries.get().size());
    }

    @Override
    public void afterCompletion(int status) {
        try {
            if (entries.get().isEmpty()
                    || (Status.STATUS_MARKED_ROLLBACK == status || Status.STATUS_ROLLEDBACK == status)) {
                // This means that in case of rollback there is no event logged
                return;
            }
            writeEntries();
            log.debug("AuditEventListener writes {} entries.", () -> entries.get().size());
        } finally {
            isEnlisted.set(false);
            entries.get().clear();
        }
    }

    protected void writeEntries() {
        if (entries.get().isEmpty()) {
            return;
        }
        // all log entries for a transaction goes to the same partition preserving ordering
        String partitionKey = getPartitionKey();
        StreamManager streamManager = getStreamManager();
        for (LogEntry entry : entries.get()) {
            if (entry != null) {
                Record record = recordOf(partitionKey, entry);
                streamManager.append(STREAM_NAME, record);
            }
        }
    }

    protected String getPartitionKey() {
        // ok because atomic integer wraps on overflow
        return String.valueOf(writeCounter.incrementAndGet());
    }

    // @deprecated since 11.5, it always writes to the same partition entry id being unset at this stage
    protected Record recordOf(LogEntry entry) {
        return recordOf(String.valueOf(entry.getId()), entry);
    }

    protected Record recordOf(String partitionKey, LogEntry entry) {
        String json = asJson(entry);
        if (json == null) {
            return null;
        }
        long timestamp = getTimestampForEntry(entry);
        return new Record(partitionKey, json.getBytes(UTF_8), Watermark.ofTimestamp(timestamp).getValue());
    }

    protected long getTimestampForEntry(LogEntry entry) {
        if (entry.getEventDate() != null) {
            return entry.getEventDate().getTime();
        }
        return System.currentTimeMillis();
    }

    protected String asJson(LogEntry entry) {
        if (entry == null) {
            return null;
        }
        RenderingContext ctx = RenderingContext.CtxBuilder.get();
        try {
            return MarshallerHelper.objectToJson(entry, ctx);
        } catch (IOException e) {
            log.warn("Unable to translate entry into json, eventId: {}: {}", entry.getEventId(), e.getMessage(), e);
            return null;
        }
    }

    protected boolean registerSynchronization(Synchronization sync) {
        try {
            TransactionManager tm = TransactionHelper.lookupTransactionManager();
            if (tm != null) {
                if (tm.getTransaction() != null) {
                    tm.getTransaction().registerSynchronization(sync);
                    return true;
                }
                return false;
            } else {
                log.error("Unable to register synchronization : no TransactionManager");
                return false;
            }
        } catch (NamingException | IllegalStateException | SystemException | RollbackException e) {
            log.error("Unable to register synchronization", e);
            return false;
        }
    }

    protected StreamManager getStreamManager() {
        StreamService service = Framework.getService(StreamService.class);
        return service.getStreamManager();
    }
}
