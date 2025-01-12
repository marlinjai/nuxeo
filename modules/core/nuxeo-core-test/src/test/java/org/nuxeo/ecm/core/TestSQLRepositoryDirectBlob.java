/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Florent Guillaume, jcarsique
 */
package org.nuxeo.ecm.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

import javax.inject.Inject;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.LocalBlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * Sample test showing how to use a direct access to the binaries storage.
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
@Deploy("org.nuxeo.ecm.core.api.tests:OSGI-INF/test-default-blob-provider.xml")
public class TestSQLRepositoryDirectBlob {

    protected static final String FILE_CONTENT = "this is a file";

    @Inject
    protected CoreSession session;

    // ----- Third-party application -----

    /** The application that creates a file. */
    public String createFile() throws Exception {
        FileManager fileMaker = new FileManager();

        // get the tmp dir where to create files
        File tmpDir = fileMaker.getTmpDir();

        // third-party application creates a file there
        File file = File.createTempFile("myapp", null, tmpDir);
        FileOutputStream out = new FileOutputStream(file);
        out.write(FILE_CONTENT.getBytes("UTF-8"));
        out.close();

        // then it moves the tmp file to the binaries storage, and gets the
        // digest
        String digest = fileMaker.moveTmpFileToBinaries(file);
        return digest;
    }

    // ----- Nuxeo application -----
    @Test
    public void testDirectBlob() throws Exception {
        DocumentModel folder = session.getRootDocument();
        DocumentModel file = session.createDocumentModel(folder.getPathAsString(), "filea", "File");
        file = session.createDocument(file);
        session.save();

        /*
         * 1. A third-party application returns a digest for a created file.
         */
        String digest = createFile();

        /*
         * 2. Later, create and use the blob for this digest.
         */
        BlobProvider blobProvider = new LocalBlobProvider();
        blobProvider.initialize("repo", Collections.emptyMap());
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = digest;
        blobInfo.filename = "doc.txt";
        blobInfo.encoding = "utf-8";
        blobInfo.mimeType = "text/plain";
        blobInfo.length = (long) FILE_CONTENT.length();
        Blob blob = blobProvider.readBlob(blobInfo);
        assertEquals("MD5", blob.getDigestAlgorithm());
        assertEquals(digest, blob.getDigest());
        file.setProperty("file", "content", blob);
        session.saveDocument(file);
        session.save();

        /*
         * 3. Check the retrieved doc.
         */
        file = session.getDocument(file.getRef());
        blob = (Blob) file.getProperty("file", "content");
        assertEquals("doc.txt", blob.getFilename());
        assertEquals(FILE_CONTENT, blob.getString());
        assertEquals(FILE_CONTENT.length(), blob.getLength());
        assertEquals("utf-8", blob.getEncoding());
        assertEquals("text/plain", blob.getMimeType());

        /*
         * remove attached file
         */
        file.setProperty("file", "content", null);
        file = session.saveDocument(file);
        session.save();
        assertNull(file.getProperty("file", "content"));

        blobProvider.close();
    }

    @Test
    public void testBinarySerialization() throws Exception {
        DocumentModel folder = session.getRootDocument();
        DocumentModel file = session.createDocumentModel(folder.getPathAsString(), "filea", "File");
        file = session.createDocument(file);
        session.save();

        // create a binary instance pointing to some content stored on the
        // filesystem
        String digest = createFile();
        BlobProvider blobProvider = new LocalBlobProvider();
        blobProvider.initialize("repo", Collections.emptyMap());
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = digest;
        blobInfo.filename = "doc.txt";
        blobInfo.encoding = "utf-8";
        blobInfo.mimeType = "text/plain";
        ManagedBlob blob = (ManagedBlob) blobProvider.readBlob(blobInfo);
        assertNotNull("Missing file for digest: " + digest, blobProvider.getFile(blob));

        String expected = FILE_CONTENT;
        byte[] observedContent = new byte[expected.length()];
        assertEquals(digest, blob.getDigest());
        assertEquals(expected.length(), blob.getStream().read(observedContent));
        assertEquals(expected, new String(observedContent));

        // serialize and deserialize the binary instance
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(blob);
        out.flush();
        out.close();

        // Make an input stream from the byte array and read
        // a copy of the object back in.
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        Blob binaryCopy = (Blob) in.readObject();

        observedContent = new byte[expected.length()];
        assertEquals(digest, binaryCopy.getDigest());
        assertEquals(expected.length(), binaryCopy.getStream().read(observedContent));
        assertEquals(expected, new String(observedContent));

        blobProvider.close();
    }

