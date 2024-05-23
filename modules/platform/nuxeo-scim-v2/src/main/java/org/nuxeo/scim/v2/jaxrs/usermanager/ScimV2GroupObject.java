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

import static com.unboundid.scim2.common.exceptions.BadRequestException.INVALID_SYNTAX;
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

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.scim.v2.jaxrs.marshalling.ResponseUtils;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.exceptions.ServerErrorException;
import com.unboundid.scim2.common.types.GroupResource;

/**
 * SCIM 2.0 Group object.
 *
 * @since 2023.13
 */
@WebObject(type = "groups")
@Produces(APPLICATION_JSON)
public class ScimV2GroupObject extends ScimV2BaseUMObject {

    @POST
    public Response createGroup(GroupResource group) throws ScimException {
        checkUpdateGuardPreconditions();
        return doCreateGroup(group);
    }

    @GET
    @Path("{uid}")
    public GroupResource getGroupResource(@PathParam("uid") String uid) throws ScimException {
        return resolveGroupRessource(uid);
    }

    @PUT
    @Path("{uid}")
    public GroupResource updateGroup(@PathParam("uid") String uid, GroupResource group) throws ScimException {
        checkUpdateGuardPreconditions();
        return doUpdateGroup(uid, group);
    }

    @DELETE
    @Path("{uid}")
    public Response deleteGroupResource(@PathParam("uid") String uid) throws ScimException {
        checkUpdateGuardPreconditions();
        return doDeleteGroup(uid);
    }

    @Override
    protected String getPrefix() {
        return "/Groups";
    }

    protected Response doCreateGroup(GroupResource group) throws ScimException {
        if (group == null) {
            throw new BadRequestException("Cannot create group without a group resource as request body",
                    INVALID_SYNTAX);
        }
        var groupName = group.getDisplayName();
        if (groupName == null) {
            throw new BadRequestException("Cannot create user without a displayName", INVALID_SYNTAX);
        }
        DocumentModel newGroup = mapper.createNuxeoGroupFromGroupResource(group);
        if (newGroup == null) {
            throw new ServerErrorException("Cannot create group from resource: " + group);
        }
        try {
            GroupResource groupResource = mapper.getGroupResourceFromNuxeoGroup(newGroup, baseURL);
            return ResponseUtils.created(groupResource);
        } catch (URISyntaxException e) {
            throw new ServerErrorException("Cannot create group: " + groupName, null, e);
        }
    }

    protected GroupResource resolveGroupRessource(String uid) throws ScimException {
        DocumentModel groupModel = um.getGroupModel(uid);
        if (groupModel == null) {
            throw new ResourceNotFoundException("Cannot find group: " + uid); // NOSONAR
        }
        try {
            return mapper.getGroupResourceFromNuxeoGroup(groupModel, baseURL);
        } catch (URISyntaxException e) {
            throw new ServerErrorException("Cannot find group: " + uid, null, e);
        }
    }

    protected GroupResource doUpdateGroup(String uid, GroupResource group) throws ScimException {
        if (group == null) {
            throw new BadRequestException("Cannot update group without a group resource as request body",
                    INVALID_SYNTAX);
        }
        DocumentModel groupModel = mapper.updateNuxeoGroupFromGroupResource(uid, group);
        if (groupModel == null) {
            throw new ResourceNotFoundException("Cannot find group: " + uid);
        }
        try {
            return mapper.getGroupResourceFromNuxeoGroup(groupModel, baseURL);
        } catch (URISyntaxException e) {
            throw new ServerErrorException("Cannot update group: " + uid, null, e);
        }
    }

    protected Response doDeleteGroup(String uid) throws ScimException {
        try {
            um.deleteGroup(uid);
            return ResponseUtils.deleted();
        } catch (DirectoryException e) {
            throw new ResourceNotFoundException("Cannot find group: " + uid);
        }
    }

}
