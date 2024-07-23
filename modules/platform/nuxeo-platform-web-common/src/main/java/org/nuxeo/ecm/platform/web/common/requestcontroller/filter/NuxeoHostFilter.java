/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.nuxeo.ecm.platform.web.common.requestcontroller.filter;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.nuxeo.common.http.HttpHeaders.NUXEO_VIRTUAL_HOST;
import static org.nuxeo.common.http.HttpHeaders.X_FORWARDED_HOST;
import static org.nuxeo.launcher.config.ConfigurationConstants.PARAM_NUXEO_ALLOWED_HOSTS;
import static org.nuxeo.launcher.config.ConfigurationConstants.PARAM_NUXEO_URL;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

/** @since 2023.14 */
public class NuxeoHostFilter extends HttpFilter {

    private static final Logger log = LogManager.getLogger(NuxeoHostFilter.class);

    protected transient Set<String> allowedHostnames;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        allowedHostnames = new HashSet<>();
        String allowedHostsProp = Framework.getProperty(PARAM_NUXEO_ALLOWED_HOSTS);
        if (StringUtils.isNotBlank(allowedHostsProp)) {
            String nuxeoUrl = Framework.getProperty(PARAM_NUXEO_URL);
            if (StringUtils.isNotBlank(nuxeoUrl)) {
                try {
                    String host = new URI(nuxeoUrl).getHost();
                    if (StringUtils.isNotBlank(host)) {
                        allowedHostnames.add(host);
                    }
                } catch (URISyntaxException e) {
                    throw new NuxeoException(e);
                }
            }
            allowedHostnames.addAll(
                    Arrays.stream(allowedHostsProp.split(",")).filter(StringUtils::isNotBlank).toList());
        }
        log.debug("Host filter initiated with allowed hosts: {}", allowedHostnames);
    }

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (allowedHostnames.isEmpty() || acceptTrustedHostRequest(request)) {
            chain.doFilter(request, response);
        } else {
            response.sendError(SC_BAD_REQUEST, "Host check failure");
        }
    }

    protected boolean acceptTrustedHostRequest(HttpServletRequest request) {
        var hostHeaders = new HashSet<String>();
        hostHeaders.add(request.getServerName());
        CollectionUtils.addIgnoreNull(hostHeaders, request.getHeader(X_FORWARDED_HOST));
        String nvhHeader = request.getHeader(NUXEO_VIRTUAL_HOST);
        if (StringUtils.isNotBlank(nvhHeader)) {
            try {
                String host = new URI(nvhHeader).getHost();
                if (StringUtils.isNotBlank(host)) {
                    hostHeaders.add(host);
                } else {
                    throw new NuxeoException("Rejecting null resulting host of url: " + nvhHeader + " from "
                            + NUXEO_VIRTUAL_HOST + " header", SC_BAD_REQUEST);
                }
            } catch (URISyntaxException e) {
                throw new NuxeoException(e, SC_BAD_REQUEST);
            }
        }
        return allowedHostnames.containsAll(hostHeaders);
    }

}
