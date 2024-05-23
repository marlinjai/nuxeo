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
 *     Thierry Delprat
 *     Antoine Taillefer
 */
package org.nuxeo.scim.v2.mapper;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Member;
import com.unboundid.scim2.common.types.Meta;
import com.unboundid.scim2.common.types.UserResource;

/**
 * Base class for mapping {@link ScimResource} to Nuxeo {@link DocumentModel} and vice versa.
 *
 * @since 2023.13
 */
public abstract class AbstractMapper {

    public static final String SCIM_RESOURCE_TYPE_USER = "User";

    public static final String SCIM_RESOURCE_TYPE_GROUP = "Group";

    public abstract DocumentModel createNuxeoUserFromUserResource(UserResource user);

    public abstract DocumentModel updateNuxeoUserFromUserResource(String uid, UserResource user);

    public abstract UserResource getUserResourceFromNuxeoUser(DocumentModel userModel, String baseURL)
            throws URISyntaxException;

    public DocumentModel createNuxeoGroupFromGroupResource(GroupResource group) {
        UserManager um = Framework.getService(UserManager.class);
        String groupId = group.getDisplayName();
        DocumentModel groupModel = um.getGroupModel(groupId);
        if (groupModel == null) {
            // create new group
            DocumentModel newGroup = um.getBareGroupModel();
            newGroup.setProperty(um.getGroupSchemaName(), um.getGroupIdField(), groupId);
            updateGroupModel(newGroup, group);
            return um.createGroup(newGroup);
        }
        // update existing group
        updateGroupModel(groupModel, group);
        um.updateGroup(groupModel);
        return groupModel;
    }

    public DocumentModel updateNuxeoGroupFromGroupResource(String uid, GroupResource group) {
        UserManager um = Framework.getService(UserManager.class);
        DocumentModel groupModel = um.getGroupModel(uid);
        if (groupModel == null) {
            return null;
        }
        updateGroupModel(groupModel, group);
        um.updateGroup(groupModel);
        return groupModel;
    }

    public GroupResource getGroupResourceFromNuxeoGroup(DocumentModel groupModel, String baseURL)
            throws URISyntaxException {
        UserManager um = Framework.getService(UserManager.class);
        String groupSchemaName = um.getGroupSchemaName();
        String groupId = (String) groupModel.getProperty(groupSchemaName, um.getGroupIdField());

        GroupResource groupResource = new GroupResource();
        groupResource.setId(groupId);
        groupResource.setExternalId(groupId);

        Meta meta = new Meta();
        URI location = new URI(String.join("/", baseURL, groupId));
        meta.setLocation(location);
        meta.setVersion("1");
        groupResource.setMeta(meta);

        String groupLabel = (String) groupModel.getProperty(groupSchemaName, um.getGroupLabelField());
        if (isNotBlank(groupLabel)) {
            groupResource.setDisplayName(groupLabel);
        }

        List<Member> members = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<String> groupMembers = (List<String>) groupModel.getProperty(groupSchemaName, um.getGroupMembersField());
        if (groupMembers != null) {
            members.addAll(
                    groupMembers.stream()
                                .map(groupMember -> new Member().setType(SCIM_RESOURCE_TYPE_USER).setValue(groupMember))
                                .toList());
        }
        @SuppressWarnings("unchecked")
        List<String> groupSubGroups = (List<String>) groupModel.getProperty(groupSchemaName,
                um.getGroupSubGroupsField());
        if (groupSubGroups != null) {
            members.addAll(groupSubGroups.stream()
                                         .map(groupSubGroup -> new Member().setType(SCIM_RESOURCE_TYPE_GROUP)
                                                                           .setValue(groupSubGroup))
                                         .toList());
        }
        groupResource.setMembers(members);

        return groupResource;
    }

    protected UserResource getUserResourceFromUserModel(String userId, String baseURL) throws URISyntaxException {
        UserResource userResource = new UserResource();
        userResource.setId(userId);
        userResource.setExternalId(userId);

        Meta meta = new Meta();
        URI location = new URI(String.join("/", baseURL, userId));
        meta.setLocation(location);
        meta.setVersion("1");
        userResource.setMeta(meta);

        userResource.setUserName(userId);

        return userResource;
    }

    protected void updateGroupModel(DocumentModel groupModel, GroupResource groupResouce) {
        UserManager um = Framework.getService(UserManager.class);
        String groupSchemaName = um.getGroupSchemaName();

        String displayName = groupResouce.getDisplayName();
        // don't nullify if not provided, need an explicit empty string for this
        if (displayName != null) {
            groupModel.setProperty(groupSchemaName, um.getGroupLabelField(), groupResouce.getDisplayName());
        }

        List<Member> members = groupResouce.getMembers();
        if (members == null) {
            // don't nullify if not provided, need an explicit empty list for this
            return;
        }
        List<String> groupMembers = new ArrayList<>();
        List<String> groupSubGroups = new ArrayList<>();
        members.stream().forEach(member -> {
            String value = member.getValue();
            if (SCIM_RESOURCE_TYPE_USER.equals(member.getType())) {
                groupMembers.add(value);
            } else {
                groupSubGroups.add(value);
            }
        });
        groupModel.setProperty(groupSchemaName, um.getGroupMembersField(), groupMembers);
        groupModel.setProperty(groupSchemaName, um.getGroupSubGroupsField(), groupSubGroups);
    }

}
