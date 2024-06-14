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
package org.nuxeo.scim.v2.rest;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.beans.IntrospectionException;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.webengine.app.jersey.WebEngineServlet;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.scim.v2.rest.marshalling.ResponseUtils;

import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ErrorResponse;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.SchemaResource;
import com.unboundid.scim2.common.types.UserResource;
import com.unboundid.scim2.common.utils.SchemaUtils;

/**
 * Root entry for the SCIM 2.0 WebEngine module.
 * <p>
 * The {@link WebEngineServlet} is mapped to /scim/v2/* in deployment-fragment.xml.
 * </p>
 *
 * @since 2023.14
 */
@Path("/")
@Produces(APPLICATION_JSON)
@WebObject(type = "scimV2")
public class ScimV2Root extends ModuleRoot {

    private static final Logger log = LogManager.getLogger(ScimV2Root.class);

    public static final String SCIM_V2_SCHEMA_USER = "urn:ietf:params:scim:schemas:core:2.0:User";

    public static final String SCIM_V2_SCHEMA_GROUP = "urn:ietf:params:scim:schemas:core:2.0:Group";

    @Override
    public Object handleError(Throwable t) {
        ScimException scimException = null;
        if (t instanceof ScimException e) {
            scimException = e;
        } else if (t.getCause() instanceof ScimException e) {
            scimException = e;
        } else {
            return super.handleError(t);
        }
        ErrorResponse errorResponse = scimException.getScimError();
        logError(errorResponse.getStatus(), scimException);
        return ResponseUtils.error(errorResponse);
    }

    protected void logError(int statusCode, Throwable throwable) {
        if (statusCode >= SC_INTERNAL_SERVER_ERROR) {
            log.error(throwable, throwable);
        } else if (Framework.isDevModeSet()) {
            log.warn(throwable, throwable);
        } else {
            log.debug(throwable, throwable);
        }
    }

    @GET
    @Path("/Schemas")
    public ListResponse<ScimResource> getSchemas() throws IntrospectionException, ScimException {
        ScimResource userSchema = getSchemaResource(SCIM_V2_SCHEMA_USER);
        ScimResource groupSchema = getSchemaResource(SCIM_V2_SCHEMA_GROUP);
        return new ListResponse<>(List.of(userSchema, groupSchema));
    }

    @GET
    @Path("/Schemas/{schemaName}")
    public SchemaResource getSchema(@PathParam("schemaName") String schemaName)
            throws IntrospectionException, ScimException {
        return getSchemaResource(schemaName);
    }

    @Path("/Users")
    public Object doGetUsersResource() {
        return newObject("users");
    }

    @Path("/Groups")
    public Object doGetGroups() {
        return newObject("groups");
    }

    protected SchemaResource getSchemaResource(String schemaName) throws IntrospectionException, ScimException {
        return switch (schemaName) {
            case SCIM_V2_SCHEMA_USER -> SchemaUtils.getSchema(UserResource.class);
            case SCIM_V2_SCHEMA_GROUP -> SchemaUtils.getSchema(GroupResource.class);
            default -> throw new ResourceNotFoundException("Cannot find schema:" + schemaName);
        };
    }

}
