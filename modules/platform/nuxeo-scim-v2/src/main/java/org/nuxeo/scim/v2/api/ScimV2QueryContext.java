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
 *     Guillaume Renard
 */
package org.nuxeo.scim.v2.api;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.types.GroupResource;

/**
 * @since 2023.15
 */
public class ScimV2QueryContext {

    public static final String FETCH_GROUP_MEMBERS_CTX_PARAM = "fetchMembers";

    public static final int LIMIT_QUERY_COUNT = 1000;

    protected static final int DEFAULT_QUERY_COUNT = 100;

    protected String baseURL;

    protected Map<String, Serializable> contexParams = new HashMap<>();

    protected Integer count = DEFAULT_QUERY_COUNT;

    protected boolean descending = false;

    protected String filterString;

    protected String sortBy;

    protected Integer startIndex = 1;

    protected UnaryOperator<ScimResource> transform;

    public String getBaseURL() {
        return baseURL;
    }

    public Serializable getContexParam(String name) {
        return contexParams.get(name);
    }

    public Map<String, Serializable> getContexParams() {
        return contexParams;
    }

    public Integer getCount() {
        return count;
    }

    public String getFilterString() {
        return filterString;
    }

    public String getSortBy() {
        return sortBy;
    }

    public Integer getStartIndex() {
        return startIndex;
    }

    public UnaryOperator<ScimResource> getTransform() {
        if (transform == null) {
            return t -> t;
        }
        return transform;
    }

    public boolean isContextParamFalse(String name) {
        return Boolean.FALSE.equals(contexParams.get(name));
    }

    public boolean isContextParamTrue(String name) {
        return Boolean.TRUE.equals(contexParams.get(name));
    }

    public boolean isDescending() {
        return descending;
    }

    /**
     * The location base URL of the returned SCIM objects.
     */
    public ScimV2QueryContext withBaseUrl(String baseURL) {
        this.baseURL = baseURL;
        return this;
    }

    public ScimV2QueryContext withContextParam(String name, Serializable value) {
        contexParams.put(name, value);
        return this;
    }

    /**
     * Specifies the desired maximum number of query results per page.
     */
    public ScimV2QueryContext withCount(Integer count) throws BadRequestException {
        if (count != null && count > LIMIT_QUERY_COUNT) {
            throw BadRequestException.tooMany("Maximum value for count is " + LIMIT_QUERY_COUNT);
        }
        this.count = count;
        return this;
    }

    /**
     * If true, sorts descending, ascending otherwise.
     */
    public ScimV2QueryContext withDescending(boolean descending) {
        this.descending = descending;
        return this;
    }

    /**
     * The SCIM filter expression (see https://datatracker.ietf.org/doc/html/rfc7644#section-3.4.2.2).
     */
    public ScimV2QueryContext withFilterString(String filterString) {
        this.filterString = filterString;
        return this;
    }

    /**
     * The attribute whose value will be used to order the returned responses.
     */
    public ScimV2QueryContext withSortBy(String sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    /**
     * The 1-based index of the first query result.
     */
    public ScimV2QueryContext withStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
        return this;
    }

    /**
     * Operator to transform the resulting {@link GroupResource} into {@link GenericScimResource}.
     */
    public ScimV2QueryContext withTransform(UnaryOperator<ScimResource> transform) {
        this.transform = transform;
        return this;
    }

}
