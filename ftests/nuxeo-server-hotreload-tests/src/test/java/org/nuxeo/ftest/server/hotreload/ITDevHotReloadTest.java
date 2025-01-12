/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Kevin Leturc <kleturc@nuxeo.com>
 *
 */
package org.nuxeo.ftest.server.hotreload;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.nuxeo.functionaltests.AbstractTest.NUXEO_URL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.junit.Rule;
import org.junit.Test;
import org.nuxeo.ecm.core.test.StorageConfiguration;
import org.nuxeo.ecm.restapi.test.JsonNodeHelper;
import org.nuxeo.functionaltests.RestTestRule;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;

/**
 * Tests the dev hot reload.
 *
 * @since 9.3
 */
public class ITDevHotReloadTest {

    protected final HttpClientTestRule httpClient = HttpClientTestRule.builder()
                                                                      .url(NUXEO_URL)
                                                                      .adminCredentials()
                                                                      .accept(MediaType.APPLICATION_JSON)
                                                                      .contentType(MediaType.APPLICATION_JSON)
                                                                      // for hot reload
                                                                      .timeout(Duration.ofMinutes(2))
                                                                      .header("X-NXproperties", "*")
                                                                      .build();

    protected final RestTestRule restHelper = new RestTestRule(httpClient);

    @Rule
    public final HotReloadTestRule hotReloadRule = new HotReloadTestRule(restHelper);

