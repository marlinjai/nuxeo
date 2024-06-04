/*
 * (C) Copyright 2017-2022 Nuxeo (http://nuxeo.com/) and others.
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
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.ftest.server;

import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.platform.oauth2.Constants.AUTHORIZATION_CODE_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.CLIENT_ID_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.CODE_CHALLENGE_METHODS_SUPPORTED;
import static org.nuxeo.ecm.platform.oauth2.Constants.CODE_CHALLENGE_METHOD_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.CODE_CHALLENGE_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.CODE_RESPONSE_TYPE;
import static org.nuxeo.ecm.platform.oauth2.Constants.REDIRECT_URI_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.RESPONSE_TYPE_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.STATE_PARAM;
import static org.nuxeo.ecm.platform.oauth2.NuxeoOAuth2Servlet.ENDPOINT_AUTH_SUBMIT;
import static org.nuxeo.ecm.platform.oauth2.NuxeoOAuth2Servlet.ENDPOINT_TOKEN;
import static org.nuxeo.ecm.platform.oauth2.NuxeoOAuth2Servlet.ERROR_DESCRIPTION_PARAM;
import static org.nuxeo.ecm.platform.oauth2.NuxeoOAuth2Servlet.ERROR_PARAM;
import static org.nuxeo.ecm.platform.oauth2.OAuth2Error.ACCESS_DENIED;
import static org.nuxeo.ecm.platform.oauth2.clients.OAuth2ClientService.OAUTH2CLIENT_DIRECTORY_NAME;
import static org.nuxeo.ecm.platform.oauth2.request.AuthorizationRequest.MISSING_REQUIRED_FIELD_MESSAGE;
import static org.nuxeo.ecm.platform.oauth2.tokens.OAuth2TokenStore.DIRECTORY_NAME;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.nuxeo.common.utils.URIUtils;
import org.nuxeo.ecm.platform.oauth2.NuxeoOAuth2Servlet;
import org.nuxeo.functionaltests.AbstractTest;
import org.nuxeo.functionaltests.RestTestRule;
import org.nuxeo.functionaltests.pages.LoginPage;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;

/**
 * Tests the OAuth2 authorization flow handled by the {@link NuxeoOAuth2Servlet}.
 *
 * @since 9.2
 */
public class ITOAuth2Test extends AbstractTest {

    public static class OAuth2Token {

        public final String accessToken;

        public final String refreshToken;

