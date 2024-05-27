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
package org.nuxeo.functionaltests;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_MULTIPLE_CHOICES;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.functionaltests.AbstractTest.NUXEO_URL;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.MediaType;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.HttpResponse;
import org.nuxeo.http.test.handler.AbstractStatusCodeHandler;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.http.test.handler.StringHandler;
import org.nuxeo.http.test.handler.VoidHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 2023.13
 */
public class RestTestRule implements TestRule {

    private static final Logger log = LogManager.getLogger(RestTestRule.class);

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected static final String USER_WORKSPACE_PATH_FORMAT = "/default-domain/UserWorkspaces/%s";

    protected static final String DEFAULT_USER_EMAIL = "devnull@nuxeo.com";

    protected final HttpClientTestRule httpClient;

    /**
     * Documents to delete in cleanup step. Key is the document id and value is its path.
     */
    protected final Map<String, String> documentsToDelete = new HashMap<>();

    /**
     * Users to delete in cleanup step. The list contains user ids.
     */
    protected final List<String> usersToDelete = new ArrayList<>();

    /**
     * Groups to delete in cleanup step. The list contains group ids.
     */
    protected final List<String> groupsToDelete = new ArrayList<>();

    /**
     * Directory entries to delete in cleanup step. Key is the directory name and value is a set of entry ids.
     */
    protected final Map<String, Set<String>> directoryEntryIdsToDelete = new HashMap<>();

    public RestTestRule() {
        this(HttpClientTestRule.builder()
                               .url(NUXEO_URL)
                               .adminCredentials()
                               .accept(MediaType.APPLICATION_JSON)
                               .contentType(MediaType.APPLICATION_JSON)
                               // for hot reload
                               .timeout(Duration.ofMinutes(2))
                               .header("X-NXproperties", "*")
                               .build());
    }

