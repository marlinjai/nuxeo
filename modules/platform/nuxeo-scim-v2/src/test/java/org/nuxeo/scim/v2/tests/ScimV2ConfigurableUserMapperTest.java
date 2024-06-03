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

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.function.ThrowableConsumer;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.scim.v2.mapper.AbstractMapper;
import org.nuxeo.scim.v2.mapper.ConfigurableUserMapper;
import org.nuxeo.scim.v2.mapper.UserMapperFactory;

import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.Group;
import com.unboundid.scim2.common.types.UserResource;

/**
 * Tests the {@link ConfigurableUserMapper}, that handles user mapping based on contributions.
 *
 * @since 2023.13
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
public class ScimV2ConfigurableUserMapperTest {

    protected static final String BASE_URL = "http://localhost:8080/nuxeo/scim/v2/Users";

    protected static AbstractMapper mapper;

    @Inject
    protected UserManager userManager;

    @BeforeClass
    public static void begin() {
        mapper = UserMapperFactory.getMapper();
    }

    @Test
    public void testCreateUser() {
        // create user with no displayName and no emails
        var userResource = newUserResource("joe", null, null);
        withUserModel(userResource, mapper::createNuxeoUserFromUserResource,
                userModel -> checkUserModel(userModel, "joe", null, null, null));

        // create user with blank displayName and empty emails
        userResource = newUserResource("joe", "", List.of());
        withUserModel(userResource, mapper::createNuxeoUserFromUserResource,
                userModel -> checkUserModel(userModel, "joe", null, null, null));

        // create user with displayName and emails
        userResource = newUserResource("joe", "Joe Doe", List.of("joe@devnull.com")); // NOSONAR
        withUserModel(userResource, mapper::createNuxeoUserFromUserResource,
                userModel -> checkUserModel(userModel, "joe", "Joe", "Doe", "joe@devnull.com"));

        // create user with displayName (single string) and emails
        userResource = newUserResource("joe", "Joe", List.of("joe@devnull.com"));
        withUserModel(userResource, mapper::createNuxeoUserFromUserResource,
                userModel -> checkUserModel(userModel, "joe", "Joe", null, "joe@devnull.com"));
    }

    @Test
    public void testCreateUpdateUser() {
        // update user with no displayName and no emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", () -> {
            var userResource = newUserResource("joe", null, null);
            var userModel = mapper.createNuxeoUserFromUserResource(userResource);
            checkUserModel(userModel, "joe", "Joe", "Doe", "joe@devnull.com");
        });

        // update user with blank displayName and empty emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", () -> {
            var userResource = newUserResource("joe", "", List.of());
            var userModel = mapper.createNuxeoUserFromUserResource(userResource);
            checkUserModel(userModel, "joe", null, null, null);
        });

        // update user with displayName and emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", () -> {
            var userResource = newUserResource("joe", "Jack Fat", List.of("jack@devnull.com")); // NOSONAR
            var userModel = mapper.createNuxeoUserFromUserResource(userResource);
            checkUserModel(userModel, "joe", "Jack", "Fat", "jack@devnull.com");
        });

        // update user with displayName (single string) and emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", () -> {
            var userResource = newUserResource("joe", "Jack", List.of("jack@devnull.com"));
            var userModel = mapper.createNuxeoUserFromUserResource(userResource);
            checkUserModel(userModel, "joe", "Jack", null, "jack@devnull.com");
        });
    }

    @Test
    public void testReadUser() {
        // get user with no firstName, no lastName, no email and default group only
        withUserModel("joe", null, null, null, null, ThrowableConsumer.asConsumer(userModel -> {
            var userResource = mapper.getUserResourceFromNuxeoUser(userModel, BASE_URL);
            checkUserResource(userResource, "joe", null, null, null, List.of("defgr")); // NOSONAR
        }));

        // get user with blank firstName, blank lastName, blank email and default group only
        withUserModel("joe", "", "", "", null, ThrowableConsumer.asConsumer(userModel -> {
            var userResource = mapper.getUserResourceFromNuxeoUser(userModel, BASE_URL);
            checkUserResource(userResource, "joe", null, null, null, List.of("defgr"));
        }));

        // get user with firstName, no lastName, no email and default group only
        withUserModel("joe", "Joe", null, null, null, ThrowableConsumer.asConsumer(userModel -> {
            var userResource = mapper.getUserResourceFromNuxeoUser(userModel, BASE_URL);
            checkUserResource(userResource, "joe", "Joe", null, null, List.of("defgr"));
        }));

        // get user with no firstName, lastName, no email and default group only
        withUserModel("joe", null, "Doe", null, null, ThrowableConsumer.asConsumer(userModel -> {
            var userResource = mapper.getUserResourceFromNuxeoUser(userModel, BASE_URL);
            checkUserResource(userResource, "joe", null, "Doe", null, List.of("defgr"));
        }));

        // get user with firstName, lastName, email and groups
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", List.of("testGroup"),
                ThrowableConsumer.asConsumer(userModel -> {
                    var userResource = mapper.getUserResourceFromNuxeoUser(userModel, BASE_URL);
                    checkUserResource(userResource, "joe", "Joe", "Doe", List.of("joe@devnull.com"),
                            List.of("testGroup", "defgr"));
                }));
    }

    @Test
    public void testUpdateUser() {
        // update user with no displayName and no emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", () -> {
            var userResource = newUserResource("joe", null, null);
            var userModel = mapper.updateNuxeoUserFromUserResource("joe", userResource);
            checkUserModel(userModel, "joe", "Joe", "Doe", "joe@devnull.com");
        });

        // update user with blank displayName and empty emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", () -> {
            var userResource = newUserResource("joe", "", List.of());
            var userModel = mapper.updateNuxeoUserFromUserResource("joe", userResource);
            checkUserModel(userModel, "joe", null, null, null);
        });

        // update user with displayName and emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", () -> {
            var userResource = newUserResource("joe", "Jack Fat", List.of("jack@devnull.com"));
            var userModel = mapper.updateNuxeoUserFromUserResource("joe", userResource);
            checkUserModel(userModel, "joe", "Jack", "Fat", "jack@devnull.com");
        });

        // update user with displayName (single string) and emails
        withUserModel("joe", "Joe", "Doe", "joe@devnull.com", () -> {
            var userResource = newUserResource("joe", "Jack", List.of("jack@devnull.com"));
            var userModel = mapper.updateNuxeoUserFromUserResource("joe", userResource);
            checkUserModel(userModel, "joe", "Jack", null, "jack@devnull.com");
        });
    }

    protected UserResource newUserResource(String username, String displayName, List<String> emails) {
        Objects.requireNonNull(username);
        var userResource = new UserResource();
        userResource.setUserName(username);
        if (displayName != null) {
            userResource.setDisplayName(displayName);
        }
        if (emails != null) {
            userResource.setEmails(emails.stream().map(email -> new Email().setValue(email)).toList());
        }
        return userResource;
    }

    protected void withUserModel(UserResource userResource, Function<UserResource, DocumentModel> mapper,
            Consumer<DocumentModel> consumer) {
        withUserModel(() -> mapper.apply(userResource), consumer);
    }

    protected void withUserModel(String username, String firstName, String lastName, String email, Runnable runnable) {
        withUserModel(username, firstName, lastName, email, null, unused -> runnable.run());
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
        assertEquals(String.format("%s/%s", BASE_URL, username), meta.getLocation().toString());
        assertEquals("1", meta.getVersion());
        assertEquals(username, userResource.getUserName());
        String displayName = getDisplayName(firstName, lastName);
        assertEquals(displayName, userResource.getDisplayName());
        var name = userResource.getName();
        if (displayName == null) {
            assertNull(name);
        } else {
            assertNotNull(name);
            assertEquals(displayName, name.getFormatted());
            assertEquals(firstName, name.getGivenName());
            assertEquals(lastName, name.getFamilyName());
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

}
