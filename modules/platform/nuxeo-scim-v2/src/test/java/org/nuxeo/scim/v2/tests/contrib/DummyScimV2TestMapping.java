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
package org.nuxeo.scim.v2.tests.contrib;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.scim.v2.service.DefaultScimV2Mapping;

import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.UserResource;

/**
 * @since 2023.14
 */
public class DummyScimV2TestMapping extends DefaultScimV2Mapping {

    public static final String EMAIL_FIELD_HOME = "homeEmail";

    public static final String EMAIL_FIELD_WORK = "workEmail";

    public static final String EMAIL_TYPE_HOME = "home";

    public static final String EMAIL_TYPE_WORK = "work";

    // Email pattern
    protected static Pattern pattern = Pattern.compile("[^@]+@[^\\.]+\\..+");

    @Override
    protected DocumentModel updateNuxeoUserFromUserResource(DocumentModel userModel, UserResource userResource) {
        final DocumentModel customUserModel = super.updateNuxeoUserFromUserResource(userModel, userResource);

        UserManager um = Framework.getService(UserManager.class);
        String userSchemaName = um.getUserSchemaName();

        // first reset email fields to honor PUT, PATCH remove and PATCH replace for a complex multivalued attribute
        customUserModel.setProperty(userSchemaName, EMAIL_FIELD_HOME, null);
        customUserModel.setProperty(userSchemaName, EMAIL_FIELD_WORK, null);

        List<Email> emails = userResource.getEmails();
        if (emails == null || emails.isEmpty()) {
            return customUserModel;
        }
        emails.stream().filter(Objects::nonNull).filter(e -> e.getType() != null).forEach(email -> {
            String type = email.getType();
            String value = email.getValue();
            switch (type) {
                case EMAIL_TYPE_HOME -> customUserModel.setProperty(userSchemaName, EMAIL_FIELD_HOME, value);
                case EMAIL_TYPE_WORK -> customUserModel.setProperty(userSchemaName, EMAIL_FIELD_WORK, value);
                default -> {
                    // cannot map email
                }
            }
        });

        return customUserModel;
    }

    @Override
    public UserResource getUserResourceFromNuxeoUser(DocumentModel userModel, String baseURL) throws ScimException {
        UserResource userResource = super.getUserResourceFromNuxeoUser(userModel, baseURL);

        List<Email> emails = new ArrayList<>();
        UserManager um = Framework.getService(UserManager.class);
        String userSchemaName = um.getUserSchemaName();

        String homeEmail = (String) userModel.getProperty(userSchemaName, EMAIL_FIELD_HOME);
        if (isNotBlank(homeEmail)) {
            emails.add(new Email().setType(EMAIL_TYPE_HOME).setValue(homeEmail));
        }
        String workEmail = (String) userModel.getProperty(userSchemaName, EMAIL_FIELD_WORK);
        if (isNotBlank(workEmail)) {
            emails.add(new Email().setType(EMAIL_TYPE_WORK).setValue(workEmail));
        }
        if (!emails.isEmpty()) {
            userResource.setEmails(emails);
        }

        return userResource;
    }

    /**
     * Adds any created user to the "members" group.
     */
    @Override
    public DocumentModel afterCreateUser(DocumentModel userModel, UserResource userResource) {
        UserManager um = Framework.getService(UserManager.class);
        NuxeoPrincipal principal = um.getPrincipal(userModel.getId());
        var groups = principal.getGroups();
        if (!groups.contains("members")) {
            groups.add("members");
            principal.setGroups(groups);
            um.updateUser(principal.getModel());
        }
        return userModel;
    }

    /**
     * Searches on email field instead of user Id field if filter value looks like an email.
     */
    @Override
    public String getUserAttributeName(String column, Object value) {
        if ("userName".equals(column) && value instanceof String s && pattern.matcher(s).matches()) {
            return "email";
        }
        return super.getUserAttributeName(column, value);
    }
}
