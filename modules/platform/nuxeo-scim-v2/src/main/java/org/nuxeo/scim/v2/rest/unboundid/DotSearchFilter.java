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
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.scim.v2.rest.unboundid;

import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_ATTRIBUTES;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_EXCLUDED_ATTRIBUTES;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_FILTER;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_PAGE_SIZE;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_PAGE_START_INDEX;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_SORT_BY;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_SORT_ORDER;
import static com.unboundid.scim2.common.utils.ApiConstants.SEARCH_WITH_POST_PATH_EXTENSION;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectReader;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.unboundid.scim2.common.messages.SearchRequest;
import com.unboundid.scim2.common.utils.JsonUtils;
import com.unboundid.scim2.common.utils.StaticUtils;
import com.unboundid.scim2.server.utils.ServerUtils;

/**
 * A ContainerRequestFilter implementation to convert a search request using HTTP POST combine with the
 * "{@code .search}" path extension to a regular search using HTTP GET.
 * <p>
 * Copied from {@link com.unboundid.scim2.server.providers.DotSearchFilter} to make it compatible with JAX RS 1.x.
 *
 * @since 2023.15
 * @apiNote This filter will be removed in 2025.0 as it is provided by the scim2 library
 */
public class DotSearchFilter implements ContainerRequestFilter {

    /**
     * {@inheritDoc}
     */
    public ContainerRequest filter(final ContainerRequest request) {
        if (request.getMethod().equals(HttpMethod.POST)
                && request.getPath().endsWith(SEARCH_WITH_POST_PATH_EXTENSION)) {
            if (request.getMediaType() == null
                    || !(request.getMediaType().isCompatible(ServerUtils.MEDIA_TYPE_SCIM_TYPE)
                            || request.getMediaType().isCompatible(MediaType.APPLICATION_JSON_TYPE))) {
                throw new WebApplicationException(Response.Status.UNSUPPORTED_MEDIA_TYPE);
            }

            SearchRequest searchRequest;
            try {
                ObjectReader reader = JsonUtils.getObjectReader().forType(SearchRequest.class);
                JsonParser p = reader.getFactory().createParser(request.getEntityInputStream());
                if (p.nextToken() == null) {
                    throw new WebApplicationException(new IOException("Empty Entity"), Response.Status.BAD_REQUEST);
                }
                searchRequest = reader.readValue(p);
            } catch (IOException e) {
                throw new WebApplicationException(e, Response.Status.BAD_REQUEST);
            }
            UriBuilder builder = request.getBaseUriBuilder();
            List<PathSegment> pathSegments = request.getPathSegments();
            for (int i = 0; i < pathSegments.size() - 1; i++) {
                builder.path(pathSegments.get(i).getPath());
            }
            if (searchRequest.getAttributes() != null) {
                builder.queryParam(QUERY_PARAMETER_ATTRIBUTES, ServerUtils.encodeTemplateNames(
                        StaticUtils.collectionToString(searchRequest.getAttributes(), ",")));
            }
            if (searchRequest.getExcludedAttributes() != null) {
                builder.queryParam(QUERY_PARAMETER_EXCLUDED_ATTRIBUTES, ServerUtils.encodeTemplateNames(
                        StaticUtils.collectionToString(searchRequest.getExcludedAttributes(), ",")));
            }
            if (searchRequest.getFilter() != null) {
                builder.queryParam(QUERY_PARAMETER_FILTER, ServerUtils.encodeTemplateNames(searchRequest.getFilter()));
            }
            if (searchRequest.getSortBy() != null) {
                builder.queryParam(QUERY_PARAMETER_SORT_BY, ServerUtils.encodeTemplateNames(searchRequest.getSortBy()));
            }
            if (searchRequest.getSortOrder() != null) {
                builder.queryParam(QUERY_PARAMETER_SORT_ORDER,
                        ServerUtils.encodeTemplateNames(searchRequest.getSortOrder().getName()));
            }
            if (searchRequest.getStartIndex() != null) {
                builder.queryParam(QUERY_PARAMETER_PAGE_START_INDEX, searchRequest.getStartIndex());
            }
            if (searchRequest.getCount() != null) {
                builder.queryParam(QUERY_PARAMETER_PAGE_SIZE, searchRequest.getCount());
            }
            request.setUris(request.getBaseUri(), builder.build());
            request.setMethod(HttpMethod.GET);
        }
        return request;
    }
}
