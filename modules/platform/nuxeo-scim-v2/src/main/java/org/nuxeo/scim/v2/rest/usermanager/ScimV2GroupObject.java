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
import static org.nuxeo.scim.v2.rest.ScimV2Root.SCIM_V2_ENDPOINT_GROUPS;

import java.beans.IntrospectionException;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.nuxeo.common.function.ThrowableUnaryOperator;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.scim.v2.rest.marshalling.ResponseUtils;

import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.exceptions.ServerErrorException;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.utils.SchemaUtils;
import com.unboundid.scim2.server.utils.ResourceTypeDefinition;

/**
 * SCIM 2.0 Group object.
 *
 * @since 2023.14
 */
@WebObject(type = "groups")
@Produces(APPLICATION_JSON)
public class ScimV2GroupObject extends ScimV2BaseUMObject {

    @POST
    public Response createGroup(GroupResource group) throws ScimException {
        checkUpdateGuardPreconditions();
        return ResponseUtils.response(CREATED, prepareCreated(doCreateGroup(group), group));
    }

    @GET
    @Path("{uid}")
    public ScimResource getGroupResource(@PathParam("uid") String uid) throws ScimException {
        return prepareRetrieved(resolveGroupRessource(uid));
    }

    @PUT
    @Path("{uid}")
    public ScimResource updateGroup(@PathParam("uid") String uid, GroupResource group) throws ScimException {
        checkUpdateGuardPreconditions();
        return prepareReplaced(doUpdateGroup(uid, group), group);
    }

    @DELETE
    @Path("{uid}")
    public Response deleteGroupResource(@PathParam("uid") String uid) throws ScimException {
        checkUpdateGuardPreconditions();
        return doDeleteGroup(uid);
    }

    @Override
    protected String getPrefix() {
        return SCIM_V2_ENDPOINT_GROUPS;
    }

    protected ScimResource doCreateGroup(GroupResource group) throws ScimException {
        if (group == null) {
            throw new BadRequestException("Cannot create group without a group resource as request body",
                    INVALID_SYNTAX);
        }
        DocumentModel newGroup = mappingService.createNuxeoGroupFromGroupResource(group);
        return mappingService.getGroupResourceFromNuxeoGroup(newGroup, baseURL);

    }

    protected GroupResource resolveGroupRessource(String uid) throws ScimException {
        DocumentModel groupModel = um.getGroupModel(uid);
        if (groupModel == null) {
            throw new ResourceNotFoundException("Cannot find group: " + uid); // NOSONAR
        }
        return mappingService.getGroupResourceFromNuxeoGroup(groupModel, baseURL);
    }

    protected GroupResource doUpdateGroup(String uid, GroupResource group) throws ScimException {
        if (group == null) {
            throw new BadRequestException("Cannot update group without a group resource as request body",
                    INVALID_SYNTAX);
        }
        DocumentModel groupModel = mappingService.updateNuxeoGroupFromGroupResource(uid, group);
        return mappingService.getGroupResourceFromNuxeoGroup(groupModel, baseURL);
    }

    protected Response doDeleteGroup(String uid) throws ScimException {
        try {
            um.deleteGroup(uid);
            return ResponseUtils.deleted();
        } catch (DirectoryException e) {
            throw new ResourceNotFoundException("Cannot find group: " + uid);
        }
    }

    @Override
    protected ListResponse<ScimResource> doSearch(Integer startIndex, Integer count, String filterString, String sortBy,
            boolean descending) throws ScimException {
        return mappingService.queryGroups(startIndex, count, filterString, sortBy, descending, baseURL,
                ThrowableUnaryOperator.asUnaryOperator(r -> prepareRetrieved(r)));
    }

    @Override
    protected ResourceTypeDefinition getResourceTypeDefinition() throws ScimException {
        try {
            return new ResourceTypeDefinition.Builder("groups", SCIM_V2_ENDPOINT_GROUPS).setCoreSchema(
                    SchemaUtils.getSchema(GroupResource.class)).build();
        } catch (IntrospectionException e) {
            throw new ServerErrorException("Cannot get resource type definition");
        }
    }

}
