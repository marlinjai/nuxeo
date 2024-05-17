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

package org.nuxeo.ecm.platform.web.requestcontroller.filter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.common.http.HttpHeaders.NUXEO_VIRTUAL_HOST;
import static org.nuxeo.common.http.HttpHeaders.X_FORWARDED_HOST;
import static org.nuxeo.launcher.config.ConfigurationConstants.PARAM_NUXEO_ALLOWED_HOSTS;
import static org.nuxeo.launcher.config.ConfigurationConstants.PARAM_NUXEO_URL;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.web.common.requestcontroller.filter.NuxeoHostFilter;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

/**
 * @Since 2023.14
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@WithFrameworkProperty(name = PARAM_NUXEO_URL, value = "http://localhost:8080/nuxeo")
public class TestNuxeoHostFilter {

    protected static final String MY_HOST_ORG = "myhost.org";

    protected NuxeoHostFilter filter;

    protected IndicatorFilter finisher;

    protected HttpServletRequest request;

    protected HttpServletResponse response;

    protected static class IndicatorFilter implements FilterChain {

        protected boolean called;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            called = true;
        }
    }

    @Before
    public void setUp() throws ServletException {
        filter = new NuxeoHostFilter();
        filter.init(null);
        finisher = new IndicatorFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    public void testNuxeoUrlHostFilteringDisabledByDefault() throws IOException, ServletException {
        when(request.getServerName()).thenReturn(MY_HOST_ORG);
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testDeniedHostFiltering() throws IOException, ServletException {
        when(request.getServerName()).thenReturn(MY_HOST_ORG);
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = MY_HOST_ORG)
    public void testAllowedHostFiltering() throws IOException, ServletException {
        when(request.getServerName()).thenReturn(MY_HOST_ORG);
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = MY_HOST_ORG)
    public void testNuxeoUrlAlwaysAllowed() throws IOException, ServletException {
        when(request.getServerName()).thenReturn("localhost");
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = MY_HOST_ORG + ",oh" + MY_HOST_ORG)
    public void testMultipleAllowedHostFilteringFirst() throws IOException, ServletException { // NOSONAR
        when(request.getServerName()).thenReturn(MY_HOST_ORG);
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = MY_HOST_ORG + ",oh" + MY_HOST_ORG)
    public void testMultipleAllowedHostFilteringSecond() throws IOException, ServletException {
        when(request.getServerName()).thenReturn("oh" + MY_HOST_ORG);
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = MY_HOST_ORG + ",oh" + MY_HOST_ORG)
    public void testDeniedHostAgainstMultipleHostFiltering() throws ServletException, IOException {
        when(request.getServerName()).thenReturn("h" + MY_HOST_ORG);
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testDeniedSubdomainHostFiltering() throws IOException, ServletException {
        when(request.getServerName()).thenReturn("not.localhost");
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testDeniedSuffixHostFiltering() throws IOException, ServletException {
        when(request.getServerName()).thenReturn("localhost.attacker.org");
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testDeniedForwardedHostFiltering() throws IOException, ServletException {
        when(request.getHeader(X_FORWARDED_HOST)).thenReturn("not.localhost");
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testDeniedNuxeoVirtualHostFiltering() throws IOException, ServletException {
        when(request.getHeader(NUXEO_VIRTUAL_HOST)).thenReturn("not.localhost");
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testMultipleHeadersAllowedHostFiltering() throws IOException, ServletException {
        setMultipleHostHeaders("localhost", "localhost", "localhost");
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testMultipleHeadersMixedHostFiltering() throws IOException, ServletException {
        setMultipleHostHeaders("localhost", MY_HOST_ORG, "localhost");
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    protected void setMultipleHostHeaders(String serverName, String forwardedHost, String virtualHost) {
        when(request.getServerName()).thenReturn(serverName);
        when(request.getHeader(X_FORWARDED_HOST)).thenReturn(forwardedHost);
        when(request.getHeader(NUXEO_VIRTUAL_HOST)).thenReturn(virtualHost);
    }

}
