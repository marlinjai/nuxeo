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
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_FILTER;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_PAGE_SIZE;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_PAGE_START_INDEX;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_SORT_BY;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_SORT_ORDER;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.webengine.WebEngine;
import org.nuxeo.ecm.webengine.app.DefaultContext;
import org.nuxeo.ecm.webengine.model.WebContext;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.scim.v2.api.ScimV2MappingService;
import org.nuxeo.scim.v2.api.ScimV2QueryContext;

import com.sun.jersey.api.core.HttpContext;
import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.ForbiddenException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;

/**
 * @since 2023.14
 */
public abstract class ScimV2BaseUMObject {

    protected final String baseURL;

    protected final ScimV2MappingService mappingService;

    protected final UserManager um;

    protected final WebContext webContext;

    protected ScimV2BaseUMObject(HttpContext httpContext) {
        this.mappingService = Framework.getService(ScimV2MappingService.class);
        this.um = Framework.getService(UserManager.class);
        this.webContext = WebEngine.getActiveContext();
        // as we're out of WebEngine we need to manually set HttpContext
        ((DefaultContext) this.webContext).setJerseyContext(null, httpContext);
        // compute base url of the current resource
        var pathAnnotation = getClass().getAnnotation(Path.class);
        var serverBaseURL = webContext.getServerURL().append(webContext.getUrlPath()).toString(); // http://localhost:8080/nuxeo/scim/v2/Users/foo
        int idx = serverBaseURL.lastIndexOf(pathAnnotation.value());
        if (idx > 0) {
            serverBaseURL = serverBaseURL.substring(0, idx + pathAnnotation.value().length()); // http://localhost:8080/nuxeo/scim/v2/Users
        }
        this.baseURL = serverBaseURL;
    }

    @GET
    public ListResponse<ScimResource> getResources(@QueryParam(QUERY_PARAMETER_PAGE_START_INDEX) Integer startIndex,
            @QueryParam(QUERY_PARAMETER_PAGE_SIZE) Integer count, @QueryParam(QUERY_PARAMETER_FILTER) String filter,
            @QueryParam(QUERY_PARAMETER_SORT_BY) String sortBy,
            @QueryParam(QUERY_PARAMETER_SORT_ORDER) String sortOrder) throws ScimException {
        return doSearch(new ScimV2QueryContext().withStartIndex(startIndex)
                                                .withCount(count)
                                                .withFilterString(filter)
                                                .withSortBy(sortBy)
                                                .withDescending(DESCENDING.getName().equalsIgnoreCase(sortOrder)));
    }

    protected abstract ListResponse<ScimResource> doSearch(ScimV2QueryContext queryCtx) throws ScimException;

    protected void checkUpdateGuardPreconditions() throws ScimException {
        NuxeoPrincipal principal = webContext.getCoreSession().getPrincipal();
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
}
