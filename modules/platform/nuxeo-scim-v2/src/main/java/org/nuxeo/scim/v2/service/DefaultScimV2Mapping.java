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
import java.util.Objects;
import java.util.function.Predicate;

import org.apache.commons.lang3.BooleanUtils;
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
    public DocumentModel afterCreateGroup(DocumentModel groupModel, GroupResource groupResource) {
        return groupModel;
    }

    @Override
    public DocumentModel afterCreateUser(DocumentModel userModel, UserResource userResource) {
        return userModel;
    }

    @Override
    public DocumentModel afterUpdateGroup(DocumentModel groupModel, GroupResource groupResource) {
        return groupModel;
    }

    @Override
    public DocumentModel afterUpdateUser(DocumentModel userModel, UserResource userResource) {
        return userModel;
    }

    @Override
    public DocumentModel beforeCreateGroup(DocumentModel groupModel, GroupResource groupResource) throws ScimException {
        return updateNuxeoGroupFromGroupResource(groupModel, groupResource);
    }

    @Override
    public DocumentModel beforeCreateUser(DocumentModel userModel, UserResource userResource) {
        return updateNuxeoUserFromUserResource(userModel, userResource);
    }

    @Override
    public DocumentModel beforeUpdateGroup(DocumentModel groupModel, GroupResource groupResource) throws ScimException {
        return updateNuxeoGroupFromGroupResource(groupModel, groupResource);
    }

    @Override
    public DocumentModel beforeUpdateUser(DocumentModel userModel, UserResource userResource) {
        return updateNuxeoUserFromUserResource(userModel, userResource);
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
            if (baseURL != null) {
                URI location = new URI(String.join("/", baseURL, groupId));
                meta.setLocation(location);
            }
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
            UserResource userResource = newUserResource(userId, baseURL);
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
    public String getGroupMemberAttributeName(String scimAttribute, Object filterValue) {
        return switch (scimAttribute) {
            case "value" -> Framework.getService(UserManager.class).getGroupIdField();
            default -> scimAttribute;
        };
    }

    @Override
    public String getUserAttributeName(String column, Object filterValue) {
        return switch (column.toLowerCase()) {
            case "id", "username" -> Framework.getService(UserManager.class).getUserIdField();
            case "emails", "emails.value" -> Framework.getService(UserManager.class).getUserEmailField();
            case "givenname" -> FIRST_NAME;
            case "familyname" -> LAST_NAME;
            default -> column;
        };
    }

    @Override
    public String getUserMemberAttributeName(String scimAttribute, Object filterValue) {
        return switch (scimAttribute) {
            case "value" -> Framework.getService(UserManager.class).getUserIdField();
            default -> scimAttribute;
        };
    }

    @Override
    public DocumentModel patchGroup(DocumentModel groupModel, GroupResource groupResource) throws ScimException {
        UserManager um = Framework.getService(UserManager.class);
        String groupSchemaName = um.getGroupSchemaName();
        groupModel.setProperty(groupSchemaName, um.getGroupLabelField(), groupResource.getDisplayName());
        return groupModel;
    }

    protected DocumentModel updateNuxeoGroupFromGroupResource(DocumentModel groupModel, GroupResource groupResource) {
        UserManager um = Framework.getService(UserManager.class);
        String groupSchemaName = um.getGroupSchemaName();

        groupModel.setProperty(groupSchemaName, um.getGroupLabelField(), groupResource.getDisplayName());

        List<Member> members = groupResource.getMembers();
        if (members == null) {
            // don't nullify if not provided, need an explicit empty list for this
            return groupModel;
        }
        List<String> groupMembers = new ArrayList<>();
        List<String> groupSubGroups = new ArrayList<>();
        members.stream().filter(m -> m.getValue() != null).forEach(member -> {
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

    protected DocumentModel updateNuxeoUserFromUserResource(DocumentModel userModel, UserResource userResource) {
        UserManager um = Framework.getService(UserManager.class);
        String userSchemaName = um.getUserSchemaName();

        Name name = userResource.getName();
        setName(name, userModel, userSchemaName);

        List<Email> emails = userResource.getEmails();
        setEmail(emails, userModel, userSchemaName);
        return userModel;
    }

    protected UserResource newUserResource(String userId, String baseURL) throws URISyntaxException {
        UserResource userResource = new UserResource();
        userResource.setId(userId);
        userResource.setExternalId(userId);

        Meta meta = new Meta();
        meta.setResourceType(SCIM_V2_RESOURCE_TYPE_USER.toString());
        if (baseURL != null) {
            URI location = new URI(String.join("/", baseURL, userId));
            meta.setLocation(location);
        }
        meta.setVersion("1");
        userResource.setMeta(meta);

        userResource.setUserName(userId);

        return userResource;
    }

    protected void setEmail(List<Email> emails, DocumentModel userModel, String userSchemaName) {
        if (emails == null || emails.isEmpty()) {
            userModel.setProperty(userSchemaName, EMAIL, null);
            return;
        }
        Predicate<Email> primaryEmail = Objects::nonNull;
        primaryEmail = primaryEmail.and(email -> BooleanUtils.isTrue(email.getPrimary()));
        Email email = emails.stream().filter(primaryEmail).findFirst().orElse(emails.get(0));
        if (email == null) {
            userModel.setProperty(userSchemaName, EMAIL, null);
        } else {
            userModel.setProperty(userSchemaName, EMAIL, email.getValue());
        }
    }

    protected void setName(Name name, DocumentModel userModel, String userSchemaName) {
        if (name == null) {
            userModel.setProperty(userSchemaName, FIRST_NAME, null);
            userModel.setProperty(userSchemaName, LAST_NAME, null);
            return;
        }
        var givenName = name.getGivenName();
        var firstName = givenName == null ? null : trimToNull(givenName);
        userModel.setProperty(userSchemaName, FIRST_NAME, firstName);
        var familyName = name.getFamilyName();
        var lastName = familyName == null ? null : trimToNull(familyName);
        userModel.setProperty(userSchemaName, LAST_NAME, lastName);
    }
}
