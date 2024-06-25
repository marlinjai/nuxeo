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
package org.nuxeo.scim.v2.service;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.nuxeo.scim.v2.api.ScimV2ResourceType.SCIM_V2_RESOURCE_TYPE_GROUP;
import static org.nuxeo.scim.v2.api.ScimV2ResourceType.SCIM_V2_RESOURCE_TYPE_USER;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.scim.v2.api.ScimV2Mapping;

import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.exceptions.ServerErrorException;
import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.Group;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Member;
import com.unboundid.scim2.common.types.Meta;
import com.unboundid.scim2.common.types.Name;
import com.unboundid.scim2.common.types.UserResource;

/**
 * @since 2023.14
 */
public class DefaultScimV2Mapping implements ScimV2Mapping {

    public static final String EMAIL = "email";

    public static final String FIRST_NAME = "firstName";

    public static final String LAST_NAME = "lastName";

    @Override
    public DocumentModel afterCreateGroup(DocumentModel groupModel, GroupResource groupResouce) {
        return groupModel;
    }

    @Override
    public DocumentModel afterCreateUser(DocumentModel userModel, UserResource userResouce) {
        return userModel;
    }

    @Override
    public DocumentModel afterUpdateGroup(DocumentModel groupModel, GroupResource groupResouce) {
        return groupModel;
    }

    @Override
    public DocumentModel afterUpdateUser(DocumentModel userModel, UserResource userResouce) {
        return userModel;
    }

    @Override
    public DocumentModel beforeCreateGroup(DocumentModel groupModel, GroupResource groupResouce) throws ScimException {
        return beforeCreateOrUpdateGroup(groupModel, groupResouce);
    }

    @Override
    public DocumentModel beforeCreateUser(DocumentModel userModel, UserResource userResouce) {
        return beforeCreateOrUpdateUser(userModel, userResouce);
    }

    @Override
    public DocumentModel beforeUpdateGroup(DocumentModel groupModel, GroupResource groupResouce) throws ScimException {
        return beforeCreateOrUpdateGroup(groupModel, groupResouce);
    }

    @Override
    public DocumentModel beforeUpdateUser(DocumentModel userModel, UserResource userResouce) {
        return beforeCreateOrUpdateUser(userModel, userResouce);
    }

    @Override
    public GroupResource getGroupResourceFromNuxeoGroup(DocumentModel groupModel, String baseURL) throws ScimException {
        UserManager um = Framework.getService(UserManager.class);
        String groupSchemaName = um.getGroupSchemaName();
        String groupId = (String) groupModel.getProperty(groupSchemaName, um.getGroupIdField());

        GroupResource groupResource = new GroupResource();
        groupResource.setId(groupId);
        groupResource.setExternalId(groupId);

        try {
            Meta meta = new Meta();
            meta.setResourceType(SCIM_V2_RESOURCE_TYPE_GROUP.toString());
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
            List<String> groupMembers = (List<String>) groupModel.getProperty(groupSchemaName,
                    um.getGroupMembersField());
            if (groupMembers != null) {
                members.addAll(
                        groupMembers.stream()
                                    .map(groupMember -> new Member().setType(SCIM_V2_RESOURCE_TYPE_USER.toString())
                                                                    .setValue(groupMember))
                                    .toList());
            }
            @SuppressWarnings("unchecked")
            List<String> groupSubGroups = (List<String>) groupModel.getProperty(groupSchemaName,
                    um.getGroupSubGroupsField());
            if (groupSubGroups != null) {
                members.addAll(
                        groupSubGroups.stream()
                                      .map(groupSubGroup -> new Member().setType(SCIM_V2_RESOURCE_TYPE_GROUP.toString())
                                                                        .setValue(groupSubGroup))
                                      .toList());
            }
            groupResource.setMembers(members);

            return groupResource;
        } catch (URISyntaxException e) {
            throw new ServerErrorException("Cannot create group: " + groupId, null, e);
        }
    }

