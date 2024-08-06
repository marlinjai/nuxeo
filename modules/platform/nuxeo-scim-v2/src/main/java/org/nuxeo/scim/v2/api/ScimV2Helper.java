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
package org.nuxeo.scim.v2.api;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.core.query.sql.model.QueryBuilder;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;

/**
 * @since 2023.17
 */
public class ScimV2Helper {

    private ScimV2Helper() {
        // helper class
    }

    public static DocumentModel getGroupModel(String uid, boolean fetchMembers) throws ResourceNotFoundException {
        DocumentModel groupModel = null;
        UserManager um = Framework.getService(UserManager.class);
        if (fetchMembers) {
            groupModel = um.getGroupModel(uid);
        } else {
            // searchGroups lazy fetches attributes such as members and subGroups
            var groups = um.searchGroups(new QueryBuilder().predicate(Predicates.like("groupname", uid)).limit(1));
            if (!groups.isEmpty()) {
                groupModel = groups.get(0);
            }
        }
        if (groupModel == null) {
            throw new ResourceNotFoundException("Cannot find group: " + uid);
        }
        return groupModel;
    }

    public static DocumentModel getUserModel(String uid) throws ResourceNotFoundException {
        UserManager um = Framework.getService(UserManager.class);
        DocumentModel userModel = um.getUserModel(uid);
        if (userModel == null) {
            throw new ResourceNotFoundException("Cannot find user: " + uid);
        }
        return userModel;
    }
}