        public OAuth2Token(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    public static final String DOC_PATH = "/api/v1/path/";

    public static final String JSON_CMIS_PATH = "/json/cmis";

    public static final String ATOM_CMIS_PATH = "/atom/cmis";

    public static final String ATOM_CMIS10_PATH = "/atom/cmis10";

    protected static String oauth2ClientDirectoryEntryId;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.builder().build();

    @Rule
    public final RestTestRule restHelper = new RestTestRule();

    @Before
    public void before() {
        restHelper.createUser(TEST_USERNAME, TEST_PASSWORD);
        // Create a test OAuth2 client redirecting to localhost
        Map<String, String> properties = new HashMap<>();
        properties.put("name", "Test Client");
        properties.put("clientId", "test-client");
        properties.put("redirectURIs", "http://localhost:8080/nuxeo/home.html");
        oauth2ClientDirectoryEntryId = restHelper.createDirectoryEntry(OAUTH2CLIENT_DIRECTORY_NAME, properties);
    }

    @After
    public void after() {
        restHelper.deleteDirectoryEntries(DIRECTORY_NAME);
        logoutSimply();
    }

    @Test
    public void testAuthorizationErrors() {
        LoginPage loginPage = getLoginPage();
        loginPage.login(TEST_USERNAME, TEST_PASSWORD);

        // No client_id parameter
        OAuth2ErrorPage errorPage = getOAuth2ErrorPage("/oauth2/authorize");
        assertTrue(driver.getTitle().endsWith("400"));
        errorPage.checkTitle("Bad Request");
        errorPage.checkDescription(String.format(MISSING_REQUIRED_FIELD_MESSAGE, CLIENT_ID_PARAM));

        // No response_type parameter
        errorPage = getOAuth2ErrorPage("/oauth2/authorize?client_id=test-client");
        errorPage.checkDescription(String.format(MISSING_REQUIRED_FIELD_MESSAGE, RESPONSE_TYPE_PARAM));

        // Invalid response_type parameter
        errorPage = getOAuth2ErrorPage("/oauth2/authorize?client_id=test-client&response_type=unknown");
        errorPage.checkDescription(String.format("Unknown %s: got \"unknown\", expecting \"%s\".", RESPONSE_TYPE_PARAM,
                CODE_RESPONSE_TYPE));

        // Invalid client_id parameter
        errorPage = getOAuth2ErrorPage("/oauth2/authorize?client_id=unknown&response_type=code");
        errorPage.checkDescription(String.format("Invalid %s: unknown.", CLIENT_ID_PARAM));

        // Invalid redirect_uri parameter
        errorPage = getOAuth2ErrorPage(
                "/oauth2/authorize?client_id=test-client&response_type=code&redirect_uri=unknown");
        errorPage.checkDescription(String.format(
                "Invalid %s parameter: unknown. It must exactly match one of the redirect URIs configured for the app.",
                REDIRECT_URI_PARAM));

        // Invalid PKCE parameters
        errorPage = getOAuth2ErrorPage(
                "/oauth2/authorize?client_id=test-client&response_type=code&code_challenge=myCodeChallenge");
        errorPage.checkDescription(
                String.format("Invalid PKCE parameters: either both %s and %s parameters must be sent or none of them.",
                        CODE_CHALLENGE_PARAM, CODE_CHALLENGE_METHOD_PARAM));
        errorPage = getOAuth2ErrorPage(
                "/oauth2/authorize?client_id=test-client&response_type=code&code_challenge_method=S256");
        errorPage.checkDescription(
                String.format("Invalid PKCE parameters: either both %s and %s parameters must be sent or none of them.",
                        CODE_CHALLENGE_PARAM, CODE_CHALLENGE_METHOD_PARAM));
        errorPage = getOAuth2ErrorPage(
                "/oauth2/authorize?client_id=test-client&response_type=code&code_challenge=myCodeChallenge&code_challenge_method=unknown");
        errorPage.checkDescription(String.format(
                "Invalid %s parameter: transform algorithm unknown not supported. The server only supports %s.",
                CODE_CHALLENGE_METHOD_PARAM, CODE_CHALLENGE_METHODS_SUPPORTED));

        // Uncaught exception - NXP-31104
        // Create the same OAuth2 client to produce error
        Map<String, String> properties = new HashMap<>();
        properties.put("name", "Test Client");
        properties.put("clientId", "test-client");
        properties.put("redirectURIs", "http://localhost:8080/nuxeo/home.html");
        restHelper.createDirectoryEntry(OAUTH2CLIENT_DIRECTORY_NAME, properties);
        errorPage = getOAuth2ErrorPage("/oauth2/authorize?client_id=test-client&response_type=code");
        errorPage.checkDescription("More than one client registered for the 'test-client' id");
    }

    @Test
    public void testOAuth2GrantPage() {
        // The grant page is behind the authentication filter
        LoginPage loginPage = get(NUXEO_URL + "/oauth2Grant.jsp", LoginPage.class);
        OAuth2GrantPage grantPage = loginPage.login(TEST_USERNAME, TEST_PASSWORD, OAuth2GrantPage.class);
        // When called directly without going through the /oauth2/authorize endpoint the input fields are empty
        grantPage.checkClientName("null");
        grantPage.checkFieldCount(2);
        grantPage.checkResponseType("");
        grantPage.checkClientId("");

        // Get the grant page by going through the /oauth2/authorize endpoint
        // First send only the required parameters
        getOAuth2GrantPageBuilder().build();

        // Then send extra parameters
        Map<String, String> extraParameters = new HashMap<>();
        extraParameters.put("redirect_uri", "http://localhost:8080/nuxeo/home.html");
        extraParameters.put("state", "1234");
        extraParameters.put("code_challenge", "myCodeChallenge");
        extraParameters.put("code_challenge_method", "plain");
        getOAuth2GrantPageBuilder().setExtraParameters(extraParameters).build();
    }

    @Test
    public void testAuthorizationSubmitErrors() {
        LoginPage loginPage = getLoginPage();
        loginPage.login(TEST_USERNAME, TEST_PASSWORD);

        // Call a GET request on /oauth2/authorize_submit
        OAuth2ErrorPage errorPage = get(NUXEO_URL + "/oauth2/authorize_submit", OAuth2ErrorPage.class);
        errorPage.checkDescription(
                String.format("The /oauth2/%s endpoint only accepts POST requests.", ENDPOINT_AUTH_SUBMIT));

        // Simulate an empty client_id parameter
        OAuth2GrantPage grantPage = getOAuth2GrantPageBuilder().build();
        grantPage.setFieldValue("client_id", "");
        grantPage.grant();
        errorPage = asPage(OAuth2ErrorPage.class);
        errorPage.checkDescription(String.format(MISSING_REQUIRED_FIELD_MESSAGE, CLIENT_ID_PARAM));

        // Simulate an empty response_type parameter
        grantPage = getOAuth2GrantPageBuilder().build();
        grantPage.setFieldValue("response_type", "");
        grantPage.grant();
        errorPage = asPage(OAuth2ErrorPage.class);
        errorPage.checkDescription(String.format(MISSING_REQUIRED_FIELD_MESSAGE, RESPONSE_TYPE_PARAM));

        // Simulate an invalid response_type parameter
        grantPage = getOAuth2GrantPageBuilder().build();
        grantPage.setFieldValue("response_type", "unknown");
        grantPage.grant();
        errorPage = asPage(OAuth2ErrorPage.class);
        errorPage.checkDescription(String.format("Unknown %s: got \"unknown\", expecting \"%s\".", RESPONSE_TYPE_PARAM,
                CODE_RESPONSE_TYPE));

        // Simulate an invalid client_id parameter
        grantPage = getOAuth2GrantPageBuilder().build();
        grantPage.setFieldValue("client_id", "unknown");
        grantPage.grant();
        errorPage = asPage(OAuth2ErrorPage.class);
        errorPage.checkDescription(String.format("Invalid %s: unknown.", CLIENT_ID_PARAM));

        // Simulate an invalid redirect_uri parameter
        Map<String, String> extraParameters = new HashMap<>();
        extraParameters.put("redirect_uri", "http://localhost:8080/nuxeo/home.html");
        grantPage = getOAuth2GrantPageBuilder().setExtraParameters(extraParameters).build();
        grantPage.setFieldValue("redirect_uri", "unknown");
        grantPage.grant();
        errorPage = asPage(OAuth2ErrorPage.class);
        errorPage.checkDescription(String.format(
                "Invalid %s parameter: unknown. It must exactly match one of the redirect URIs configured for the app.",
                REDIRECT_URI_PARAM));

        // Simulate invalid PKCE parameters
        extraParameters.put("code_challenge", "myCodeChallenge");
        extraParameters.put("code_challenge_method", "S256");
        grantPage = getOAuth2GrantPageBuilder().setExtraParameters(extraParameters).build();
        grantPage.removeField("code_challenge_method");
        grantPage.grant();
        errorPage = asPage(OAuth2ErrorPage.class);
        errorPage.checkDescription(
                String.format("Invalid PKCE parameters: either both %s and %s parameters must be sent or none of them.",
                        CODE_CHALLENGE_PARAM, CODE_CHALLENGE_METHOD_PARAM));

        grantPage = getOAuth2GrantPageBuilder().setExtraParameters(extraParameters).build();
        grantPage.removeField("code_challenge");
        grantPage.grant();
        errorPage = asPage(OAuth2ErrorPage.class);
        errorPage.checkDescription(
                String.format("Invalid PKCE parameters: either both %s and %s parameters must be sent or none of them.",
                        CODE_CHALLENGE_PARAM, CODE_CHALLENGE_METHOD_PARAM));

        grantPage = getOAuth2GrantPageBuilder().setExtraParameters(extraParameters).build();
        grantPage.setFieldValue("code_challenge_method", "unknown");
        grantPage.grant();
        errorPage = asPage(OAuth2ErrorPage.class);
        errorPage.checkDescription(String.format(
                "Invalid %s parameter: transform algorithm unknown not supported. The server only supports %s.",
                CODE_CHALLENGE_METHOD_PARAM, CODE_CHALLENGE_METHODS_SUPPORTED));
    }

    @Test
    public void testAuthorizationDenied() {
        LoginPage loginPage = getLoginPage();
        loginPage.login(TEST_USERNAME, TEST_PASSWORD);

        OAuth2GrantPage grantPage = getOAuth2GrantPageBuilder().setExtraParameters(
                Collections.singletonMap("state", "1234")).build();
        grantPage.deny();
        String currentURL = driver.getCurrentUrl();
        assertEquals("http://localhost:8080/nuxeo/home.html", URIUtils.getURIPath(currentURL));
        Map<String, String> expectedParameters = new HashMap<>();
        expectedParameters.put(ERROR_PARAM, ACCESS_DENIED);
        expectedParameters.put(ERROR_DESCRIPTION_PARAM, "Access denied by the user");
        expectedParameters.put(STATE_PARAM, "1234");
        assertEquals(expectedParameters, URIUtils.getRequestParameters(currentURL));
    }

    @Test
    public void testAuthorizationGranted() {
        LoginPage loginPage = getLoginPage();
        loginPage.login(TEST_USERNAME, TEST_PASSWORD);

        OAuth2GrantPage grantPage = getOAuth2GrantPageBuilder().setExtraParameters(
                Collections.singletonMap("state", "1234")).build();
        grantPage.grant();
        String currentURL = driver.getCurrentUrl();
        assertEquals("http://localhost:8080/nuxeo/home.html", URIUtils.getURIPath(currentURL));
        Map<String, String> parameters = URIUtils.getRequestParameters(currentURL);
        assertEquals(2, parameters.size());
        assertEquals("1234", parameters.get(STATE_PARAM));
        assertTrue(parameters.containsKey(AUTHORIZATION_CODE_PARAM));
    }

    @Test
    public void testAuthorizationOnRestAPI() {
        OAuth2Token token = getOAuth2Token("Administrator", "Administrator");
        logoutSimply();

        checkAuthorizationWithValidAccessToken(DOC_PATH, token.accessToken);

        // refresh the access token
        OAuth2Token refreshedToken = refreshOAuth2Token(token.refreshToken);

        checkAuthorizationWithInvalidAccessToken(DOC_PATH, token.accessToken);

        checkAuthorizationWithValidAccessToken(DOC_PATH, refreshedToken.accessToken);
    }

    @Test
    public void testAuthorizationOnCMIS() {
        OAuth2Token token = getOAuth2Token("Administrator", "Administrator");

        checkAuthorizationWithValidAccessToken(JSON_CMIS_PATH, token.accessToken);
        checkAuthorizationWithValidAccessToken(ATOM_CMIS_PATH, token.accessToken);
        checkAuthorizationWithValidAccessToken(ATOM_CMIS10_PATH, token.accessToken);

        // refresh the access token
        OAuth2Token refreshedToken = refreshOAuth2Token(token.refreshToken);

        checkAuthorizationWithInvalidAccessToken(JSON_CMIS_PATH, token.accessToken);
        checkAuthorizationWithInvalidAccessToken(ATOM_CMIS_PATH, token.accessToken);
        checkAuthorizationWithInvalidAccessToken(ATOM_CMIS10_PATH, token.accessToken);

        checkAuthorizationWithValidAccessToken(JSON_CMIS_PATH, refreshedToken.accessToken);
        checkAuthorizationWithValidAccessToken(ATOM_CMIS_PATH, refreshedToken.accessToken);
        checkAuthorizationWithValidAccessToken(ATOM_CMIS10_PATH, refreshedToken.accessToken);
    }

    @Test
    public void testTokenGetRequest() {
        // Call a GET request on /oauth2/token
        OAuth2ErrorPage errorPage = get(NUXEO_URL + "/oauth2/token", OAuth2ErrorPage.class);
        errorPage.checkDescription(
                String.format("The /oauth2/%s endpoint only accepts POST requests.", ENDPOINT_TOKEN));
    }

    @Test
    public void testAuthorizationWithExistingToken() {
        // Get an OAuth2 token
        OAuth2Token initialToken = getOAuth2Token(TEST_USERNAME, TEST_PASSWORD);
        logoutSimply();

        // Ask for authorization
        String url = NUXEO_URL + "/oauth2/authorize?client_id=test-client&response_type=code";
        LoginPage loginPage = get(url, LoginPage.class);
        loginPage.login(TEST_USERNAME, TEST_PASSWORD);

        // Expecting to be redirected to the client's redirect_uri with a code parameter, bypassing the grant page
        String currentURL = driver.getCurrentUrl();
        assertEquals("http://localhost:8080/nuxeo/home.html", URIUtils.getURIPath(currentURL));
        Map<String, String> queryParameters = URIUtils.getRequestParameters(currentURL);
        assertEquals(1, queryParameters.size());
        String code = queryParameters.get("code");
        assertNotNull(code);

        // Ask for a token, expecting the initial access token
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("client_id", "test-client");
        params.put("code", code);
        OAuth2Token token = getOAuth2Token(params);
        assertEquals(initialToken.accessToken, token.accessToken);
    }

    @Test
    public void testAuthorizationWithAutoGrant() {
        // Set auto-grant on the client
        setAutoGrant(true);

        // Ask for authorization
        String url = NUXEO_URL + "/oauth2/authorize?client_id=test-client&response_type=code";
        LoginPage loginPage = get(url, LoginPage.class);
        loginPage.login(TEST_USERNAME, TEST_PASSWORD);

        // Expecting to be redirected to the client's redirect_uri with a code parameter, bypassing the grant page
        String currentURL = driver.getCurrentUrl();
        assertEquals("http://localhost:8080/nuxeo/home.html", URIUtils.getURIPath(currentURL));
        Map<String, String> queryParameters = URIUtils.getRequestParameters(currentURL);
        assertEquals(1, queryParameters.size());
        String code = queryParameters.get("code");
        assertNotNull(code);

        // Reset auto-grant on the client
        setAutoGrant(false);
    }

    protected void setAutoGrant(boolean autoGrant) {
        restHelper.updateDirectoryEntry(OAUTH2CLIENT_DIRECTORY_NAME, oauth2ClientDirectoryEntryId,
                Collections.singletonMap("autoGrant", autoGrant));
    }

    protected OAuth2Token getOAuth2Token(String username, String password) {
        LoginPage loginPage = getLoginPage();
        loginPage.login(username, password);

        OAuth2GrantPage grantPage = getOAuth2GrantPageBuilder().build();
        grantPage.grant();
        String currentURL = driver.getCurrentUrl();
        Map<String, String> parameters = URIUtils.getRequestParameters(currentURL);
        String code = parameters.get(AUTHORIZATION_CODE_PARAM);

        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("client_id", "test-client");
        params.put("code", code);
        return getOAuth2Token(params);
    }

    protected OAuth2Token getOAuth2Token(Map<String, String> params) {
        return httpClient.buildPostRequest("/oauth2/" + ENDPOINT_TOKEN)
                         .entity(params)
                         .executeAndThen(new JsonNodeHandler(),
                                 node -> new OAuth2Token(node.get("access_token").textValue(),
                                         node.get("refresh_token").textValue()));
    }

    protected OAuth2Token refreshOAuth2Token(String refreshToken) {
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("client_id", "test-client");
        params.put("refresh_token", refreshToken);
        return getOAuth2Token(params);
    }

    protected void checkAuthorizationWithValidAccessToken(String path, String accessToken) {
        httpClient.buildGetRequest(path)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_UNAUTHORIZED, status.intValue()));

