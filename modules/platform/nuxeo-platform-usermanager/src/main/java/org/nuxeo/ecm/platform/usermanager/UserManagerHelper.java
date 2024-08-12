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
package org.nuxeo.ecm.platform.usermanager;

import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.directory.BaseSession;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 2023.17
 */
public class UserManagerHelper {

    private UserManagerHelper() {
        // helper class
    }

    public static void addUserToGroup(NuxeoPrincipal principal, NuxeoGroup group) {
        addUserToGroup(principal, group, false);
    }

    public static void addUserToGroup(NuxeoPrincipal principal, NuxeoGroup group, boolean loadGroupMembers) {
        UserManager userManager = Framework.getService(UserManager.class);
        String groupName = group.getName();
        if (!BaseSession.isReadOnlyEntry(principal.getModel())) {
            // we can write to the user directory
            List<String> groups = principal.getGroups();
            if (groups == null) {
                groups = new ArrayList<>();
            }
            if (!groups.contains(groupName)) {
                groups.add(groupName);
                principal.setGroups(groups);
                userManager.updateUser(principal.getModel());
            }
        } else {
            // user directory is read-only, update through the group instead
            if (loadGroupMembers) {
                // load group members in case they were not
                group = userManager.getGroup(groupName);
            }
            List<String> users = group.getMemberUsers();
            if (users == null) {
                users = new ArrayList<>();
            }
            String userName = principal.getName();
            if (!users.contains(userName)) {
                users.add(userName);
                group.setMemberUsers(users);
                userManager.updateGroup(group.getModel());
            }
        }
    }

    public static void addGroupToGroup(NuxeoGroup subgroup, String groupName) {
        List<String> parentGroups = subgroup.getParentGroups();
        if (parentGroups == null) {
            parentGroups = new ArrayList<>();
        }
        if (!parentGroups.contains(groupName)) {
            parentGroups.add(groupName);
            subgroup.setParentGroups(parentGroups);
            Framework.getService(UserManager.class).updateGroup(subgroup.getModel());
        }
    }

    public static void removeUserFromGroup(NuxeoPrincipal principal, NuxeoGroup group) {
        removeUserFromGroup(principal, group, false);
    }

    public static void removeUserFromGroup(NuxeoPrincipal principal, NuxeoGroup group, boolean loadGroupMembers) {
        UserManager userManager = Framework.getService(UserManager.class);
        String groupName = group.getName();
        if (!BaseSession.isReadOnlyEntry(principal.getModel())) {
            // we can write to the user directory
            List<String> groups = principal.getGroups();
            if (groups == null) {
                groups = new ArrayList<>();
            }
            if (groups.contains(groupName)) {
                groups.remove(groupName);
                principal.setGroups(groups);
                userManager.updateUser(principal.getModel());
            }
        } else {
            // user directory is read-only, update through the group instead
            if (loadGroupMembers) {
                // load group members in case they were not
                group = userManager.getGroup(groupName);
            }
            List<String> users = group.getMemberUsers();
            if (users == null) {
                users = new ArrayList<>();
            }
            String userName = principal.getName();
            if (users.contains(userName)) {
                users.remove(userName);
                group.setMemberUsers(users);
                userManager.updateGroup(group.getModel());
            }
        }
    }

    public static void removeGroupFromGroup(NuxeoGroup subgroup, String groupName) {
        List<String> parentGroups = subgroup.getParentGroups();
        if (parentGroups == null) {
            parentGroups = new ArrayList<>();
        }
        if (parentGroups.contains(groupName)) {
            parentGroups.remove(groupName);
            subgroup.setParentGroups(parentGroups);
            Framework.getService(UserManager.class).updateGroup(subgroup.getModel());
        }
    }
}
