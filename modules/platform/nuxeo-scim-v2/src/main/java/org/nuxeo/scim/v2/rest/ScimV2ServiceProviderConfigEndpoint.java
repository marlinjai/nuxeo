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
package org.nuxeo.scim.v2.rest;

import static org.nuxeo.scim.v2.api.ScimV2QueryContext.LIMIT_QUERY_COUNT;

import java.util.List;

import javax.ws.rs.Path;

import com.unboundid.scim2.common.types.AuthenticationScheme;
import com.unboundid.scim2.common.types.BulkConfig;
import com.unboundid.scim2.common.types.ChangePasswordConfig;
import com.unboundid.scim2.common.types.ETagConfig;
import com.unboundid.scim2.common.types.FilterConfig;
import com.unboundid.scim2.common.types.Meta;
import com.unboundid.scim2.common.types.PatchConfig;
import com.unboundid.scim2.common.types.ServiceProviderConfigResource;
import com.unboundid.scim2.common.types.SortConfig;
import com.unboundid.scim2.server.resources.AbstractServiceProviderConfigEndpoint;

/**
 * @since 2023.15
 */
@Path("ServiceProviderConfig")
public class ScimV2ServiceProviderConfigEndpoint extends AbstractServiceProviderConfigEndpoint {

    @Override
    public ServiceProviderConfigResource getServiceProviderConfig() {
        var documentationUri = "https://doc.nuxeo.com/";
        var patch = new PatchConfig(true);
        var bulk = new BulkConfig(false, 0, 0);
        var filter = new FilterConfig(true, LIMIT_QUERY_COUNT);
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

        Meta meta = new Meta();
        meta.setVersion("1");
        config.setMeta(meta);

        return config;
    }
}