        httpClient.buildGetRequest(path)
                  .addQueryParameter("access_token", accessToken)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        httpClient.buildGetRequest(path)
                  .addHeader("Authorization", "Bearer " + accessToken)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    protected void checkAuthorizationWithInvalidAccessToken(String path, String accessToken) {
        httpClient.buildGetRequest(path)
                  .addQueryParameter("access_token", accessToken)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_UNAUTHORIZED, status.intValue()));
        httpClient.buildGetRequest(path)
                  .addHeader("Authorization", "Bearer " + accessToken)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_UNAUTHORIZED, status.intValue()));
    }

    protected OAuth2ErrorPage getOAuth2ErrorPage(String resource) {
        return get(NUXEO_URL + resource, OAuth2ErrorPage.class);
    }

    protected OAuth2GrantPageBuilder getOAuth2GrantPageBuilder() {
        return new OAuth2GrantPageBuilder();
    }

    public static class OAuth2GrantPageBuilder {

        protected Map<String, String> extraParameters;

        protected OAuth2GrantPageBuilder() {
        }

        public OAuth2GrantPageBuilder setExtraParameters(final Map<String, String> extraParameters) {
            this.extraParameters = extraParameters;
            return this;
        }

        public OAuth2GrantPage build() {
            OAuth2GrantPage grantPage;
            String url = NUXEO_URL + "/oauth2/authorize?client_id=test-client&response_type=code";
            if (extraParameters != null) {
                url = URIUtils.addParametersToURIQuery(url, extraParameters);
            }
            grantPage = get(url, OAuth2GrantPage.class);
            grantPage.checkClientName("Test Client");
            grantPage.checkResponseType("code");
            grantPage.checkClientId("test-client");
            int fieldCount = 2;
            if (extraParameters != null) {
                extraParameters.forEach(grantPage::checkExtraParameter);
                fieldCount += extraParameters.size();
            }
            grantPage.checkFieldCount(fieldCount);
            return grantPage;
        }

    }

}
