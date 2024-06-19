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

import static com.unboundid.scim2.common.utils.ApiConstants.RESOURCE_TYPES_ENDPOINT;
import static com.unboundid.scim2.common.utils.ApiConstants.SCHEMAS_ENDPOINT;
import static com.unboundid.scim2.common.utils.ApiConstants.SERVICE_PROVIDER_CONFIG_ENDPOINT;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.nuxeo.scim.v2.api.ScimV2ResourceType.SCIM_V2_RESOURCE_TYPE_GROUP;
import static org.nuxeo.scim.v2.api.ScimV2ResourceType.SCIM_V2_RESOURCE_TYPE_RESOURCE_TYPE;
import static org.nuxeo.scim.v2.api.ScimV2ResourceType.SCIM_V2_RESOURCE_TYPE_SCHEMA;
import static org.nuxeo.scim.v2.api.ScimV2ResourceType.SCIM_V2_RESOURCE_TYPE_SERVICE_PROVIDER_CONFIG;
import static org.nuxeo.scim.v2.api.ScimV2ResourceType.SCIM_V2_RESOURCE_TYPE_USER;

import java.beans.IntrospectionException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import org.nuxeo.ecm.webengine.app.jersey.WebEngineServlet;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.scim.v2.api.ScimV2ResourceType;
import org.nuxeo.scim.v2.rest.marshalling.ResponseUtils;

