/*
 * (C) Copyright 2006-2017 Nuxeo (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.ecm.platform.audit.service;

import org.nuxeo.ecm.platform.audit.api.AuditStorage;
import org.nuxeo.ecm.platform.audit.api.Logs;

/**
 * Audit Backend SPI
 *
 * @author tiry
 */
public interface AuditBackend extends Logs {

    int getApplicationStartedOrder();

    void onApplicationStarted();

    /**
     * @since 9.2
     */
    void onApplicationStopped();

    /**
     * Restore the backend from the given {@link AuditStorage}
     *
     * @param auditStorage the audit storage
     * @param batchSize the batch size
     * @param keepAlive the keep alive duration
     * @since 9.3
     */
    void restore(AuditStorage auditStorage, int batchSize, int keepAlive);

}