    /**
     * Constructor which takes as input the {@link HttpClientTestRule}.
     * <p>
     * Note that you must not declare your {@link HttpClientTestRule} as {@link org.junit.Rule JUnit Rule} when using
     * this constructor, because this is {@link RestTestRule} which handles the Rule mechanism, see below:
     * 
     * <pre>{@code
     * protected final HttpClientTestRule httpClient = HttpClientTestRule.builder().build();
     *
     * @Rule
     * public final RestTestRule restHelper = new RestTestRule(httpClient);
     * }</pre>
     *
     * @since 2023.13
     */
    public RestTestRule(HttpClientTestRule httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                starting();
                try {
                    base.evaluate();
                } finally {
                    finished();
                }
            }
        };
    }

    public void starting() {
        httpClient.starting();
    }

    public void finished() {
        cleanup();
        httpClient.finished();
    }

    protected String safeWriteValue(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new NuxeoException("Unable to marshall map: " + map, e);
        }
    }

    // ----------------
    // Cleanup Services
    // ----------------

    public void cleanup() {
        cleanupDocuments();
        cleanupUsers();
        cleanupGroups();
        cleanupDirectoryEntries();
    }

    public void cleanupDocuments() {
        // delete by ids
        documentsToDelete.keySet().forEach(this::deleteDocument);
        documentsToDelete.clear();
    }

    public void cleanupUsers() {
        for (String user : usersToDelete) {
            httpClient.buildDeleteRequest("/api/v1/path" + String.format(USER_WORKSPACE_PATH_FORMAT, user))
                      .execute(new VoidHandler());
        }
        usersToDelete.forEach(this::deleteUser);
        usersToDelete.clear();
    }

    public void cleanupGroups() {
        groupsToDelete.forEach(this::deleteGroup);
        groupsToDelete.clear();
    }

    public void cleanupDirectoryEntries() {
        directoryEntryIdsToDelete.forEach(
                (directoryName, entryIds) -> entryIds.forEach(id -> deleteDirectoryEntry(directoryName, id)));
        directoryEntryIdsToDelete.clear();
    }

    public void addDocumentToDelete(String idOrPath) {
        var document = fetchDocument(idOrPath);
        addDocumentToDelete((String) document.get("uid"), (String) document.get("path"));
    }

    protected void addDocumentToDelete(String id, String path) {
        // do we already have to delete one parent?
        if (documentsToDelete.values().stream().noneMatch(path::startsWith)) {
            documentsToDelete.put(id, path);
        }
    }

    public void removeDocumentToDelete(String idOrPath) {
        if (idOrPath.startsWith("/")) {
            documentsToDelete.values().remove(idOrPath);
        } else {
            documentsToDelete.remove(idOrPath);
        }
    }

    // -------------
    // User Services
    // -------------

    public String createUser(String name, String password) {
        return createUser(name, password, null, null, null, null, null);
    }

    public String createUser(String name, String password, String firstName, String lastName, String company,
            String email, String group) {
        var userProperties = new HashMap<String, Object>();
        userProperties.put("username", name);
        userProperties.put("password", password);
        if (isNotBlank(firstName)) {
            userProperties.put("firstName", firstName);
        }
        if (isNotBlank(lastName)) {
            userProperties.put("lastName", lastName);
        }
        if (isNotBlank(company)) {
            userProperties.put("company", company);
        }
        userProperties.put("email", defaultIfBlank(email, DEFAULT_USER_EMAIL));
        if (isNotBlank(group)) {
            userProperties.put("groups", List.of(group));
        }

        var user = new HashMap<String, Object>();
        user.put("entity-type", "user");
        user.put("properties", userProperties);
        String userId = httpClient.buildPostRequest("/api/v1/user")
                                  .entity(safeWriteValue(user))
                                  .executeAndThen(new JsonNodeHandler(SC_CREATED), node -> node.get("id").textValue());

        usersToDelete.add(userId);
        return userId;
    }

    public boolean userExists(String name) {
        return httpClient.buildGetRequest("/api/v1/user/" + name)
                         .executeAndThen(new HttpStatusCodeHandler(), status -> status == SC_OK);
    }

    public void deleteUser(String name) {
        httpClient.buildDeleteRequest("/api/v1/user/" + name).executeAndConsume(new HttpStatusCodeHandler(), status -> {
            if (status == SC_NOT_FOUND) {
                log.warn("User: {} not deleted because not found", name);
            } else {
                assertEquals(SC_NO_CONTENT, status.intValue());
            }
        });
    }

    // --------------
    // Group Services
    // --------------

    public void createGroup(String name, String label) {
        createGroup(name, label, List.of(), List.of());
    }

    public void createGroup(String name, String label, List<String> members, List<String> subGroups) {
        var group = new HashMap<String, Object>();
        group.put("entity-type", "group");
        group.put("groupname", name);
        group.put("grouplabel", label);
        if (!members.isEmpty()) {
            group.put("memberUsers", members);
        }
        if (!subGroups.isEmpty()) {
            group.put("memberGroups", subGroups);
        }
        httpClient.buildPostRequest("/group").entity(safeWriteValue(group)).execute(new JsonNodeHandler(SC_CREATED));

        groupsToDelete.add(name);
    }

    public boolean groupExists(String name) {
        return httpClient.buildGetRequest("/api/v1/group/" + name)
                         .executeAndThen(new HttpStatusCodeHandler(), status -> status == SC_OK);
    }

    public void deleteGroup(String name) {
        httpClient.buildDeleteRequest("/api/v1/group/" + name)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> {
                      if (status == SC_NOT_FOUND) {
                          log.warn("Group: {} not deleted because not found", name);
                      } else {
                          assertEquals(SC_NO_CONTENT, status.intValue());
                      }
                  });
    }

    // -----------------
    // Document Services
    // -----------------

    protected String computeDocumentPathOrIdUrl(String idOrPath) {
        return "/api/v1" + (idOrPath.startsWith("/") ? "/path" : "/id/") + idOrPath;
    }

    public String createDocument(String parentIdOrPath, String type, String title) {
        return createDocument(parentIdOrPath, type, title, Map.of());
    }

    public String createDocument(String parentIdOrPath, String type, String title, Map<String, Object> props) {
        var properties = new HashMap<>(props);
        properties.put("dc:title", title);

        var document = new HashMap<String, Object>();
        document.put("entity-type", "document");
        document.put("type", type);
        document.put("name", title);
        document.put("properties", properties);

        return httpClient.buildPostRequest(computeDocumentPathOrIdUrl(parentIdOrPath))
                         .entity(safeWriteValue(document))
                         .executeAndThen(new JsonNodeHandler(SC_CREATED), node -> {
                             String docId = node.get("uid").textValue();
                             String docPath = node.get("path").textValue();
                             addDocumentToDelete(docId, docPath);
                             return docId;
                         });
    }

    public boolean documentExists(String idOrPath) {
        return httpClient.buildGetRequest(computeDocumentPathOrIdUrl(idOrPath))
                         .executeAndThen(new HttpStatusCodeHandler(), status -> status == SC_OK);
    }

    public Map<String, Object> fetchDocument(String idOrPath) {
        return httpClient.buildGetRequest(computeDocumentPathOrIdUrl(idOrPath)).execute(new MapHandler());
    }

    public void deleteDocument(String idOrPath) {
        httpClient.buildDeleteRequest(computeDocumentPathOrIdUrl(idOrPath))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));
    }

    public void addPermission(String idOrPath, String username, String permission) {
        var params = new HashMap<>();
        params.put("acl", "local");
        params.put("permission", permission);
        params.put("user", username);

        var entity = new HashMap<String, Object>();
        entity.put("input", "doc:" + idOrPath);
        entity.put("params", params);
        httpClient.buildPostRequest("/api/v1/automation/Document.AddPermission")
                  .entity(safeWriteValue(entity))
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    public void followLifecycleTransition(String idOrPath, String transitionName) {
        var params = new HashMap<>();
        params.put("value", transitionName);

        var entity = new HashMap<String, Object>();
        entity.put("input", "doc:" + idOrPath);
        entity.put("params", params);
        httpClient.buildPostRequest("/api/v1/automation/Document.FollowLifecycleTransition")
                  .entity(safeWriteValue(entity))
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    public void startWorkflowInstance(String idOrPath, String workflowId) {
        String workflow = httpClient.buildGetRequest("/api/v1/workflowModel/" + workflowId)
                                    .execute(new StringHandler());

        httpClient.buildPostRequest(computeDocumentPathOrIdUrl(idOrPath) + "/@workflow")
                  .entity(workflow)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));
    }

    public boolean documentHasWorkflowStarted(String idOrPath) {
        return httpClient.buildGetRequest(computeDocumentPathOrIdUrl(idOrPath) + "/@workflow")
                         .executeAndThen(new JsonNodeHandler(), node -> !node.get("entries").isEmpty());
    }

    public String getWorkflowInstanceTitle(String workflowId) {
        return httpClient.buildGetRequest("/api/v1/workflowModel/" + workflowId)
                         .executeAndThen(new JsonNodeHandler(), node -> node.get("title").textValue());
    }

    /**
     * Runs a page provider on Nuxeo instance and return the total size of documents.
     *
     * @return the total size of documents
     */
    public int countQueryPageProvider(String providerName) {
        return httpClient.buildGetRequest("/api/v1/search/pp/" + providerName + "/execute")
                         .addQueryParameter("pageSize", "1")
                         .addQueryParameter("currentPageIndex", "0")
                         .addQueryParameter("sortBy", "dc:title")
                         .addQueryParameter("sortOrder", "ASC")
                         .executeAndThen(new JsonNodeHandler(), node -> node.get("totalSize").intValue());
    }

    // ------------------
    // Directory Services
    // ------------------

    public String createDirectoryEntry(String directoryName, Map<String, String> properties) {
        var directoryEntry = new HashMap<String, Object>();
        directoryEntry.put("entity-type", "directoryEntry");
        directoryEntry.put("directoryName", directoryName);
        directoryEntry.put("properties", properties);

        String entryId = httpClient.buildPostRequest("/api/v1/directory/" + directoryName)
                                   .entity(safeWriteValue(directoryEntry))
                                   .executeAndThen(new JsonNodeHandler(SC_CREATED), node -> node.get("id").textValue());

        directoryEntryIdsToDelete.computeIfAbsent(directoryName, k -> new HashSet<>()).add(entryId);
        return entryId;
    }

    public Map<String, Object> fetchDirectoryEntry(String directoryName, String directoryEntryId) {
        return httpClient.buildGetRequest("/api/v1/directory/" + directoryName + "/" + directoryEntryId)
                         .execute(new MapHandler());
    }

    public void updateDirectoryEntry(String directoryName, String directoryEntryId, Map<String, Object> properties) {
        var directoryEntry = new HashMap<String, Object>();
        directoryEntry.put("entity-type", "directoryEntry");
        directoryEntry.put("directoryName", directoryName);
        directoryEntry.put("id", directoryEntryId);
        directoryEntry.put("properties", properties);

        httpClient.buildPutRequest("/api/v1/directory/" + directoryName + "/" + directoryEntryId)
                  .entity(safeWriteValue(directoryEntry))
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    public void deleteDirectoryEntry(String directoryName, String directoryEntryId) {
        httpClient.buildDeleteRequest("/api/v1/directory/" + directoryName + "/" + directoryEntryId)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));
    }

    @SuppressWarnings("unchecked")
    public void deleteDirectoryEntries(String directoryName) {
        var directoryEntries = httpClient.buildGetRequest("/api/v1/directory/" + directoryName)
                                         .execute(new MapHandler());
        ((List<Map<String, Object>>) directoryEntries.get("entries")).forEach(
                entry -> deleteDirectoryEntry(directoryName, (String) entry.get("id")));
    }

    // ------------------
    // Operation Services
    // ------------------

    public void operation(String operationId, Map<String, Object> parameters) {
        var entity = new HashMap<String, Object>();
        entity.put("params", parameters);
        httpClient.buildPostRequest("/api/v1/automation/" + operationId)
                  .entity(safeWriteValue(entity))
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertTrue("Status is not in the 2xx range",
                          status >= SC_OK && status < SC_MULTIPLE_CHOICES));
    }

    public void operation(String operationId, File input, Map<String, Object> context, Map<String, Object> parameters) {
        var automationExecution = new HashMap<String, Object>();
        automationExecution.put("context", context);
        automationExecution.put("params", parameters);
        var entity = MultipartEntityBuilder.create()
                                           .addTextBody("execution", safeWriteValue(automationExecution),
                                                   ContentType.APPLICATION_JSON)
                                           .addBinaryBody("content", input)
                                           .build();
        try (var stream = entity.getContent()) {
            httpClient.buildPostRequest("/api/v1/automation/" + operationId)
                      .contentType(entity.getContentType().getValue())
                      .entity(stream)
                      .executeAndConsume(new HttpStatusCodeHandler(),
                              status -> assertTrue("Status is not in the 2xx range",
                                      status >= SC_OK && status < SC_MULTIPLE_CHOICES));
        } catch (IOException e) {
            throw new NuxeoException("Unable to marshall the multipart entity", e);
        }
    }

    /**
     * Logs on server with <code>RestHelper</code> as source and <code>warn</code> as level.
     */
    public void logOnServer(String message) {
        logOnServer("warn", message);
    }

    /**
     * Logs on server with <code>RestHelper</code> as source.
     */
    public void logOnServer(String level, String message) {
        logOnServer("RestTestRule", level, message);
    }

    /**
     * @param source the logger source, usually RestHelper or WebDriver
     * @param level the log level
     */
    public void logOnServer(String source, String level, String message) {
        operation("Log", Map.of( //
                // for backward compatibility
                "category", "org.nuxeo.functionaltests.RestHelper", //
                "level", level, //
                "message", String.format("----- %s: %s", source, message) //
        ));
    }

    // -------------
    // HTTP Services
    // -------------

    /**
     * Performs a POST request and return whether or not request was successful.
     */
    public boolean post(String path, String body) {
        return httpClient.buildPostRequest(path)
                         .entity(body)
                         .contentType(MediaType.APPLICATION_OCTET_STREAM)
                         .accept(MediaType.WILDCARD)
                         .executeAndThen(new HttpStatusCodeHandler(), status -> status < 300);
    }

    protected static class MapHandler extends AbstractStatusCodeHandler<Map<String, Object>> {

        @Override
        @SuppressWarnings("unchecked")
        protected Map<String, Object> doHandleResponse(HttpResponse response) throws IOException {
            return MAPPER.readValue(response.getEntityInputStream(), Map.class);
        }
    }
}
