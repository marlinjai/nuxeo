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

import static com.unboundid.scim2.common.messages.SortOrder.DESCENDING;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_ATTRIBUTES;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_EXCLUDED_ATTRIBUTES;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_FILTER;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_PAGE_SIZE;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_SORT_BY;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_SORT_ORDER;
import static com.unboundid.scim2.common.utils.ApiConstants.SEARCH_WITH_POST_PATH_EXTENSION;

import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.scim.v2.api.ScimV2MappingService;

import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.ForbiddenException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.messages.PatchOperation;
import com.unboundid.scim2.common.messages.SearchRequest;
import com.unboundid.scim2.common.utils.ApiConstants;
import com.unboundid.scim2.server.utils.ResourcePreparer;
import com.unboundid.scim2.server.utils.ResourceTypeDefinition;

/**
 * @since 2023.14
 */
public abstract class ScimV2BaseUMObject extends DefaultObject {

    protected String baseURL;

    protected ScimV2MappingService mappingService;

    protected UserManager um;

    @Context
    protected UriInfo uriInfo;

    protected abstract ListResponse<ScimResource> doSearch(Integer startIndex, Integer count, String filterString,
            String sortBy, boolean descending) throws ScimException;

    protected abstract String getPrefix();

    protected abstract ResourceTypeDefinition getResourceTypeDefinition() throws ScimException;

    @Override
    protected void initialize(Object... args) {
        super.initialize(args);
        um = Framework.getService(UserManager.class);
        mappingService = Framework.getService(ScimV2MappingService.class);
        baseURL = (String) args[0];
        int idx = baseURL.lastIndexOf(getPrefix());
        if (idx > 0) {
            baseURL = baseURL.substring(0, idx + getPrefix().length()); // http://localhost:8080/nuxeo/scim/v2/Users
        }
    }

    @GET
    public ListResponse<ScimResource> getResources(
            @QueryParam(ApiConstants.QUERY_PARAMETER_PAGE_START_INDEX) Integer startIndex,
            @QueryParam(QUERY_PARAMETER_PAGE_SIZE) Integer count, @QueryParam(QUERY_PARAMETER_FILTER) String filter,
            @QueryParam(QUERY_PARAMETER_SORT_BY) String sortBy,
            @QueryParam(QUERY_PARAMETER_SORT_ORDER) String sortOrder) throws ScimException {
        return doSearch(startIndex, count, filter, sortBy, DESCENDING.getName().equalsIgnoreCase(sortOrder));
    }

    @POST
    @Path("/" + SEARCH_WITH_POST_PATH_EXTENSION)
    public ListResponse<ScimResource> getResources(SearchRequest request) throws ScimException {
        if (request.getAttributes() != null) {
            uriInfo.getQueryParameters()
                   .add(QUERY_PARAMETER_ATTRIBUTES, request.getAttributes().stream().collect(Collectors.joining(",")));
        }
        if (request.getExcludedAttributes() != null) {
            uriInfo.getQueryParameters()
                   .add(QUERY_PARAMETER_EXCLUDED_ATTRIBUTES,
                           request.getExcludedAttributes().stream().collect(Collectors.joining(",")));
        }
        return doSearch(request.getStartIndex(), request.getCount(), request.getFilter(), request.getSortBy(),
                DESCENDING.equals(request.getSortOrder()));
    }

    protected void checkUpdateGuardPreconditions() throws ScimException {
        NuxeoPrincipal principal = getContext().getCoreSession().getPrincipal();
        if (!principal.isAdministrator() && (!principal.isMemberOf("powerusers") || !isAPowerUserEditableArtifact())) {
            throw new ForbiddenException(
                    String.format("User: %s is not allowed to edit users or groups", principal.getName()));
        }
    }

    /**
     * Check that the current artifact is editable by a power user. Basically this means not an administrator user or
     * not an administrator group.
     */
    protected boolean isAPowerUserEditableArtifact() {
        return false;
    }

    protected <T extends ScimResource> GenericScimResource prepareCreated(T returnedResource, T requestResource)
            throws ScimException {
        ResourcePreparer<T> resourcePreparer = new ResourcePreparer<>(getResourceTypeDefinition(), uriInfo);
        return resourcePreparer.trimCreatedResource(returnedResource, requestResource);
    }

    protected <T extends ScimResource> GenericScimResource prepareModified(T returnedResource,
            Iterable<PatchOperation> patchOperations) throws ScimException {
        ResourcePreparer<T> resourcePreparer = new ResourcePreparer<>(getResourceTypeDefinition(), uriInfo);
        return resourcePreparer.trimModifiedResource(returnedResource, patchOperations);
    }

    protected <T extends ScimResource> GenericScimResource prepareReplaced(T returnedResource, T requestResource)
            throws ScimException {
        ResourcePreparer<T> resourcePreparer = new ResourcePreparer<>(getResourceTypeDefinition(), uriInfo);
        return resourcePreparer.trimReplacedResource(returnedResource, requestResource);
    }

    protected <T extends ScimResource> GenericScimResource prepareRetrieved(T resource) throws ScimException {
        ResourcePreparer<T> resourcePreparer = new ResourcePreparer<>(getResourceTypeDefinition(), uriInfo);
        return resourcePreparer.trimRetrievedResource(resource);
    }

}
