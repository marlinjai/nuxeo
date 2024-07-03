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

import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.UserResource;

/**
 * Interface for mapping {@link ScimResource} to Nuxeo {@link DocumentModel} and vice versa.
 *
 * @since 2023.14
 */
public interface ScimV2Mapping {

    /**
     * Hook to be executed after a group is created.
     *
     * @param groupModel the created group model
     * @param groupResource the group resource from which the group model is created
     * @return the created group model
     * @throws ScimException if an error occurred
     */
    DocumentModel afterCreateGroup(DocumentModel groupModel, GroupResource groupResource) throws ScimException;

    /**
     * Hook to be executed after a user is created.
     *
     * @param userModel the created user model
     * @param userResource the user resource from which the user model is created
     * @return the created user model
     * @throws ScimException if an error occurred
     */
    DocumentModel afterCreateUser(DocumentModel userModel, UserResource userResource) throws ScimException;

    /**
     * Hook to be executed after a group is updated.
     *
     * @param groupModel the updated group model
     * @param groupResource the group resource from which the group model is updated
     * @return the updated group model
     * @throws ScimException if an error occurred
     */
    DocumentModel afterUpdateGroup(DocumentModel groupModel, GroupResource groupResource) throws ScimException;

    /**
     * Hook to be executed after a user is updated.
     *
     * @param userModel the created user model
     * @param userResource the user resource from which the user model is updated
     * @return the updated user model
     * @throws ScimException if an error occurred
     */
    DocumentModel afterUpdateUser(DocumentModel userModel, UserResource userResource) throws ScimException;

    /**
     * Hook to be executed before a group is created.
     *
     * @param groupModel the group model about to be created
     * @param groupResource the group resource from which the group model is created
     * @return the created group model
     * @throws ScimException if an error occurred
     */
    DocumentModel beforeCreateGroup(DocumentModel groupModel, GroupResource groupResource) throws ScimException;

    /**
     * Hook to be executed before a user is created.
     *
     * @param userModel the user model about to be created
     * @param userResource the user resource from which the user model is created
     * @return the created user model
     * @throws ScimException if an error occurred
     */
    DocumentModel beforeCreateUser(DocumentModel userModel, UserResource userResource) throws ScimException;

    /**
     * Hook to be executed before a group is updated.
     *
     * @param groupModel the group model about to be updated
     * @param groupResource the group resource from which the group model is updated
     * @return the updated group model
     * @throws ScimException if an error occurred
     */
    DocumentModel beforeUpdateGroup(DocumentModel groupModel, GroupResource groupResource) throws ScimException;

    /**
     * Hook to be executed before a user is updated.
     *
     * @param userModel the user model about to be updated
     * @param userResource the user resource from which the user model is updated
     * @return the updated user model
     * @throws ScimException if an error occurred
     */
    DocumentModel beforeUpdateUser(DocumentModel userModel, UserResource userResource) throws ScimException;

    /**
     * Gets the Nuxeo group model attribute name matching the SCIM attribute name used in SCIM Search filtering and
     * sorting.
     *
     * @param scimAttribute the SCIM attribute name
     * @param filterValue the value of a filter expression to be converted if any
     * @return the matching group model attribute
     */
    String getGroupAttributeName(String scimAttribute, Object filterValue);

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
     * Gets the Nuxeo user model attribute name matching the SCIM attribute name used in SCIM Search filtering and
     * sorting.
     *
     * @param scimAttribute the SCIM attribute name
     * @param filterValue the value of a filter expression to be converted if any
     * @return the matching group model attribute
     */
    String getUserAttributeName(String scimAttribute, Object filterValue);

    /**
     * Gets a user resource representation of a user model.
     *
     * @param userModel the user model
     * @param baseURL the location base URL of the SCIM user object
     * @return the user resource
     * @throws ScimException if an error occurred
     */
    UserResource getUserResourceFromNuxeoUser(DocumentModel userModel, String baseURL) throws ScimException;

}
