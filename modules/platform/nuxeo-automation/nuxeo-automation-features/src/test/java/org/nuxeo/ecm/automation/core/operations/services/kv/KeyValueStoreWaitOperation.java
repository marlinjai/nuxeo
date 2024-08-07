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
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.ecm.automation.core.operations.services.kv;

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.kv.KeyValueService;

/**
 * @since 2025.0
 */
@Operation(id = KeyValueStoreWaitOperation.ID, category = Constants.CAT_SERVICES)
public class KeyValueStoreWaitOperation {

    public static final String ID = "Test.KeyValueStoreWait";

    @Context
    protected KeyValueService keyValueService;

    @Param(name = "keyValueStoreName")
    protected String keyValueStoreName;

    @Param(name = "key")
    protected String key;

    @Param(name = "value")
    protected String value;

    /** Timeout in milliseconds. */
    @Param(name = "timeout", required = false)
    protected long timeout = 1_000;

    @OperationMethod
    public void run() throws InterruptedException {
        long begin = System.currentTimeMillis();
        var keyValueStore = keyValueService.getKeyValueStore(keyValueStoreName);
        while (!value.equals(keyValueStore.getString(key))) {
            if (System.currentTimeMillis() - begin > timeout) {
                throw new NuxeoException("Timed out waiting for: " + keyValueStoreName + "/" + key);
            }
            Thread.sleep(100);
        }
    }
}