    @Test
    @Ignore("NXP-32045")
    public void testBinaryManagerTmpFileMoveNotCopy() throws Exception {
        // tmp file
        Blob blob = Blobs.createBlob(new ByteArrayInputStream("abcd\b".getBytes("UTF-8")));
        File originaFile = blob.getFile();
        assertTrue(originaFile.exists());
        // set in doc
        DocumentModel doc = session.createDocumentModel("/", "myfile", "File");
        doc.setPropertyValue("file:content", (Serializable) blob);
        doc = session.createDocument(doc);
        session.save();

        assertTrue(blob.getFile().exists());
        // Below assertion fails because org.nuxeo.ecm.core.blob.LocalBlobStore.writeBlobGeneric(BlobWriteContext)
        // does not delete original file like did
        // org.nuxeo.ecm.core.blob.binary.DefaultBinaryManager.storeAndDigest(FileBlob)
        assertFalse(originaFile.exists());
    }

}

/**
 * Class doing a simplified version of what the binaries storage does.
 * <p>
 * In a real application, change the constructor to pass the rootDir as a parameter or use configuration.
 *
 * @author Florent Guillaume
 */
class FileManager {

    /*
     * These parameters have to be the same as the one from the binaries storage.
     */

    public static final String DIGEST_ALGORITHM = "MD5";

    public static final int DEPTH = 2;

    protected final File tmpDir;

    protected final File dataDir;

    public FileManager() {
        // from inside Nuxeo components, this can be used
        // otherwise use a hardcoded string or parameter to that directory
        File rootDir = new File(Environment.getDefault().getData(), "binaries");
        tmpDir = new File(rootDir, "tmp");
        dataDir = new File(rootDir, "data");
        tmpDir.mkdirs();
        dataDir.mkdirs();
    }

    public File getTmpDir() {
        return tmpDir;
    }

    public String moveTmpFileToBinaries(File file) throws IOException {
        // digest the file
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw (IOException) new IOException().initCause(e);
        }
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                messageDigest.update(buf, 0, n);
            }
        } finally {
            in.close();
        }
        String digest = toHexString(messageDigest.digest());

        // move the file to its final location
        File dest = getFileForDigest(digest, dataDir);
        file.renameTo(dest); // atomic move, fails if already there
        file.delete(); // fails if the move was successful
        if (!dest.exists()) {
            throw new IOException("Could not create file: " + dest);
        }
        return digest;
    }

    protected static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    protected static String toHexString(byte[] data) {
        StringBuilder sb = new StringBuilder(2 * data.length);
        for (byte b : data) {
            sb.append(HEX_DIGITS[(0xF0 & b) >> 4]);
            sb.append(HEX_DIGITS[0x0F & b]);
        }
        return sb.toString();
    }

    protected static File getFileForDigest(String digest, File dataDir) {
        StringBuilder sb = new StringBuilder(3 * DEPTH - 1);
        for (int i = 0; i < DEPTH; i++) {
            if (i != 0) {
                sb.append(File.separatorChar);
            }
            sb.append(digest.substring(2 * i, 2 * i + 2));
        }
        File dir = new File(dataDir, sb.toString());
        dir.mkdirs();
        return new File(dir, digest);
    }

}
