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

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.usermapper.service.UserMapperService;

/**
 * Factory to get the SCIM 2.0 mapper implementation.
 *
 * @since 2023.13
 */
public final class UserMapperFactory {

    protected static AbstractMapper mapper;

    private UserMapperFactory() {
        // helper class
    }

    public static synchronized AbstractMapper getMapper() {
        if (mapper == null) {
            UserMapperService ums = Framework.getService(UserMapperService.class);
            if (ums != null && ums.getAvailableMappings().contains(ConfigurableUserMapper.SCIM_V2_MAPPING_NAME)) {
                mapper = new ConfigurableUserMapper();
            } else {
                mapper = new StaticUserMapper();
            }
        }
        return mapper;
    }

}
