/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.blob.binary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.blob.binary.AESBinaryManager.PARAM_KEY_ALIAS;
import static org.nuxeo.ecm.core.blob.binary.AESBinaryManager.PARAM_KEY_PASSWORD;
import static org.nuxeo.ecm.core.blob.binary.AESBinaryManager.PARAM_KEY_STORE_FILE;
import static org.nuxeo.ecm.core.blob.binary.AESBinaryManager.PARAM_KEY_STORE_PASSWORD;
import static org.nuxeo.ecm.core.blob.binary.AESBinaryManager.PARAM_KEY_STORE_TYPE;
import static org.nuxeo.ecm.core.blob.binary.AESBinaryManager.PARAM_PASSWORD;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.test.TemporaryKeyStore;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@Deprecated(since = "2023.9")
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
public class TestAESBinaryManager {

    private static final String KEY_STORE_TYPE = "JCEKS";

    private static final String KEY_STORE_PASSWORD = "keystoresecret";

    private static final String KEY_ALIAS = "myaeskey";

    private static final String KEY_PASSWORD = "keysecret";

    private static final String CONTENT = "this is a file au caf\u00e9";

    private static final String CONTENT_MD5 = "d25ea4f4642073b7f218024d397dbaef";

    private static final String UTF8 = "UTF-8";

    @Rule
    public TemporaryKeyStore temporaryKeyStore = new TemporaryKeyStore.Builder(KEY_STORE_TYPE,
            KEY_STORE_PASSWORD).generateKey(KEY_ALIAS, KEY_PASSWORD, "AES", 256).build();

    @Test
    public void testEncryptDecryptWithPassword() throws Exception {
        AESBinaryManager binaryManager = new AESBinaryManager();
        binaryManager.digestAlgorithm = binaryManager.getDefaultDigestAlgorithm(); // MD5
        String options = String.format("%s=%s", PARAM_PASSWORD, "mypassword");
        binaryManager.initializeOptions(options);

        // encrypt
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String digest = binaryManager.storeAndDigest(new ByteArrayInputStream(CONTENT.getBytes(UTF8)), out);
        assertEquals(CONTENT_MD5, digest);
        byte[] encrypted = out.toByteArray();

        // decrypt
        out = new ByteArrayOutputStream();
        binaryManager.decrypt(new ByteArrayInputStream(encrypted), out);

        assertEquals(CONTENT, new String(out.toByteArray(), UTF8));

        // cannot decrypt with wrong password

        options = String.format("%s=%s", PARAM_PASSWORD, "badpassword");
        binaryManager.initializeOptions(options);

        out = new ByteArrayOutputStream();
        try {
            binaryManager.decrypt(new ByteArrayInputStream(encrypted), out);
            assertFalse(CONTENT.equals(new String(out.toByteArray(), UTF8)));
        } catch (NuxeoException e) {
            String message = e.getMessage();
            assertTrue(message, message.contains("Given final block not properly padded")
                    || message.contains("Tag mismatch") || message.contains("mac check in GCM failed"));
        }

        binaryManager.close();
    }

    @Test
    public void testEncryptDecryptWithKeyStore() throws Exception {
        String options = String.format("%s=%s,%s=%s,%s=%s,%s=%s,%s=%s", PARAM_KEY_STORE_TYPE, KEY_STORE_TYPE, //
                PARAM_KEY_STORE_FILE, temporaryKeyStore.getPath().toString(), //
                PARAM_KEY_STORE_PASSWORD, KEY_STORE_PASSWORD, //
                PARAM_KEY_ALIAS, KEY_ALIAS, //
                PARAM_KEY_PASSWORD, KEY_PASSWORD);

        AESBinaryManager binaryManager = new AESBinaryManager();
        binaryManager.digestAlgorithm = binaryManager.getDefaultDigestAlgorithm(); // MD5
        binaryManager.initializeOptions(options);

        // encrypt
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String digest = binaryManager.storeAndDigest(new ByteArrayInputStream(CONTENT.getBytes(UTF8)), out);
        assertEquals(CONTENT_MD5, digest);
        byte[] encrypted = out.toByteArray();

        // decrypt
        out = new ByteArrayOutputStream();
        binaryManager.decrypt(new ByteArrayInputStream(encrypted), out);

        assertEquals(CONTENT, new String(out.toByteArray(), UTF8));

        binaryManager.close();
    }

    @Test
    public void testAESBinaryManager() throws Exception {
        AESBinaryManager binaryManager = new AESBinaryManager();
        String options = String.format("%s=%s", PARAM_PASSWORD, "mypassword");
        binaryManager.initialize("repo", Collections.singletonMap(BinaryManager.PROP_KEY, options));

        Binary binary = binaryManager.getBinary(CONTENT_MD5);
        assertNull(binary);

        // store binary
        byte[] bytes = CONTENT.getBytes(UTF8);
        binary = binaryManager.getBinary(new ByteArrayInputStream(bytes));
        assertNotNull(binary);
        assertEquals(CONTENT_MD5, binary.getDigest());

        // get binary
        binary = binaryManager.getBinary(CONTENT_MD5);
        assertNotNull(binary);
        try (InputStream stream = binary.getStream()) {
            assertEquals(CONTENT, IOUtils.toString(stream, UTF8));
        }

        binaryManager.close();
    }

}
