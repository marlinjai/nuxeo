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
 *     Antoine Taillefer
 */
package org.nuxeo.scim.v2.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.scim.v2.api.ScimV2ResourceType.SCIM_V2_RESOURCE_TYPE_GROUP;
import static org.nuxeo.scim.v2.api.ScimV2ResourceType.SCIM_V2_RESOURCE_TYPE_USER;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.function.ThrowableConsumer;
import org.nuxeo.common.function.ThrowableFunction;
import org.nuxeo.common.function.ThrowableRunnable;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.scim.v2.api.ScimV2MappingService;

import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.Group;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Member;
import com.unboundid.scim2.common.types.Name;
import com.unboundid.scim2.common.types.UserResource;

/**
 * Tests the {@link ScimV2MappingService}, that handles user and group mapping.
 *
 * @since 2023.14
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
public class ScimV2MappingServiceTest {

    protected static final String GROUP_BASE_URL = "http://localhost:8080/nuxeo/scim/v2/Groups";

    protected static final String USER_BASE_URL = "http://localhost:8080/nuxeo/scim/v2/Users";

    @Inject
    protected ScimV2MappingService mappingService;

    @Inject
    protected UserManager userManager;

    @Test
    public void testCreateGroup() {
        // create group with no members and no subgroups
        var groupResource = newGroupResource("people", null, null); // NOSONAR
        withGroupModel(groupResource, ThrowableFunction.asFunction(mappingService::createNuxeoGroupFromGroupResource),
                groupModel -> checkGroupModel(groupModel, "people", List.of(), List.of()));

        // create group with empty members and empty subgroups
        groupResource = newGroupResource("people", List.of(), List.of());
        withGroupModel(groupResource, ThrowableFunction.asFunction(mappingService::createNuxeoGroupFromGroupResource),
                groupModel -> checkGroupModel(groupModel, "people", List.of(), List.of()));

        // create group with members and subgroups
        groupResource = newGroupResource("people", List.of("joe", "jack"), List.of("subGroup1", "subGroup2")); // NOSONAR
        withGroupModel(groupResource, ThrowableFunction.asFunction(mappingService::createNuxeoGroupFromGroupResource),
                groupModel -> checkGroupModel(groupModel, "people", List.of("joe", "jack"),
                        List.of("subGroup1", "subGroup2")));
    }

    @Test
    public void testCreateUser() {
        // create user with no givenName, no familyName and no emails
        var userResource = newUserResource("joe", null, null, null);
        withUserModel(userResource, ThrowableFunction.asFunction(mappingService::createNuxeoUserFromUserResource),
                userModel -> checkUserModel(userModel, "joe", null, null, null));

        // create user with blank givenName, blank familyName and empty emails
        userResource = newUserResource("joe", "", "", List.of());
        withUserModel(userResource, ThrowableFunction.asFunction(mappingService::createNuxeoUserFromUserResource),
                userModel -> checkUserModel(userModel, "joe", null, null, null));

        // create user with givenName, no familyName and emails
        userResource = newUserResource("joe", "Joe", null, List.of("joe@devnull.com")); // NOSONAR
        withUserModel(userResource, ThrowableFunction.asFunction(mappingService::createNuxeoUserFromUserResource),
                userModel -> checkUserModel(userModel, "joe", "Joe", null, "joe@devnull.com"));

        // create user with no givenName, familyName and emails
        userResource = newUserResource("joe", null, "Doe", List.of("joe@devnull.com")); // NOSONAR
        withUserModel(userResource, ThrowableFunction.asFunction(mappingService::createNuxeoUserFromUserResource),
                userModel -> checkUserModel(userModel, "joe", null, "Doe", "joe@devnull.com"));

        // create user with givenName, familyName and emails
        userResource = newUserResource("joe", "Joe", "Doe", List.of("joe@devnull.com"));
        withUserModel(userResource, ThrowableFunction.asFunction(mappingService::createNuxeoUserFromUserResource),
                userModel -> checkUserModel(userModel, "joe", "Joe", "Doe", "joe@devnull.com"));
    }

    @Test
    public void testReadGroup() {
        // get group with no grouplabel, no members and no subgroups
        withGroupModel("people", null, null, null, ThrowableConsumer.asConsumer(groupModel -> {
            var groupResource = mappingService.getGroupResourceFromNuxeoGroup(groupModel, GROUP_BASE_URL);
            checkGroupResource(groupResource, "people", null, List.of(), List.of());
        }));

        // get group with blank grouplabel, empty members and empty subgroups
        withGroupModel("people", "", List.of(), List.of(), ThrowableConsumer.asConsumer(groupModel -> {
            var groupResource = mappingService.getGroupResourceFromNuxeoGroup(groupModel, GROUP_BASE_URL);
            checkGroupResource(groupResource, "people", null, List.of(), List.of());
        }));

        // get group with grouplabel, members and subgroups
        withGroupModel("people", "People", List.of("joe", "jack"), List.of("subGroup1", "subGroup2"), //NOSONAR
                ThrowableConsumer.asConsumer(groupModel -> {
                    var groupResource = mappingService.getGroupResourceFromNuxeoGroup(groupModel, GROUP_BASE_URL);
                    checkGroupResource(groupResource, "people", "People", List.of("joe", "jack"),
                            List.of("subGroup1", "subGroup2"));
                }));
    }

    @Test
    public void testReadUser() {
        // get user with no firstName, no lastName, no email and default group only
        withUserModel("joe", null, null, null, null, ThrowableConsumer.asConsumer(userModel -> {
            var userResource = mappingService.getUserResourceFromNuxeoUser(userModel, USER_BASE_URL);
            checkUserResource(userResource, "joe", null, null, null, List.of("defgr")); // NOSONAR
        }));

        // get user with blank firstName, blank lastName, blank email and default group only
        withUserModel("joe", "", "", "", null, ThrowableConsumer.asConsumer(userModel -> {
            var userResource = mappingService.getUserResourceFromNuxeoUser(userModel, USER_BASE_URL);
            checkUserResource(userResource, "joe", null, null, null, List.of("defgr"));
        }));

        // get user with firstName, no lastName, no email and default group only
        withUserModel("joe", "Joe", null, null, null, ThrowableConsumer.asConsumer(userModel -> {
            var userResource = mappingService.getUserResourceFromNuxeoUser(userModel, USER_BASE_URL);
            checkUserResource(userResource, "joe", "Joe", null, null, List.of("defgr"));
        }));

        // get user with no firstName, lastName, no email and default group only
        withUserModel("joe", null, "Doe", null, null, ThrowableConsumer.asConsumer(userModel -> {
            var userResource = mappingService.getUserResourceFromNuxeoUser(userModel, USER_BASE_URL);
            checkUserResource(userResource, "joe", null, "Doe", null, List.of("defgr"));
        }));

        // get user with firstName, lastName, email and groups
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", List.of("testGroup"),
                ThrowableConsumer.asConsumer(userModel -> {
                    var userResource = mappingService.getUserResourceFromNuxeoUser(userModel, USER_BASE_URL);
                    checkUserResource(userResource, "joe", "Joe", "Doe", List.of("joe@devnull.com"),
                            List.of("testGroup", "defgr"));
                }));
    }

    @Test
    public void testUpdateGroup() {
        // update group with no displayName, no members and no subgroups
        withGroupModel("people", "People", List.of("joe"), List.of("subGroup1"), ThrowableRunnable.asRunnable(() -> {
            var groupResource = newGroupResource(null, null, null);
            var groupModel = mappingService.updateNuxeoGroupFromGroupResource("people", groupResource);
            checkGroupModel(groupModel, "People", List.of("joe"), List.of("subGroup1"));
        }));

        // update group with blank displayName, empty members and empty subgroups
        withGroupModel("people", "People", List.of("joe"), List.of("subGroup1"), ThrowableRunnable.asRunnable(() -> {
            var groupResource = newGroupResource("", List.of(), List.of());
            var groupModel = mappingService.updateNuxeoGroupFromGroupResource("people", groupResource);
            checkGroupModel(groupModel, "", List.of(), List.of());
        }));

        // update group with displayName, members and subgroups
        withGroupModel("people", "People", List.of("joe"), List.of("subGroup1"), ThrowableRunnable.asRunnable(() -> {
            var groupResource = newGroupResource("OtherPeople", List.of("joe", "jack"),
                    List.of("subGroup1", "subGroup2"));
            var groupModel = mappingService.updateNuxeoGroupFromGroupResource("people", groupResource);
            checkGroupModel(groupModel, "OtherPeople", List.of("joe", "jack"), List.of("subGroup1", "subGroup2"));
        }));
    }

    @Test
    public void testUpdateUser() {
        // update user with no givenName, no familyName and no emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", ThrowableRunnable.asRunnable(() -> {
            var userResource = newUserResource("joe", null, null, null);
            var userModel = mappingService.updateNuxeoUserFromUserResource("joe", userResource);
            checkUserModel(userModel, "joe", "Joe", "Doe", "joe@devnull.com");
        }));

        // update user with blank givenName, blank familyName and empty emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", ThrowableRunnable.asRunnable(() -> {
            var userResource = newUserResource("joe", "", "", List.of());
            var userModel = mappingService.updateNuxeoUserFromUserResource("joe", userResource);
            checkUserModel(userModel, "joe", null, null, null);
        }));

        // update user with givenName, no familyName and emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", ThrowableRunnable.asRunnable(() -> {
            var userResource = newUserResource("joe", "Jack", null, List.of("jack@devnull.com")); // NOSONAR
            var userModel = mappingService.updateNuxeoUserFromUserResource("joe", userResource);
            checkUserModel(userModel, "joe", "Jack", "Doe", "jack@devnull.com");
        }));

        // update user with no givenName, familyName and emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", ThrowableRunnable.asRunnable(() -> {
            var userResource = newUserResource("joe", null, "Fat", List.of("jack@devnull.com"));
            var userModel = mappingService.updateNuxeoUserFromUserResource("joe", userResource);
            checkUserModel(userModel, "joe", "Joe", "Fat", "jack@devnull.com");
        }));

        // update user with givenName, familyName and emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", ThrowableRunnable.asRunnable(() -> {
            var userResource = newUserResource("joe", "Jack", "Fat", List.of("jack@devnull.com"));
            var userModel = mappingService.updateNuxeoUserFromUserResource("joe", userResource);
            checkUserModel(userModel, "joe", "Jack", "Fat", "jack@devnull.com");
        }));
    }

    protected void checkGroupModel(DocumentModel groupModel, String groupLabel, List<String> members,
            List<String> subGroups) {
        assertNotNull(groupModel.getPropertyValue("group:groupname"));
        assertEquals(groupLabel, groupModel.getPropertyValue("group:grouplabel"));
        assertEquals(members, groupModel.getPropertyValue("group:members"));
        assertEquals(subGroups, groupModel.getPropertyValue("group:subGroups"));
    }

    protected void checkGroupResource(GroupResource groupResource, String groupName, String groupLabel,
            List<String> members, List<String> subGroups) {
        assertEquals(groupName, groupResource.getId());
        assertEquals(groupName, groupResource.getExternalId());
        var meta = groupResource.getMeta();
        assertEquals(String.format("%s/%s", GROUP_BASE_URL, groupName), meta.getLocation().toString());
        assertEquals("1", meta.getVersion());
        assertEquals(groupLabel, groupResource.getDisplayName());
        List<Member> actualMembers = groupResource.getMembers();
        assertNotNull(actualMembers);
        List<String> actualUserMembers = actualMembers.stream()
                                                      .filter(member -> SCIM_V2_RESOURCE_TYPE_USER.toString()
                                                                                                  .equals(member.getType()))
                                                      .map(Member::getValue)
                                                      .toList();
        List<String> actualGroupMembers = actualMembers.stream()
                                                       .filter(member -> SCIM_V2_RESOURCE_TYPE_GROUP.toString()
                                                                                                    .equals(member.getType()))
                                                       .map(Member::getValue)
                                                       .toList();
        assertEquals(members, actualUserMembers);
        assertEquals(subGroups, actualGroupMembers);
    }

    protected void checkUserModel(DocumentModel userModel, String username, String firstName, String lastName,
            String email) {
        assertEquals(username, userModel.getPropertyValue("user:username"));
        assertEquals(firstName, userModel.getPropertyValue("user:firstName"));
        assertEquals(lastName, userModel.getPropertyValue("user:lastName"));
        assertEquals(email, userModel.getPropertyValue("user:email"));
    }

    protected void checkUserResource(UserResource userResource, String username, String firstName, String lastName,
            List<String> emails, List<String> groups) {
        assertEquals(username, userResource.getId());
        assertEquals(username, userResource.getExternalId());
        var meta = userResource.getMeta();
        assertEquals(String.format("%s/%s", USER_BASE_URL, username), meta.getLocation().toString());
        assertEquals("1", meta.getVersion());
        assertEquals(username, userResource.getUserName());
        var name = userResource.getName();
        if (firstName == null && lastName == null) {
            assertNull(name);
        } else {
            assertNotNull(name);
            if (firstName != null) {
                assertEquals(firstName, name.getGivenName());
            }
            if (lastName != null) {
                assertEquals(lastName, name.getFamilyName());
            }
            String displayName = getDisplayName(firstName, lastName);
            assertEquals(displayName, name.getFormatted());
            assertEquals(displayName, userResource.getDisplayName());
        }
        List<Email> actualEmails = userResource.getEmails();
        if (emails == null) {
            assertNull(actualEmails);
        } else {
            assertNotNull(actualEmails);
            assertEquals(emails, actualEmails.stream().map(Email::getValue).toList());
        }
        List<Group> actualGroups = userResource.getGroups();
        if (groups == null) {
            assertNull(actualGroups);
        } else {
            assertNotNull(actualGroups);
            assertEquals(groups, actualGroups.stream().map(Group::getValue).toList());
        }
        assertTrue(userResource.getActive());
    }

    protected String getDisplayName(String firstName, String lastName) {
        String displayName = null;
        if (firstName != null && lastName != null) {
            displayName = String.join(" ", firstName, lastName);
        } else if (firstName != null) {
            displayName = firstName;
        } else if (lastName != null) {
            displayName = lastName;
        }
        return displayName;
    }

    protected DocumentModel newGroupModel(String groupName, String groupLabel, List<String> members,
            List<String> subGroups) {
        Objects.requireNonNull(groupName);
        var groupModel = userManager.getBareGroupModel();
        groupModel.setPropertyValue("group:groupname", groupName);
        groupModel.setPropertyValue("group:grouplabel", groupLabel);
        if (members != null) {
            for (String member : members) {
                var userModel = userManager.getBareUserModel();
                userModel.setPropertyValue("username", member);
                userManager.createUser(userModel);
            }
        }
        groupModel.setPropertyValue("group:members", (Serializable) members);
        if (subGroups != null) {
            for (String subGroup : subGroups) {
                var subGroupModel = userManager.getBareGroupModel();
                subGroupModel.setPropertyValue("groupname", subGroup);
                userManager.createGroup(subGroupModel);
            }
        }
        groupModel.setPropertyValue("group:subGroups", (Serializable) subGroups);
        groupModel = userManager.createGroup(groupModel);
        return groupModel;
    }

    protected GroupResource newGroupResource(String groupName, List<String> userMembers, List<String> groupMembers) {
        var groupResource = new GroupResource();
        groupResource.setDisplayName(groupName);
        if (userMembers == null && groupMembers == null) {
            return groupResource;
        }
        List<Member> members = new ArrayList<>();
        if (userMembers != null) {
            members.addAll(
                    userMembers.stream()
                               .map(id -> new Member().setType(SCIM_V2_RESOURCE_TYPE_USER.toString()).setValue(id))
                               .toList());
        }
        if (groupMembers != null) {
            members.addAll(
                    groupMembers.stream()
                                .map(id -> new Member().setType(SCIM_V2_RESOURCE_TYPE_GROUP.toString()).setValue(id))
                                .toList());
        }
        groupResource.setMembers(members);
        return groupResource;
    }

    protected DocumentModel newUserModel(String username, String firstName, String lastName, String email,
            List<String> groups) {
        Objects.requireNonNull(username);
        var userModel = userManager.getBareUserModel();
        userModel.setPropertyValue("user:username", username);
        userModel.setPropertyValue("user:firstName", firstName);
        userModel.setPropertyValue("user:lastName", lastName);
        userModel.setPropertyValue("user:email", email);
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

    protected UserResource newUserResource(String username, String givenName, String familyName, List<String> emails) {
        Objects.requireNonNull(username);
        var userResource = new UserResource();
        userResource.setUserName(username);
        if (givenName != null || familyName != null) {
            Name name = new Name();
            if (givenName != null) {
                name.setGivenName(givenName);
            }
            if (familyName != null) {
                name.setFamilyName(familyName);
            }
            userResource.setName(name);
        }
        if (emails != null) {
            userResource.setEmails(emails.stream().map(email -> new Email().setValue(email)).toList());
        }
        return userResource;
    }

    protected void withGroupModel(GroupResource groupResource, Function<GroupResource, DocumentModel> mapper,
            Consumer<DocumentModel> consumer) {
        withGroupModel(() -> mapper.apply(groupResource), consumer);
    }

    protected void withGroupModel(String groupName, String groupLabel, List<String> members, List<String> subGroups,
            Consumer<DocumentModel> consumer) {
        try {
            withGroupModel(() -> newGroupModel(groupName, groupLabel, members, subGroups), consumer);
        } finally {
            if (subGroups != null) {
                for (String member : members) {
                    try {
                        userManager.deleteUser(member);
                    } catch (NuxeoException e) {
                        // nothing to do
                    }
                }
                for (String subGroup : subGroups) {
                    try {
                        userManager.deleteGroup(subGroup);
                    } catch (NuxeoException e) {
                        // nothing to do
                    }
                }
            }
        }
    }

    protected void withGroupModel(String groupName, String groupLabel, List<String> members, List<String> subGroups,
            Runnable runnable) {
        withGroupModel(groupName, groupLabel, members, subGroups, unused -> runnable.run());
    }

    protected void withGroupModel(Supplier<DocumentModel> supplier, Consumer<DocumentModel> consumer) {
        DocumentModel groupModel = null;
        try {
            groupModel = supplier.get();
            consumer.accept(groupModel);
        } finally {
            if (groupModel != null) {
                userManager.deleteGroup(groupModel);
            }
        }
    }

    protected void withUserModel(String username, String firstName, String lastName, String email, List<String> groups,
            Consumer<DocumentModel> consumer) {
        try {
            withUserModel(() -> newUserModel(username, firstName, lastName, email, groups), consumer);
        } finally {
            if (groups != null) {
                for (String group : groups) {
                    try {
                        userManager.deleteGroup(group);
                    } catch (NuxeoException e) {
                        // nothing to do
                    }
                }
            }
        }
    }

    protected void withUserModel(String username, String firstName, String lastName, String email, Runnable runnable) {
        withUserModel(username, firstName, lastName, email, null, unused -> runnable.run());
    }

    protected void withUserModel(Supplier<DocumentModel> supplier, Consumer<DocumentModel> consumer) {
        DocumentModel userModel = null;
        try {
            userModel = supplier.get();
            consumer.accept(userModel);
        } finally {
            if (userModel != null) {
                userManager.deleteUser(userModel);
            }
        }
    }

    protected void withUserModel(UserResource userResource, Function<UserResource, DocumentModel> mapper,
            Consumer<DocumentModel> consumer) {
        withUserModel(() -> mapper.apply(userResource), consumer);
    }

}
