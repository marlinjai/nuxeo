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
package org.nuxeo.scim.v2.jaxrs.usermanager;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.scim.v2.mapper.AbstractMapper;
import org.nuxeo.scim.v2.mapper.UserMapperFactory;

import com.unboundid.scim2.common.exceptions.ForbiddenException;
import com.unboundid.scim2.common.exceptions.ScimException;

/**
 * @since 2023.13
 */
public abstract class ScimV2BaseUMObject extends DefaultObject {

    protected UserManager um;

    protected AbstractMapper mapper;

    protected abstract String getPrefix();

    @Override
    protected void initialize(Object... args) {
        super.initialize(args);
        um = Framework.getService(UserManager.class);
        String baseURL = VirtualHostHelper.getServerURL(ctx.getRequest()); // http://localhost:8080/
        while (baseURL.endsWith("/")) {
            baseURL = baseURL.substring(0, baseURL.length() - 1); // http://localhost:8080
        }
        baseURL = baseURL + ctx.getUrlPath(); // http://localhost:8080/nuxeo/scim/v2/Users/Administrator
        int idx = baseURL.lastIndexOf(getPrefix());
        if (idx > 0) {
            baseURL = baseURL.substring(0, idx + getPrefix().length()); // http://localhost:8080/nuxeo/scim/v2/Users
        }
        mapper = UserMapperFactory.getMapper(baseUrl);
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

}
