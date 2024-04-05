/*
 * (C) Copyright 2015-2024 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     François Maturel
 */

package org.nuxeo.ecm.platform.ui.web.keycloak;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.NodesRegistrationManagement;
import org.keycloak.adapters.tomcat.CatalinaHttpFacade;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.platform.ui.web.auth.LoginScreenHelper;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 7.4
 */
public class KeycloakAuthenticatorProvider {

    private static final Logger log = LogManager.getLogger(KeycloakAuthenticatorProvider.class);

    protected static final String CLIENT_ID_PARAM = "client_id";

    protected static final String CLIENT_SECRET_PARAM = "client_secret";

    protected static final String ID_TOKEN_HINT_PARAM = "id_token_hint";

    protected static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";

    protected static final String GRANT_TYPE_PARAM = "grant_type";

    protected static final String RESOURCE_SECRET_KEY = "secret";

    protected static final String POST_LOGOUT_REDIRECT_URI_PARAM = "post_logout_redirect_uri";

    protected static final String SCOPE_PARAM = "scope";

    protected static final String TOKEN_SCOPE_OPENID = "openid";

    protected static final ObjectMapper mapper = new ObjectMapper();

    protected final NodesRegistrationManagement nodesRegistrationManagement = new NodesRegistrationManagement();

    protected final AdapterDeploymentContext deploymentContext;

    protected KeycloakDeployment resolvedDeployment;

    public KeycloakAuthenticatorProvider(AdapterDeploymentContext deploymentContext) {
        this.deploymentContext = deploymentContext;
    }

    public KeycloakRequestAuthenticator provide(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        DeploymentResult deploymentResult = new DeploymentResult(httpServletRequest, httpServletResponse).invokeOn(
                deploymentContext);

        if (!deploymentResult.isOk()) {
            return null;
        }

        resolvedDeployment = DeploymentResult.getKeycloakDeployment();
        Request request = deploymentResult.getRequest();
        CatalinaHttpFacade facade = deploymentResult.getFacade();

        nodesRegistrationManagement.tryRegister(resolvedDeployment);

        return new KeycloakRequestAuthenticator(request, httpServletResponse, facade, resolvedDeployment);
    }

    public String logout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        DeploymentResult deploymentResult = new DeploymentResult(httpServletRequest, httpServletResponse).invokeOn(
                deploymentContext);

        if (!deploymentResult.isOk()) {
            return null;
        }
        resolvedDeployment = DeploymentResult.getKeycloakDeployment();

        return getLogoutUri(deploymentResult.getRequest());
    }

    public KeycloakDeployment getResolvedDeployment() {
        return resolvedDeployment;
    }

    protected String getLogoutUri(Request request) {
        String redirectTo = VirtualHostHelper.getBaseURL(request) + LoginScreenHelper.getStartupPagePath();
        return resolvedDeployment.getLogoutUrl()
                                 .queryParam(POST_LOGOUT_REDIRECT_URI_PARAM, redirectTo)
                                 .queryParam(ID_TOKEN_HINT_PARAM, getIdTokenHint())
                                 .build()
                                 .toString();
    }

    protected String getIdTokenHint() {
        var secret = (String) resolvedDeployment.getResourceCredentials().get(RESOURCE_SECRET_KEY);
        var post = new HttpPost(resolvedDeployment.getTokenUrl());
        var params = List.of( //
                new BasicNameValuePair(CLIENT_ID_PARAM, resolvedDeployment.getResourceName()),
                new BasicNameValuePair(CLIENT_SECRET_PARAM, secret),
                new BasicNameValuePair(GRANT_TYPE_PARAM, GRANT_TYPE_CLIENT_CREDENTIALS),
                new BasicNameValuePair(SCOPE_PARAM, TOKEN_SCOPE_OPENID));

        try (CloseableHttpClient httpClient = buildHttpClient()) {
            post.setEntity(new UrlEncodedFormEntity(params));
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                var jsonToken = mapper.readValue(response.getEntity().getContent(), KeycloakToken.class);
                return jsonToken.idToken();
            }
        } catch (IOException e) {
            log.error("Error while fetching Keycloak id token hint: {}", e::getMessage);
            log.debug(e, e);
            return "";
        }
    }

    protected CloseableHttpClient buildHttpClient() {
        var builder = HttpClientBuilder.create();
        configureProxy(builder);
        return builder.build();
    }

    protected void configureProxy(HttpClientBuilder builder) {
        var proxyHost = Framework.getProperty(Environment.NUXEO_HTTP_PROXY_HOST);
        var proxyPort = Framework.getProperty(Environment.NUXEO_HTTP_PROXY_PORT);
        var proxyLogin = Framework.getProperty(Environment.NUXEO_HTTP_PROXY_LOGIN);
        var proxyPassword = Framework.getProperty(Environment.NUXEO_HTTP_PROXY_PASSWORD);

        if (isNotBlank(proxyHost) && isNotBlank(proxyPort)) {
            var proxy = new HttpHost(proxyHost, Integer.parseInt(proxyPort));
            builder.setRoutePlanner(new DefaultProxyRoutePlanner(proxy));

            // proxyPassword could be blank
            if (isNotBlank(proxyLogin) && proxyPassword != null) {
                var credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(new AuthScope(proxy),
                        new UsernamePasswordCredentials(proxyLogin, proxyPassword));
                builder.setDefaultCredentialsProvider(credentialsProvider);
            }
        }
    }
}
