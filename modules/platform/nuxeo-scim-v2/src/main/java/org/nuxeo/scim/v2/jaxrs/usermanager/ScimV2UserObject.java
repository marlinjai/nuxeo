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
package org.nuxeo.scim.v2.jaxrs.usermanager;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.net.URISyntaxException;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.scim.v2.jaxrs.marshalling.ResponseUtils;

import com.unboundid.scim2.common.types.UserResource;

/**
 * SCIM 2.0 User object.
 *
 * @since 2023.13
 */
@WebObject(type = "users")
@Produces(APPLICATION_JSON)
public class ScimV2UserObject extends ScimV2BaseUMObject {

    private static final Logger log = LogManager.getLogger(ScimV2UserObject.class);

    @POST
    public Response createUser(UserResource user) {
        checkUpdateGuardPreconditions();
        return doCreateUser(user);
    }

    @GET
    @Path("{uid}")
    public UserResource getUserResource(@PathParam("uid") String uid) {
        return resolveUserRessource(uid);

    }

    @PUT
    @Path("{uid}")
    public Response updateUser(@PathParam("uid") String uid, UserResource user) {
        checkUpdateGuardPreconditions();
        return doUpdateUser(uid, user);
    }

    @DELETE
    @Path("{uid}")
    public Response deleteUserResource(@PathParam("uid") String uid) {
        try {
            um.deleteUser(uid);
            return ResponseUtils.deleted();
        } catch (DirectoryException e) {
            return ResponseUtils.notFound();
        }
    }

    @Override
    protected String getPrefix() {
        return "/Users";
    }

    protected Response doCreateUser(UserResource user) {
        try {
            DocumentModel newUser = mapper.createNuxeoUserFromUserResource(user);
            UserResource userResource = mapper.getUserResourceFromNuxeoUser(newUser);
            return ResponseUtils.created(userResource);
        } catch (URISyntaxException e) {
            log.error("Unable to create user: {}", user.getId(), e);
        }
        return null;
    }

    protected UserResource resolveUserRessource(String uid) {
        try {
            DocumentModel userModel = um.getUserModel(uid);
            if (userModel != null) {
                return mapper.getUserResourceFromNuxeoUser(userModel);
            }
        } catch (URISyntaxException e) {
            log.error("Error while resolving user: {}", uid, e);
        }
        return null;
    }

    protected Response doUpdateUser(String uid, UserResource user) {
        try {
            DocumentModel userModel = mapper.updateNuxeoUserFromUserResource(uid, user);
            if (userModel != null) {
                UserResource userResource = mapper.getUserResourceFromNuxeoUser(userModel);
                return ResponseUtils.updated(userResource);
            }
        } catch (URISyntaxException e) {
            log.error("Unable to update user: {}", uid, e);
        }
        return null;
    }

}