    @Override
    public UserResource getUserResourceFromNuxeoUser(DocumentModel userModel, String baseURL) throws ScimException {
        UserManager um = Framework.getService(UserManager.class);
        String userSchemaName = um.getUserSchemaName();
        String userId = (String) userModel.getProperty(userSchemaName, um.getUserIdField());
        try {
            UserResource userResource = getUserResourceFromUserModel(userId, baseURL);
            String firstName = (String) userModel.getProperty(userSchemaName, FIRST_NAME);
            String lastName = (String) userModel.getProperty(userSchemaName, LAST_NAME);
            if (isNotBlank(firstName) || isNotBlank(lastName)) {
                Name name = new Name();
                String displayName = "";
                if (isNotBlank(firstName)) {
                    firstName = firstName.trim();
                    name.setGivenName(firstName);
                    displayName = firstName + " ";
                }
                if (isNotBlank(lastName)) {
                    lastName = lastName.trim();
                    name.setFamilyName(lastName);
                    displayName += lastName.trim();
                }
                displayName = displayName.trim();
                name.setFormatted(displayName);
                userResource.setName(name);
                userResource.setDisplayName(displayName);
            }

            String email = (String) userModel.getProperty(userSchemaName, EMAIL);
            if (isNotBlank(email)) {
                List<Email> emails = List.of(new Email().setValue(email));
                userResource.setEmails(emails);
            }

            List<Group> groups = new ArrayList<>();
            List<String> groupIds = um.getPrincipal(userId).getAllGroups();
            if (groupIds != null) {
                groups.addAll(groupIds.stream().map(groupId -> new Group().setValue(groupId)).toList());
            }
            userResource.setGroups(groups);

            userResource.setActive(true);

            return userResource;
        } catch (URISyntaxException e) {
            throw new ServerErrorException("Cannot create user: " + userId, null, e);
        }

    }

    @Override
    public String getGroupAttributeName(String column, Object filterValue) {
        return switch (column.toLowerCase()) {
            case "id" -> Framework.getService(UserManager.class).getGroupIdField();
            case "displayname" -> Framework.getService(UserManager.class).getGroupLabelField();
            default -> column;
        };
    }

    @Override
    public String getUserAttributeName(String column, Object filterValue) {
        return switch (column.toLowerCase()) {
            case "id", "username" -> Framework.getService(UserManager.class).getUserIdField();
            case "emails", "emails.value" -> Framework.getService(UserManager.class).getUserEmailField();
            case "givenname" -> "firstName";
            case "familyname" -> "lastName";
            default -> column;
        };
    }

    protected DocumentModel beforeCreateOrUpdateGroup(DocumentModel groupModel, GroupResource groupResouce)
            throws ScimException {
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
            return groupModel;
        }
        List<String> groupMembers = new ArrayList<>();
        List<String> groupSubGroups = new ArrayList<>();
        members.stream().forEach(member -> {
            String value = member.getValue();
            if (SCIM_V2_RESOURCE_TYPE_GROUP.equalsString(member.getType())) {
                groupSubGroups.add(value);
            } else {
                groupMembers.add(value);
            }
        });
        groupModel.setProperty(groupSchemaName, um.getGroupMembersField(), groupMembers);
        groupModel.setProperty(groupSchemaName, um.getGroupSubGroupsField(), groupSubGroups);
        return groupModel;
    }

    protected DocumentModel beforeCreateOrUpdateUser(DocumentModel userModel, UserResource userResouce) {
        UserManager um = Framework.getService(UserManager.class);
        String userSchemaName = um.getUserSchemaName();

        Name name = userResouce.getName();
        setName(name, userModel, userSchemaName);

        List<Email> emails = userResouce.getEmails();
        setEmail(emails, userModel, userSchemaName);
        return userModel;
    }

    protected UserResource getUserResourceFromUserModel(String userId, String baseURL) throws URISyntaxException {
        UserResource userResource = new UserResource();
        userResource.setId(userId);
        userResource.setExternalId(userId);

        Meta meta = new Meta();
        meta.setResourceType(SCIM_V2_RESOURCE_TYPE_USER.toString());
        URI location = new URI(String.join("/", baseURL, userId));
        meta.setLocation(location);
        meta.setVersion("1");
        userResource.setMeta(meta);

        userResource.setUserName(userId);

        return userResource;
    }

    protected void setEmail(List<Email> emails, DocumentModel userModel, String userSchemaName) {
        // don't nullify if not provided, need an explicit empty list for this
        if (emails == null) {
            return;
        }
        if (emails.isEmpty()) {
            userModel.setProperty(userSchemaName, EMAIL, null);
            return;
        }
        Email email = emails.get(0);
        if (email == null) {
            userModel.setProperty(userSchemaName, EMAIL, null);
        } else {
            userModel.setProperty(userSchemaName, EMAIL, email.getValue());
        }
    }

    protected void setName(Name name, DocumentModel userModel, String userSchemaName) {
        // don't nullify if not provided, need an explicit empty string for this
        if (name == null) {
            return;
        }
        var givenName = name.getGivenName();
        if (givenName != null) {
            var firstName = trimToNull(givenName);
            userModel.setProperty(userSchemaName, FIRST_NAME, firstName);
        }
        var familyName = name.getFamilyName();
        if (familyName != null) {
            var lastName = trimToNull(familyName);
            userModel.setProperty(userSchemaName, LAST_NAME, lastName);
        }
    }
}
