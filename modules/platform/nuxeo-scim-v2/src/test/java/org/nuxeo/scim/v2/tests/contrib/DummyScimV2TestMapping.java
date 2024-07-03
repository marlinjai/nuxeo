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

import java.util.regex.Pattern;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.scim.v2.service.DefaultScimV2Mapping;

import com.unboundid.scim2.common.types.UserResource;

/**
 * @since 2023.14
 */
public class DummyScimV2TestMapping extends DefaultScimV2Mapping {

    // Email pattern
    protected static Pattern pattern = Pattern.compile("[^@]+@[^\\.]+\\..+");

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
