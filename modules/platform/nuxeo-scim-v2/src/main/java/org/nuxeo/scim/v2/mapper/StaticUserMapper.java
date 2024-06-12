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
import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.usermapper.service.UserMapperService;

import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.Group;
import com.unboundid.scim2.common.types.Name;
import com.unboundid.scim2.common.types.UserResource;

/**
 * Static / hard-coded SCIM 2.0 mapper implementation, in case the {@link UserMapperService} is not available.
 *
 * @since 2023.14
 */
public class StaticUserMapper extends AbstractMapper {

    public static final String FIRST_NAME = "firstName";

    public static final String LAST_NAME = "lastName";

    public static final String EMAIL = "email";

    @Override
    public DocumentModel createNuxeoUserFromUserResource(UserResource user) {
        UserManager um = Framework.getService(UserManager.class);
        String userId = user.getUserName();
        DocumentModel userModel = um.getUserModel(userId);
        if (userModel == null) {
            // create new user
            DocumentModel newUser = um.getBareUserModel();
            newUser.setProperty(um.getUserSchemaName(), um.getUserIdField(), userId);
            updateUserModel(newUser, user);
            return um.createUser(newUser);
        }
        // update existing user
        updateUserModel(userModel, user);
        um.updateUser(userModel);
        return userModel;
    }

    @Override
    public DocumentModel updateNuxeoUserFromUserResource(String uid, UserResource user) {
        UserManager um = Framework.getService(UserManager.class);
        DocumentModel userModel = um.getUserModel(uid);
        if (userModel == null) {
            return null;
        }
        updateUserModel(userModel, user);
        um.updateUser(userModel);
        return userModel;
    }

    @Override
    public UserResource getUserResourceFromNuxeoUser(DocumentModel userModel, String baseURL)
            throws URISyntaxException {
        UserManager um = Framework.getService(UserManager.class);
        String userSchemaName = um.getUserSchemaName();
        String userId = (String) userModel.getProperty(userSchemaName, um.getUserIdField());

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
    }

    protected void updateUserModel(DocumentModel userModel, UserResource userResouce) {
        UserManager um = Framework.getService(UserManager.class);
        String userSchemaName = um.getUserSchemaName();

        Name name = userResouce.getName();
        setName(name, userModel, userSchemaName);

        List<Email> emails = userResouce.getEmails();
        setEmail(emails, userModel, userSchemaName);
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

}
