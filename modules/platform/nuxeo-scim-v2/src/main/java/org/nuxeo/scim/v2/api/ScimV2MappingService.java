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
package org.nuxeo.scim.v2.api;

import org.nuxeo.ecm.core.api.DocumentModel;

import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.UserResource;

/**
 * @since 2023.14
 */
public interface ScimV2MappingService {

    /**
     * Creates a Nuxeo group model according to the group resource.
     *
     * @param group the group resource
     * @return the created group
     * @throws ScimException if an error occurred
     */
    DocumentModel createNuxeoGroupFromGroupResource(GroupResource group) throws ScimException;

    /**
     * Creates a Nuxeo user model according to the user resource.
     *
     * @param user the user resource
     * @return the created user
     * @throws ScimException if an error occurred
     */
    DocumentModel createNuxeoUserFromUserResource(UserResource user) throws ScimException;

    /**
     * Gets a group resource representation of a group model.
     *
     * @param groupModel the group model
     * @param baseURL the location base URL of the SCIM group object
     * @return the group resource
     * @throws ScimException if an error occurred
     */
    GroupResource getGroupResourceFromNuxeoGroup(DocumentModel groupModel, String baseURL) throws ScimException;

    /**
     * Gets the contributed SCIM mapping.
     *
     * @return the contributed scim mapping
     */
    ScimV2Mapping getMapping();

    /**
     * Gets a user resource representation of a user model.
     *
     * @param userModel the user model
     * @param baseURL the location base URL of the SCIM user object
     * @return the user resource
     * @throws ScimException if an error occurred
     */
    UserResource getUserResourceFromNuxeoUser(DocumentModel userModel, String baseURL) throws ScimException;

    /**
     * Patches a Nuxeo user model according to the patch request.
     *
     * @param uid the user uid
     * @param patch the patch request
     * @return the patched user
     * @throws ScimException if an error occurred
     */
    DocumentModel patchNuxeoUser(String uid, PatchRequest patch) throws ScimException;

    /**
     * Searches for groups.
     *
     * @param queryCtx the query context
     * @return the query result
     * @throws ScimException if an error occurred
     */
    ListResponse<ScimResource> queryGroups(ScimV2QueryContext queryCtx) throws ScimException;

    /**
     * Searches for users.
     *
     * @param queryCtx the query context
     * @return the query result
     * @throws ScimException if an error occurred
     */
    ListResponse<ScimResource> queryUsers(ScimV2QueryContext queryCtx) throws ScimException;

    /**
     * Updates a Nuxeo group model according to the group resource.
     *
     * @param uid the group uid
     * @param group the group resource
     * @return the updated group
     * @throws ScimException if an error occurred
     */
    DocumentModel updateNuxeoGroupFromGroupResource(String uid, GroupResource group) throws ScimException;

    /**
     * Updates a Nuxeo user model according to the user resource.
     *
     * @param uid the user uid
     * @param user the user resource
     * @return the updated user
     * @throws ScimException if an error occurred
     */
    DocumentModel updateNuxeoUserFromUserResource(String uid, UserResource user) throws ScimException;
}