import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.exceptions.ServerErrorException;
import com.unboundid.scim2.common.messages.ErrorResponse;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.types.AuthenticationScheme;
import com.unboundid.scim2.common.types.BulkConfig;
import com.unboundid.scim2.common.types.ChangePasswordConfig;
import com.unboundid.scim2.common.types.ETagConfig;
import com.unboundid.scim2.common.types.FilterConfig;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Meta;
import com.unboundid.scim2.common.types.PatchConfig;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import com.unboundid.scim2.common.types.ServiceProviderConfigResource;
import com.unboundid.scim2.common.types.SortConfig;
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

    public static final String SCIM_V2_ENDPOINT_GROUPS = "/Groups";

    public static final String SCIM_V2_ENDPOINT_USERS = "/Users";

    public static final String SCIM_V2_SCHEMA_GROUP = "urn:ietf:params:scim:schemas:core:2.0:Group";

    public static final String SCIM_V2_SCHEMA_USER = "urn:ietf:params:scim:schemas:core:2.0:User";

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
    @Path("/" + SERVICE_PROVIDER_CONFIG_ENDPOINT)
    public ServiceProviderConfigResource getConfig() throws ScimException {
        var documentationUri = "https://doc.nuxeo.com/";
        // TODO allow PatchConfig
        var patch = new PatchConfig(false);
        var bulk = new BulkConfig(false, 0, 0);
        // TODO allow FilterConfig
        var filter = new FilterConfig(false, 0);
        var changePassword = new ChangePasswordConfig(false);
        var sort = new SortConfig(true);
        var etag = new ETagConfig(false);
        var basicAuthScheme = AuthenticationScheme.createHttpBasic(true);
        var oauth2AuthScheme = AuthenticationScheme.createOAuth2BearerToken(false);
        var authenticationSchemes = List.of(basicAuthScheme, oauth2AuthScheme);

        var config = new ServiceProviderConfigResource(documentationUri, patch, bulk, filter, changePassword, sort,
                etag, authenticationSchemes);
        config.setId("Nuxeo");
        config.setExternalId("Nuxeo");
        setMeta(config, SCIM_V2_RESOURCE_TYPE_SERVICE_PROVIDER_CONFIG, null, "1");

        return config;
    }

    @GET
    @Path("/" + SCHEMAS_ENDPOINT)
    public ListResponse<ScimResource> getSchemas() throws ScimException {
        var userSchema = getSchemaResource(SCIM_V2_SCHEMA_USER);
        setMeta(userSchema, SCIM_V2_RESOURCE_TYPE_SCHEMA, SCIM_V2_SCHEMA_USER, null);
        var groupSchema = getSchemaResource(SCIM_V2_SCHEMA_GROUP);
        setMeta(groupSchema, SCIM_V2_RESOURCE_TYPE_SCHEMA, SCIM_V2_SCHEMA_GROUP, null);
        return new ListResponse<>(List.of(userSchema, groupSchema));
    }

    @GET
    @Path("/" + SCHEMAS_ENDPOINT + "/{schemaName}")
    public SchemaResource getSchema(@PathParam("schemaName") String schemaName) throws ScimException {
        var schema = getSchemaResource(schemaName);
        setMeta(schema, SCIM_V2_RESOURCE_TYPE_SCHEMA, null, null);
        return schema;
    }

    @GET
    @Path("/" + RESOURCE_TYPES_ENDPOINT)
    public ListResponse<ScimResource> getResourceTypes() throws ScimException {
        var userResourceType = getResourceTypeResource(SCIM_V2_RESOURCE_TYPE_USER);
        setMeta(userResourceType, SCIM_V2_RESOURCE_TYPE_RESOURCE_TYPE, SCIM_V2_RESOURCE_TYPE_USER.toString(), null);
        var groupResourceType = getResourceTypeResource(SCIM_V2_RESOURCE_TYPE_GROUP);
        setMeta(groupResourceType, SCIM_V2_RESOURCE_TYPE_RESOURCE_TYPE, SCIM_V2_RESOURCE_TYPE_GROUP.toString(), null);
        return new ListResponse<>(List.of(userResourceType, groupResourceType));
    }

    @GET
    @Path("/" + RESOURCE_TYPES_ENDPOINT + "/{resourceTypeName}")
    public ResourceTypeResource getResourceType(@PathParam("resourceTypeName") String resourceTypeName)
            throws ScimException {
        var resourceType = getResourceTypeResource(ScimV2ResourceType.fromValue(resourceTypeName));
        setMeta(resourceType, SCIM_V2_RESOURCE_TYPE_RESOURCE_TYPE, null, null);
        return resourceType;
    }

    @Path(SCIM_V2_ENDPOINT_USERS)
    public Object doGetUsersResource() {
        return newObject("users", getBaseURL());
    }

    @Path(SCIM_V2_ENDPOINT_GROUPS)
    public Object doGetGroups() {
        return newObject("groups", getBaseURL());
    }

    protected String getBaseURL() {
        var baseURL = VirtualHostHelper.getServerURL(ctx.getRequest()); // http://localhost:8080/
        while (baseURL.endsWith("/")) {
            baseURL = baseURL.substring(0, baseURL.length() - 1); // http://localhost:8080
        }
        return baseURL + ctx.getUrlPath(); // e.g.: http://localhost:8080/nuxeo/scim/v2/Users
    }

    protected SchemaResource getSchemaResource(String schemaName) throws ScimException {
        return switch (schemaName) {
            case SCIM_V2_SCHEMA_USER -> getSchemaResource(UserResource.class);
            case SCIM_V2_SCHEMA_GROUP -> getSchemaResource(GroupResource.class);
            default -> throw new ResourceNotFoundException("Cannot find schema:" + schemaName);
        };
    }

    protected <T extends ScimResource> SchemaResource getSchemaResource(Class<T> resourceClass) throws ScimException {
        try {
            return SchemaUtils.getSchema(resourceClass);
        } catch (IntrospectionException e) {
            throw new ServerErrorException("Cannot get schema resource for class:" + resourceClass.getSimpleName());
        }
    }

    protected void setMeta(ScimResource resource, ScimV2ResourceType resourceType, String locationSuffix,
            String version) throws ScimException {
        var meta = new Meta();
        meta.setResourceType(resourceType.toString());
        var uriString = locationSuffix == null ? getBaseURL() : String.join("/", getBaseURL(), locationSuffix);
        URI location = getURI(uriString);
        meta.setLocation(location);
        if (version != null) {
            meta.setVersion(version);
        }
        resource.setMeta(meta);
    }

    protected URI getURI(String uri) throws ScimException {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new ServerErrorException("Cannot create URI for sring: " + uri, null, e);
        }
    }

    protected ResourceTypeResource getResourceTypeResource(ScimV2ResourceType type) throws ScimException {
        return switch (type) {
            case SCIM_V2_RESOURCE_TYPE_USER -> new ResourceTypeResource(SCIM_V2_RESOURCE_TYPE_USER.toString(),
                    "User Account", getURI(SCIM_V2_ENDPOINT_USERS), getURI(SCIM_V2_SCHEMA_USER));
            case SCIM_V2_RESOURCE_TYPE_GROUP -> new ResourceTypeResource(SCIM_V2_RESOURCE_TYPE_GROUP.toString(), "Group", // NOSONAR
                    getURI(SCIM_V2_ENDPOINT_GROUPS), getURI(SCIM_V2_SCHEMA_GROUP));
            default -> throw new ResourceNotFoundException("Cannot find resource type:" + type);
        };
    }

}
