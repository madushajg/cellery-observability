/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.cellery.observability.auth;

import io.cellery.observability.auth.exception.AuthProviderException;
import io.cellery.observability.auth.internal.AuthConfig;
import io.cellery.observability.auth.internal.ServiceHolder;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.HttpClient;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.wso2.carbon.config.ConfigurationException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Auth related utilities.
 */
public class AuthUtils {
    private static final String BASIC_AUTH_PREFIX = "Basic ";

    /**
     * Get a HTTP Client which bypasses the SSL checks.
     *
     * @return HTTP Client which bypasses SSL validations
     * @throws KeyManagementException this will be thrown if an issue occurs while executing this method
     * @throws NoSuchAlgorithmException this will be thrown if an issue occurs while executing this method
     */
    public static HttpClient getTrustAllClient() throws KeyManagementException, NoSuchAlgorithmException {
        TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                // Do Nothing
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                // Do Nothing
            }
        }};
        SSLContext context = SSLContext.getInstance("SSL");
        context.init(null, trustManagers, null);
        SSLConnectionSocketFactory sslConnectionFactory =
                new SSLConnectionSocketFactory(context, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

        PlainConnectionSocketFactory plainConnectionSocketFactory = new PlainConnectionSocketFactory();

        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslConnectionFactory)
                .register("http", plainConnectionSocketFactory)
                .build();
        HttpClientConnectionManager ccm = new BasicHttpClientConnectionManager(registry);

        return HttpClientBuilder.create()
                .setSSLSocketFactory(sslConnectionFactory)
                .setConnectionManager(ccm)
                .build();
    }

    /**
     * Disable SSL verification for the default URL connection.
     */
    public static void disableSSLVerification() throws KeyManagementException, NoSuchAlgorithmException {
        TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                // Do Nothing
            }

            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                // Do Nothing
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((string, sslSession) -> true);
    }

    /**
     * Exchange auth code for tokens.
     *
     * @param authCode The auth code to exchange tokens for
     * @return The OAuth access token response received
     * @throws AuthProviderException if Cellery auth failure occurs
     * @throws ConfigurationException if fetching configuration fails
     * @throws OAuthSystemException if OAuth exchange fails
     * @throws OAuthProblemException if fetching access token fails
     */
    public static OAuthAccessTokenResponse exchangeAuthCode(String authCode)
            throws AuthProviderException, ConfigurationException, OAuthSystemException, OAuthProblemException {
        OAuthClientRequest oAuthClientRequest = OAuthClientRequest
                .tokenLocation(AuthConfig.getInstance().getIdpUrl()
                        + AuthConfig.getInstance().getIdpOidcTokenEndpoint())
                .setGrantType(GrantType.AUTHORIZATION_CODE)
                .setClientId(ServiceHolder.getDcrProvider().getClientId())
                .setClientSecret(ServiceHolder.getDcrProvider().getClientSecret())
                .setRedirectURI(AuthConfig.getInstance().getPortalHomeUrl())
                .setCode(authCode).buildBodyMessage();

        OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
        return oAuthClient.accessToken(oAuthClientRequest);
    }

    /**
     * Get the Base64 encoded IdP Admin Credentials.
     *
     * @param username username
     * @param password password
     * @return Basic auth header value for the provided username and password
     */
    public static String generateBasicAuthHeaderValue(String username, String password) {
        String authString = username + ":" + password;
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes(Charset.forName(StandardCharsets.UTF_8.name())));
        return BASIC_AUTH_PREFIX + new String(authEncBytes, Charset.forName(StandardCharsets.UTF_8.name()));
    }

    private AuthUtils() {   // Prevent initialization
    }
}
