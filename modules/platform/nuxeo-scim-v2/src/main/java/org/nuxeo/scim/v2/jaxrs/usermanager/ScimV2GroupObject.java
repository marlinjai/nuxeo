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

import com.unboundid.scim2.common.types.GroupResource;

/**
 * SCIM 2.0 Group object.
 *
 * @since 2023.13
 */
@WebObject(type = "groups")
@Produces(APPLICATION_JSON)
public class ScimV2GroupObject extends ScimV2BaseUMObject {

    private static final Logger log = LogManager.getLogger(ScimV2GroupObject.class);

    @POST
    public Response createGroup(GroupResource group) {
        checkUpdateGuardPreconditions();
        return doCreateGroup(group);
    }

    @GET
    @Path("{uid}")
    public GroupResource getGroupResource(@PathParam("uid") String uid) {
        return resolveGroupRessource(uid);
    }

    @PUT
    @Path("{uid}")
    public Response updateGroup(@PathParam("uid") String uid, GroupResource group) {
        checkUpdateGuardPreconditions();
        return doUpdateGroup(uid, group);
    }

    @Path("{uid}")
    @DELETE
    public Response deleteGroupResource(@PathParam("uid") String uid) {
        try {
            um.deleteGroup(uid);
            return ResponseUtils.deleted();
        } catch (DirectoryException e) {
            return ResponseUtils.notFound();
        }
    }

    @Override
    protected String getPrefix() {
        return "/Groups";
    }

    protected Response doCreateGroup(GroupResource group) {
        try {
            DocumentModel newGroup = mapper.createNuxeoGroupFromGroupResource(group);
            GroupResource groupResource = mapper.getGroupResourceFromNuxeoGroup(newGroup);
            return ResponseUtils.created(groupResource);
        } catch (URISyntaxException e) {
            log.error("Unable to create group: {}", group.getId(), e);
        }
        return null;
    }

    protected GroupResource resolveGroupRessource(String uid) {
        try {
            DocumentModel groupModel = um.getGroupModel(uid);
            if (groupModel != null) {
                return mapper.getGroupResourceFromNuxeoGroup(groupModel);
            }
        } catch (URISyntaxException e) {
            log.error("Error while resolving group: {}", uid, e);
        }
        return null;
    }

    protected Response doUpdateGroup(String uid, GroupResource group) {
        try {
            DocumentModel groupModel = mapper.updateNuxeoGroupFromGroupResource(uid, group);
            if (groupModel != null) {
                GroupResource groupResource = mapper.getGroupResourceFromNuxeoGroup(groupModel);
                return ResponseUtils.updated(groupResource);
            }
        } catch (URISyntaxException e) {
            log.error("Unable to update group: {}", uid, e);
        }
        return null;
    }

}
