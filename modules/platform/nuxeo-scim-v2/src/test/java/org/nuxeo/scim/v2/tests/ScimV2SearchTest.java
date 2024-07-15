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
 *     Guillaume Renard
 */
package org.nuxeo.scim.v2.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.common.utils.DateUtils.formatISODateTime;

import java.io.Serializable;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.scim.v2.api.ScimV2MappingService;
import org.nuxeo.scim.v2.api.ScimV2QueryContext;

import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;

/**
 * Tests the Scim v2 Search Filtering.
 *
 * @since 2023.14
 */
@Deploy("org.nuxeo.scim.v2:test-scim-v2-user-schema-override.xml")
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
public class ScimV2SearchTest {

    @Inject
    protected ScimV2MappingService mappingService;

    @Inject
    protected UserManager userManager;

    @Test
    public void testEmailsContains() throws ScimException {
        newUserModel("jdoe", "John", "Doe", "jdoe@the.org", 1, Calendar.getInstance(), null); // NOSONAR
        assertEquals(1, countUsers("emails co \"the\""));
        assertEquals(1, countUsers("emails.value co \"doe\""));
    }

    @Test
    public void testEmployeeNumberGreaterThan() throws ScimException {
        newUserModel("jdoe", "John", "Doe", "jdoe@the.org", 10, Calendar.getInstance(), null);
        assertEquals(0, countUsers("employeeNumber gt 10"));
        assertEquals(1, countUsers("employeeNumber gt 9"));
        assertEquals(1, countUsers("employeeNumber ge 10"));
    }

    @Test
    public void testEmployeeNumberLessThan() throws ScimException {
        newUserModel("jdoe", "John", "Doe", "jdoe@the.org", 5, Calendar.getInstance(), null);
        assertEquals(0, countUsers("employeeNumber lt 4"));
        assertEquals(1, countUsers("employeeNumber lt 6"));
        assertEquals(1, countUsers("employeeNumber le 5"));
    }

    @Test
    public void testTooManyResults() {
        assertThrows(BadRequestException.class,
                () -> mappingService.queryUsers(new ScimV2QueryContext().withCount(10000)));
    }

    @Test
    public void testUpdatedGreaterThan() throws ScimException {
        Calendar updated = Calendar.getInstance();
        updated.add(Calendar.HOUR, -1);
        Calendar now = Calendar.getInstance();
        newUserModel("jdoe", "John", "Doe", "jdoe@the.org", 10, updated, null);
        assertEquals(0, countUsers("scim_updated gt \"" + formatISODateTime(now) + "\""));
        assertEquals(1, countUsers("scim_updated ge \"" + formatISODateTime(updated) + "\""));
    }

    @Test
    public void testUpdatedLessThan() throws ScimException {
        Calendar updated = Calendar.getInstance();
        updated.add(Calendar.HOUR, 1);
        String now = formatISODateTime(Calendar.getInstance());
        newUserModel("jdoe", "John", "Doe", "jdoe@the.org", 10, updated, null);
        assertEquals(0, countUsers("scim_updated lt \"" + now + "\""));
        assertEquals(1, countUsers("scim_updated le \"" + formatISODateTime(updated) + "\""));
    }

    // Test combining filter
    @Test
    public void testUpdatedOrEmployeeNumberPresentOrNot() throws ScimException {
        newUserModel("jsmith", "John", "Smith", "jsmith@the.org", 3, null, null);
        newUserModel("jdoe", "John", "Doe", "jdoe@the.org", null, Calendar.getInstance(), null);
        assertEquals(2, countUsers("employeeNumber pr or scim_updated pr")); // jsmith & jdoe
        assertEquals(1, countUsers("employeeNumber pr and not(scim_updated pr)")); // jsmith
        assertEquals(2, countUsers("not (employeeNumber pr) and not (scim_updated pr)")); // Administrator & Guest
        assertEquals(2, countUsers("not (employeeNumber pr or scim_updated pr)")); // Administrator & Guest
    }

    @Test
    public void testUsernameNotPresent() throws ScimException {
        assertTrue(0 < countUsers("username pr")); // Administrator & Guest
        assertEquals(0, countUsers("not (username pr)"));
    }

    @Test
    public void testUsernameEqualsCaseInsensitive() throws ScimException {
        assertEquals(1, countUsers("username eq \"administrator\""));
        assertEquals(1, countUsers("username eq \"Administrator\""));
    }

