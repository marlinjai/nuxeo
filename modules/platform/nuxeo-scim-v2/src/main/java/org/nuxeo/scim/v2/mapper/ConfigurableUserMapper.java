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
package org.nuxeo.scim.v2.mapper;

import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.usermapper.service.UserMapperService;

import com.unboundid.scim2.common.types.UserResource;

/**
 * SCIM 2.0 mapper implementation relying on the {@link UserMapperService}.
 *
 * @since 2023.14
 */
public class ConfigurableUserMapper extends AbstractMapper {

    protected static final String SCIM_V2_MAPPING_NAME = "scimV2";

    @Override
    public DocumentModel createNuxeoUserFromUserResource(UserResource user) {
        UserMapperService mapperService = Framework.getService(UserMapperService.class);
        NuxeoPrincipal principal = mapperService.getOrCreateAndUpdateNuxeoPrincipal(SCIM_V2_MAPPING_NAME, user);
        if (principal == null) {
            return null;
        }
        UserManager um = Framework.getService(UserManager.class);
        return um.getUserModel(principal.getName());
    }

    @Override
    public DocumentModel updateNuxeoUserFromUserResource(String uid, UserResource user) {
        UserManager um = Framework.getService(UserManager.class);
        Map<String, Serializable> params = Map.of(um.getUserIdField(), uid);
        UserMapperService mapperService = Framework.getService(UserMapperService.class);
        NuxeoPrincipal principal = mapperService.getOrCreateAndUpdateNuxeoPrincipal(SCIM_V2_MAPPING_NAME, user, false,
                true, params);
        if (principal == null) {
            return null;
        }
        return um.getUserModel(principal.getName());
    }

    @Override
    public UserResource getUserResourceFromNuxeoUser(DocumentModel userModel, String baseURL)
            throws URISyntaxException {
        UserManager um = Framework.getService(UserManager.class);
        String userSchemaName = um.getUserSchemaName();
        String userId = (String) userModel.getProperty(userSchemaName, um.getUserIdField());

        UserResource userResource = getUserResourceFromUserModel(userId, baseURL);
        NuxeoPrincipal principal = um.getPrincipal(userId);

        UserMapperService mapperService = Framework.getService(UserMapperService.class);
        return (UserResource) mapperService.wrapNuxeoPrincipal(SCIM_V2_MAPPING_NAME, principal, userResource, null);
    }

}