    @Test
    public void testEmptyHotReload() {
        hotReloadRule.updateDevBundles("# EMPTY HOT RELOAD");
        // test create a document
        String id = restHelper.createDocument("/", "File", "file", Map.of("dc:description", "description"));
        assertNotNull(id);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHotReloadVocabulary() {
        // test fetch created entry
        Map<String, Object> directoryEntry = restHelper.fetchDirectoryEntry("hierarchical", "child2");
        assertNotNull(directoryEntry);
        var properties = (Map<String, Object>) directoryEntry.get("properties");
        assertNotNull(properties);
        assertEquals("root1", properties.get("parent"));
        assertEquals("child2", properties.get("label"));
    }

    @Test
    public void testHotReloadSequence() {
        String storageConf = StorageConfiguration.defaultSystemProperty(StorageConfiguration.CORE_PROPERTY,
                StorageConfiguration.DEFAULT_CORE);
        assumeTrue("This test only works with VCS", StorageConfiguration.CORE_VCS.equals(storageConf));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("sequenceName", "hibernateSequencer");
        restHelper.operation("javascript.getSequence", parameters);
    }

    @Test
    public void testHotReloadDocumentType() {
        // test create a document
        String id = restHelper.createDocument("/", "HotReload", "hot reload", Map.of("hr:content", "some content"));
        assertNotNull(id);
    }

    @Test
    public void testHotReloadLifecycle() {
        // test follow a transition
        String id = restHelper.createDocument("/", "File", "file");
        restHelper.followLifecycleTransition(id, "to_in_process");
        restHelper.followLifecycleTransition(id, "to_archived");
        restHelper.followLifecycleTransition(id, "to_draft");
    }

    @Test
    public void testHotReloadStructureTemplate() {
        // test Folder creation trigger a child of type File
        restHelper.createDocument("/", "Folder", "folder");
        assertTrue(restHelper.documentExists("/folder/File"));

        // undeploy the bundle
        hotReloadRule.updateDevBundles("# Remove previous bundle for test");
        // test the opposite
        restHelper.createDocument("/folder", "Folder", "child");
        assertFalse(restHelper.documentExists("/folder/child/File"));
    }

    @Test
    public void testHotReloadWorkflow() {
        // test start a workflow
        String id = restHelper.createDocument("/", "File", "file");
        // our workflow only has one automatic transition to the final node
        restHelper.startWorkflowInstance(id, "newWorkflow");
        assertFalse(restHelper.documentHasWorkflowStarted(id));
    }

    @Test
    public void testHotReloadAutomationChain() {
        // test call our automation chain
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("parentPath", "/");
        parameters.put("docName", "file");
        restHelper.operation("CreateDocumentAndStartWorkflow", parameters);
        // add the created file to RestHelper context
        restHelper.addDocumentToDelete("/file");
        // our document should have a started workflow instance
        assertTrue(restHelper.documentHasWorkflowStarted("/file"));
    }

    @Test
    public void testHotReloadAutomationScripting() {
        // test call our automation chain
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("parentPath", "/");
        int nbChildren = 5;
        parameters.put("nbChildren", Integer.valueOf(nbChildren));
        restHelper.operation("javascript.CreateSeveralChild", parameters);
        for (int i = 0; i < nbChildren; i++) {
            String childPath = "/file" + i;
            assertTrue(String.format("Document '%s' doesn't exist", childPath), restHelper.documentExists(childPath));
            // add the created file to RestHelper context
            restHelper.addDocumentToDelete(childPath);
        }
    }

    @Test
    public void testHotReloadAutomationEventHandler() {
        // test create a Folder to trigger automation event handler
        // this will create a File child
        restHelper.createDocument("/", "Folder", "folder");
        assertTrue(restHelper.documentExists("/folder/file"));
    }

    @Test
    public void testHotReloadUserAndGroup() {
        // test fetch userTest and groupTest
        assertTrue(restHelper.userExists("userTest"));
        restHelper.deleteUser("userTest");

        assertTrue(restHelper.groupExists("groupTest"));
        restHelper.deleteGroup("groupTest");
    }

    @Test
    public void testHotReloadPageProvider() {
        // test fetch result of page provider - SELECT * FROM File
        restHelper.createDocument("/", "File", "file");
        int nbDocs = restHelper.countQueryPageProvider("SIMPLE_NXQL_FOR_HOT_RELOAD_PAGE_PROVIDER");
        assertEquals(1, nbDocs);
    }

    @Test
    public void testHotReloadPermission() {
        restHelper.createUser("john", "doe");
        String docId = restHelper.createDocument("/", "File", "file");
        // there's no existence check when adding a permission, so we will test to hot reload a new permission which
        // brings the Remove one, add it for john user on /file document and try to delete it
        // in order to be able to do that, john needs to have the RemoveChildren permission on the parent and as
        // CoreSession will resolve the parent, john also needs to have the Read permission on the parent
        restHelper.addPermission("/", "john", "Read");
        restHelper.addPermission("/", "john", "RemoveChildren");
        // try to delete the file with john user
        httpClient.buildDeleteRequest("/api/v1/id/" + docId)
                  .credentials("john", "doe")
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN), node -> assertEquals(
                          "Failed to delete document /file, Permission denied: cannot remove document %s, Missing permission 'Remove' on document %s".formatted(
                                  docId, docId),
                          JsonNodeHelper.getErrorMessage(node)));
        // there's no check on adding a permission try to delete the file
        restHelper.addPermission("/file", "john", "HotReloadRemove");
        httpClient.buildDeleteRequest("/api/v1/id/" + docId)
                  .credentials("john", "doe")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));
        restHelper.removeDocumentToDelete(docId);
    }

    @Test
    public void testHotReloadTwoBundles() {
        // this test just test we can hot reload two bundles
        // bundle-01 test
        testHotReloadPageProvider();
        // bundle-02 test
        testHotReloadAutomationScripting();
    }

    /**
     * Goal of this is test is to check that resources present in jar are correctly get after a hot reload.
     * <p>
     * There are several caches around JarFile which leads to issues when hot reloading Nuxeo, for instance: when
     * replacing a jar and doing a hot reload, it's possible to get previous resource instead of new one present in jar.
     *
     * @since 10.10
     */
    @Test
    public void testHotReloadJarFileFactoryCleanup() {
        // deploy first bundle
        hotReloadRule.deployJarDevBundle(ITDevHotReloadTest.class,
                "_testHotReloadJarFileFactoryFlush/first/jar-to-hot-reload.jar");
        // assert workflow name
        assertEquals("New Workflow", restHelper.getWorkflowInstanceTitle("newWorkflow"));
        // deploy second bundle with same jar name and resource change
        hotReloadRule.deployJarDevBundle(ITDevHotReloadTest.class,
                "_testHotReloadJarFileFactoryFlush/second/jar-to-hot-reload.jar");
        assertEquals("New Workflow (2)", restHelper.getWorkflowInstanceTitle("newWorkflow"));
    }

    @Test
    public void testHotReloadFileImporters() throws IOException {
        // FileManagerService plugins extension point issue appearing when doing 2 hot reloads
        // see NXP-27147
        hotReloadRule.deployJarDevBundle(ITDevHotReloadTest.class, "testHotReloadFileImporters");

        Path tempPath = Files.createTempFile("", ".bin");
        File tempFile = tempPath.toFile();
        String docPath = "/default-domain/" + tempFile.getName();

        restHelper.operation("FileManager.Import", tempFile, Map.of("currentDocument", "/default-domain"), Map.of());
        restHelper.addDocumentToDelete(docPath);

        var doc = restHelper.fetchDocument(docPath);
        assertEquals("Foo", doc.get("type"));
    }

    /**
     * @since 11.1
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHotReloadMimeTypeIconUpdater() throws IOException {
        // Creates the document and its content.
        String docId = restHelper.createDocument("/", "CustomDocType", "myDocument");
        addBlobToDocument(docId, "custom:image", "anyImage", "png");
        addBlobToDocument(docId, "custom:question/image", "anyImage", "png");
        addBlobToDocument(docId, "custom:question/audio", "anySong", "mp3");

        // Check that all mime types are correctly computed.
        var doc = restHelper.fetchDocument(docId);
        var properties = (Map<String, Object>) doc.get("properties");
        var image = (Map<String, Object>) properties.get("custom:image");
        assertEquals("image/png", image.get("mime-type"));

        var question = (Map<String, Map<String, Object>>) properties.get("custom:question");
        assertEquals("image/png", question.get("image").get("mime-type"));
        assertEquals("audio/mpeg", question.get("audio").get("mime-type"));

        // make a new deploy that override the exiting document model by removing the `question:image` property.
        hotReloadRule.deployJarDevBundle(ITDevHotReloadTest.class, "testHotReloadMimeTypeIconUpdaterWhenSchemaChanges");

        // Ensure that the event listener responsible of computing the mime type is correctly called when
        // the schema is updated.
        addBlobToDocument(docId, "custom:image", "newImage", "psd");

        // Check then the new mime type is correctly computed.
        doc = restHelper.fetchDocument(docId);
        properties = (Map<String, Object>) doc.get("properties");
        image = (Map<String, Object>) properties.get("custom:image");
        question = (Map<String, Map<String, Object>>) properties.get("custom:question");
        assertEquals("application/photoshop", image.get("mime-type"));
        assertEquals("audio/mpeg", question.get("audio").get("mime-type"));
        assertNull(question.get("image"));
    }

    /** @since 11.1 **/
    protected void addBlobToDocument(String docId, String xpath, String fileName, String extension) throws IOException {
        File file = Files.createTempFile(fileName, "." + extension).toFile();
        restHelper.operation("Blob.AttachOnDocument", file, Map.of(), Map.of("document", docId, "xpath", xpath));
    }

}