    @Test
    public void testUsernameStartsWith() throws ScimException {
        newUserModel("jdoe", "John", "Doe", "jdoe@the.org", 1, Calendar.getInstance(), null);
        assertEquals(1, countUsers("id sw \"jd\""));
        assertEquals(0, countUsers("id sw \"jja\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSortByUsername() throws ScimException {
        newUserModel("Zee", "Zee", "Zee", "zee@the.org", 1, Calendar.getInstance(), null);
        newUserModel("Too", "Too", "Too", "too@the.org", 1, Calendar.getInstance(), null);
        newUserModel("Ham", "Ham", "Ham", "ham@the.org", 1, Calendar.getInstance(), null);
        ListResponse<ScimResource> users = mappingService.queryUsers(
                new ScimV2QueryContext().withCount(10).withSortBy("id"));
        assertEquals(5, users.getResources().size());
        assertEquals("Administrator", ((Map<String, Object>) users.getResources().get(0)).get("id"));
        assertEquals("Guest", ((Map<String, Object>) users.getResources().get(1)).get("id"));
        assertEquals("Ham", ((Map<String, Object>) users.getResources().get(2)).get("id"));
        assertEquals("Too", ((Map<String, Object>) users.getResources().get(3)).get("id"));
        assertEquals("Zee", ((Map<String, Object>) users.getResources().get(4)).get("id"));
        users = mappingService.queryUsers(new ScimV2QueryContext().withCount(10).withSortBy("id").withDescending(true));
        assertEquals(5, users.getResources().size());
        assertEquals("Administrator", ((Map<String, Object>) users.getResources().get(4)).get("id"));
        assertEquals("Guest", ((Map<String, Object>) users.getResources().get(3)).get("id"));
        assertEquals("Ham", ((Map<String, Object>) users.getResources().get(2)).get("id"));
        assertEquals("Too", ((Map<String, Object>) users.getResources().get(1)).get("id"));
        assertEquals("Zee", ((Map<String, Object>) users.getResources().get(0)).get("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSortByGroupname() throws ScimException {
        newGroupModel("a", "Groupe a");
        newGroupModel("b", "Groupe b");
        newGroupModel("c", "Groupe c");
        ListResponse<ScimResource> groups = mappingService.queryGroups(
                new ScimV2QueryContext().withCount(10).withSortBy("id"));
        assertEquals(6, groups.getResources().size());
        assertEquals("a", ((Map<String, Object>) groups.getResources().get(0)).get("id"));
        assertEquals("administrators", ((Map<String, Object>) groups.getResources().get(1)).get("id"));
        assertEquals("b", ((Map<String, Object>) groups.getResources().get(2)).get("id"));
        assertEquals("c", ((Map<String, Object>) groups.getResources().get(3)).get("id"));
        assertEquals("members", ((Map<String, Object>) groups.getResources().get(4)).get("id"));
        assertEquals("powerusers", ((Map<String, Object>) groups.getResources().get(5)).get("id"));
        groups = mappingService.queryGroups(
                new ScimV2QueryContext().withCount(10).withSortBy("id").withDescending(true));
        assertEquals(6, groups.getResources().size());
        assertEquals("a", ((Map<String, Object>) groups.getResources().get(5)).get("id"));
        assertEquals("administrators", ((Map<String, Object>) groups.getResources().get(4)).get("id"));
        assertEquals("b", ((Map<String, Object>) groups.getResources().get(3)).get("id"));
        assertEquals("c", ((Map<String, Object>) groups.getResources().get(2)).get("id"));
        assertEquals("members", ((Map<String, Object>) groups.getResources().get(1)).get("id"));
        assertEquals("powerusers", ((Map<String, Object>) groups.getResources().get(0)).get("id"));
    }

    protected int countUsers(String filter) throws ScimException {
        return mappingService.queryUsers(
                new ScimV2QueryContext().withCount(10).withSortBy("id").withFilterString(filter)).getTotalResults();
    }

    protected DocumentModel newUserModel(String username, String firstName, String lastName, String email,
            Integer employeeNumber, Calendar updated, List<String> groups) {
        Objects.requireNonNull(username);
        var userModel = userManager.getBareUserModel();
        userModel.setPropertyValue("user:username", username);
        userModel.setPropertyValue("user:firstName", firstName);
        userModel.setPropertyValue("user:lastName", lastName);
        userModel.setPropertyValue("user:email", email);
        userModel.setPropertyValue("user:employeeNumber", employeeNumber);
        userModel.setPropertyValue("user:scim_updated", updated);
        if (groups != null) {
            for (String group : groups) {
                var groupModel = userManager.getBareGroupModel();
                groupModel.setPropertyValue("groupname", group);
                userManager.createGroup(groupModel);
            }
        }
        userModel.setPropertyValue("user:groups", (Serializable) groups);
        userModel = userManager.createUser(userModel);
        return userModel;
    }

    protected DocumentModel newGroupModel(String id, String label) {
        Objects.requireNonNull(id);
        DocumentModel groupModel = userManager.getBareGroupModel();
        groupModel.setProperty(userManager.getGroupSchemaName(), userManager.getGroupIdField(), id);
        groupModel.setProperty(userManager.getGroupSchemaName(), userManager.getGroupLabelField(), label);
        userManager.createGroup(groupModel);
        return groupModel;
    }
}
