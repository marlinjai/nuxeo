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
import static org.nuxeo.scim.v2.rest.ScimV2Root.SCIM_V2_RESOURCE_TYPE_GROUP;
import static org.nuxeo.scim.v2.rest.ScimV2Root.SCIM_V2_RESOURCE_TYPE_USER;

import java.io.Serializable;
import java.util.ArrayList;
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
import org.nuxeo.scim.v2.mapper.UserMapperFactory;

import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Member;

/**
 * Tests the {@link AbstractMapper}, that handles group mapping.
 *
 * @since 2023.14
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
public class ScimV2AbstractMapperTest {

    protected static final String BASE_URL = "http://localhost:8080/nuxeo/scim/v2/Groups";

    protected static AbstractMapper mapper;

    @Inject
    protected UserManager userManager;

    @BeforeClass
    public static void begin() {
        mapper = UserMapperFactory.getMapper();
    }

    @Test
    public void testCreateGroup() {
        // create group with no members and no subgroups
        var groupResource = newGroupResource("people", null, null); // NOSONAR
        withGroupModel(groupResource, mapper::createNuxeoGroupFromGroupResource,
                groupModel -> checkGroupModel(groupModel, "people", "people", List.of(), List.of()));

        // create group with empty members and empty subgroups
        groupResource = newGroupResource("people", List.of(), List.of());
        withGroupModel(groupResource, mapper::createNuxeoGroupFromGroupResource,
                groupModel -> checkGroupModel(groupModel, "people", "people", List.of(), List.of()));

        // create group with members and subgroups
        groupResource = newGroupResource("people", List.of("joe", "jack"), List.of("subGroup1", "subGroup2")); // NOSONAR
        withGroupModel(groupResource, mapper::createNuxeoGroupFromGroupResource,
                groupModel -> checkGroupModel(groupModel, "people", "people", List.of("joe", "jack"),
                        List.of("subGroup1", "subGroup2")));
    }

    @Test
    public void testCreateUpdate() {
        // update group with no members and no subgroups
        withGroupModel("people", "People", List.of("joe"), List.of("subGroup1"), () -> { // NOSONAR
            var groupResource = newGroupResource("people", null, null);
            var groupModel = mapper.createNuxeoGroupFromGroupResource(groupResource);
            checkGroupModel(groupModel, "people", "people", List.of("joe"), List.of("subGroup1"));
        });

        // update group with empty members and empty subgroups
        withGroupModel("people", "People", List.of("joe"), List.of("subGroup1"), () -> {
            var groupResource = newGroupResource("people", List.of(), List.of());
            var groupModel = mapper.createNuxeoGroupFromGroupResource(groupResource);
            checkGroupModel(groupModel, "people", "people", List.of(), List.of());
        });

        // update group with members and subgroups
        withGroupModel("people", "People", List.of("joe"), List.of("subGroup1"), () -> {
            var groupResource = newGroupResource("people", List.of("joe", "jack"), List.of("subGroup1", "subGroup2"));
            var groupModel = mapper.createNuxeoGroupFromGroupResource(groupResource);
            checkGroupModel(groupModel, "people", "people", List.of("joe", "jack"), List.of("subGroup1", "subGroup2"));
        });
    }

    @Test
    public void testRead() {
        // get group with no grouplabel, no members and no subgroups
        withGroupModel("people", null, null, null, ThrowableConsumer.asConsumer(groupModel -> {
            var groupResource = mapper.getGroupResourceFromNuxeoGroup(groupModel, BASE_URL);
            checkGroupResource(groupResource, "people", null, List.of(), List.of());
        }));

        // get group with blank grouplabel, empty members and empty subgroups
        withGroupModel("people", "", List.of(), List.of(), ThrowableConsumer.asConsumer(groupModel -> {
            var groupResource = mapper.getGroupResourceFromNuxeoGroup(groupModel, BASE_URL);
            checkGroupResource(groupResource, "people", null, List.of(), List.of());
        }));

        // get group with grouplabel, members and subgroups
        withGroupModel("people", "People", List.of("joe", "jack"), List.of("subGroup1", "subGroup2"),
                ThrowableConsumer.asConsumer(groupModel -> {
                    var groupResource = mapper.getGroupResourceFromNuxeoGroup(groupModel, BASE_URL);
                    checkGroupResource(groupResource, "people", "People", List.of("joe", "jack"),
                            List.of("subGroup1", "subGroup2"));
                }));
    }

    @Test
    public void testUpdate() {
        // update group with no displayName, no members and no subgroups
        withGroupModel("people", "People", List.of("joe"), List.of("subGroup1"), () -> {
            var groupResource = newGroupResource(null, null, null);
            var groupModel = mapper.updateNuxeoGroupFromGroupResource("people", groupResource);
            checkGroupModel(groupModel, "people", "People", List.of("joe"), List.of("subGroup1"));
        });

        // update group with blank displayName, empty members and empty subgroups
        withGroupModel("people", "People", List.of("joe"), List.of("subGroup1"), () -> {
            var groupResource = newGroupResource("", List.of(), List.of());
            var groupModel = mapper.updateNuxeoGroupFromGroupResource("people", groupResource);
            checkGroupModel(groupModel, "people", "", List.of(), List.of());
        });

        // update group with displayName, members and subgroups
        withGroupModel("people", "People", List.of("joe"), List.of("subGroup1"), () -> {
            var groupResource = newGroupResource("OtherPeople", List.of("joe", "jack"),
                    List.of("subGroup1", "subGroup2"));
            var groupModel = mapper.updateNuxeoGroupFromGroupResource("people", groupResource);
            checkGroupModel(groupModel, "people", "OtherPeople", List.of("joe", "jack"),
                    List.of("subGroup1", "subGroup2"));
        });
    }

    protected GroupResource newGroupResource(String groupName, List<String> userMembers, List<String> groupMembers) {
        var groupResource = new GroupResource();
        groupResource.setDisplayName(groupName);
        if (userMembers == null && groupMembers == null) {
            return groupResource;
        }
        List<Member> members = new ArrayList<>();
        if (userMembers != null) {
            members.addAll(userMembers.stream()
                                      .map(id -> new Member().setType(SCIM_V2_RESOURCE_TYPE_USER).setValue(id))
                                      .toList());
        }
        if (groupMembers != null) {
            members.addAll(groupMembers.stream()
                                       .map(id -> new Member().setType(SCIM_V2_RESOURCE_TYPE_GROUP).setValue(id))
                                       .toList());
        }
        groupResource.setMembers(members);
        return groupResource;
    }

    protected void withGroupModel(GroupResource groupResource, Function<GroupResource, DocumentModel> mapper,
            Consumer<DocumentModel> consumer) {
        withGroupModel(() -> mapper.apply(groupResource), consumer);
    }

    protected void withGroupModel(String groupName, String groupLabel, List<String> members, List<String> subGroups,
            Runnable runnable) {
        withGroupModel(groupName, groupLabel, members, subGroups, unused -> runnable.run());
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

    protected void checkGroupModel(DocumentModel groupModel, String groupName, String groupLabel, List<String> members,
            List<String> subGroups) {
        assertEquals(groupName, groupModel.getPropertyValue("group:groupname"));
        assertEquals(groupLabel, groupModel.getPropertyValue("group:grouplabel"));
        assertEquals(members, groupModel.getPropertyValue("group:members"));
        assertEquals(subGroups, groupModel.getPropertyValue("group:subGroups"));
    }

    protected void checkGroupResource(GroupResource groupResource, String groupName, String groupLabel,
            List<String> members, List<String> subGroups) {
        assertEquals(groupName, groupResource.getId());
        assertEquals(groupName, groupResource.getExternalId());
        var meta = groupResource.getMeta();
        assertEquals(String.format("%s/%s", BASE_URL, groupName), meta.getLocation().toString());
        assertEquals("1", meta.getVersion());
        assertEquals(groupLabel, groupResource.getDisplayName());
        List<Member> actualMembers = groupResource.getMembers();
        assertNotNull(actualMembers);
        List<String> actualUserMembers = actualMembers.stream()
                                                      .filter(member -> SCIM_V2_RESOURCE_TYPE_USER.equals(
                                                              member.getType()))
                                                      .map(Member::getValue)
                                                      .toList();
        List<String> actualGroupMembers = actualMembers.stream()
                                                       .filter(member -> SCIM_V2_RESOURCE_TYPE_GROUP.equals(
                                                               member.getType()))
                                                       .map(Member::getValue)
                                                       .toList();
        assertEquals(members, actualUserMembers);
        assertEquals(subGroups, actualGroupMembers);
    }

}
