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
package org.nuxeo.scim.v2.rest.usermanager;

import static com.unboundid.scim2.common.exceptions.BadRequestException.INVALID_SYNTAX;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.net.URISyntaxException;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.scim.v2.rest.marshalling.ResponseUtils;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.exceptions.ServerErrorException;
import com.unboundid.scim2.common.types.UserResource;

/**
 * SCIM 2.0 User object.
 *
 * @since 2023.14
 */
@WebObject(type = "users")
@Produces(APPLICATION_JSON)
public class ScimV2UserObject extends ScimV2BaseUMObject {

    @POST
    public Response createUser(UserResource user) throws ScimException {
        checkUpdateGuardPreconditions();
        return doCreateUser(user);
    }

    @GET
    @Path("{uid}")
    public UserResource getUserResource(@PathParam("uid") String uid) throws ScimException {
        return resolveUserRessource(uid);
    }

    @PUT
    @Path("{uid}")
    public UserResource updateUser(@PathParam("uid") String uid, UserResource user) throws ScimException {
        checkUpdateGuardPreconditions();
        return doUpdateUser(uid, user);
    }

    @DELETE
    @Path("{uid}")
    public Response deleteUserResource(@PathParam("uid") String uid) throws ScimException {
        checkUpdateGuardPreconditions();
        return doDeleteUser(uid);
    }

    @Override
    protected String getPrefix() {
        return "/Users";
    }

    protected Response doCreateUser(UserResource user) throws ScimException {
        if (user == null) {
            throw new BadRequestException("Cannot create user without a user resource as request body", INVALID_SYNTAX);
        }
        var userName = user.getUserName();
        if (isBlank(userName)) {
            throw new BadRequestException("Cannot create user without a username", INVALID_SYNTAX);
        }
        UserManager um = Framework.getService(UserManager.class);
        boolean create = um.getUserModel(userName) == null;
        DocumentModel newUser = mapper.createNuxeoUserFromUserResource(user);
        if (newUser == null) {
            throw new ServerErrorException("Cannot create user from resource: " + user);
        }
        try {
            UserResource userResource = mapper.getUserResourceFromNuxeoUser(newUser, baseURL);
            return ResponseUtils.response(create ? CREATED : OK, userResource);
        } catch (URISyntaxException e) {
            throw new ServerErrorException("Cannot create user: " + userName, null, e);
        }
    }

    protected UserResource resolveUserRessource(String uid) throws ScimException {
        DocumentModel userModel = um.getUserModel(uid);
        if (userModel == null) {
            throw new ResourceNotFoundException("Cannot find user: " + uid); // NOSONAR
        }
        try {
            return mapper.getUserResourceFromNuxeoUser(userModel, baseURL);
        } catch (URISyntaxException e) {
            throw new ServerErrorException("Cannot find user: " + uid, null, e);
        }
    }

    protected UserResource doUpdateUser(String uid, UserResource user) throws ScimException {
        if (user == null) {
            throw new BadRequestException("Cannot update user without a user resource as request body", INVALID_SYNTAX);
        }
        DocumentModel userModel = mapper.updateNuxeoUserFromUserResource(uid, user);
        if (userModel == null) {
            throw new ResourceNotFoundException("Cannot find user: " + uid);
        }
        try {
            return mapper.getUserResourceFromNuxeoUser(userModel, baseURL);
        } catch (URISyntaxException e) {
            throw new ServerErrorException("Cannot update user: " + uid, null, e);
        }
    }

    protected Response doDeleteUser(String uid) throws ScimException {
        try {
            um.deleteUser(uid);
            return ResponseUtils.deleted();
        } catch (DirectoryException e) {
            throw new ResourceNotFoundException("Cannot find user: " + uid);
        }
    }

}
