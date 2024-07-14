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

import static com.sun.jersey.api.core.ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS;

import java.util.List;

import org.nuxeo.ecm.webengine.app.jersey.WebEngineServlet;
import org.nuxeo.ecm.webengine.jaxrs.ApplicationHost;
import org.nuxeo.scim.v2.rest.unboundid.DotSearchFilter;

import com.sun.jersey.api.core.ApplicationAdapter;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

/**
 * @since 2023.15
 * @apiNote The servlet will be removed in 2025, because since JAX RS 2.x, such kind of filters are now
 *          {@link javax.ws.rs.ext.Provider providers} and so discoverable.
 */
public class ScimV2Servlet extends WebEngineServlet {

    @Override
    protected ServletContainer createServletContainer(ApplicationHost app) {
        ApplicationAdapter adapter = new ApplicationAdapter(app);
        // disable wadl since we got class loader pb in JAXB under equinox
        adapter.getFeatures().put(ResourceConfig.FEATURE_DISABLE_WADL, Boolean.TRUE);
        // copy all features recorded in app
        adapter.getFeatures().putAll(app.getFeatures());
        // declare filters
        adapter.getProperties().put(PROPERTY_CONTAINER_REQUEST_FILTERS, List.of(DotSearchFilter.class));
        return new ServletContainer(adapter);
    }
}
