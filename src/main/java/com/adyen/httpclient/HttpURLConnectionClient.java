/*
 *                       ######
 *                       ######
 * ############    ####( ######  #####. ######  ############   ############
 * #############  #####( ######  #####. ######  #############  #############
 *        ######  #####( ######  #####. ######  #####  ######  #####  ######
 * ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 * ###### ######  #####( ######  #####. ######  #####          #####  ######
 * #############  #############  #############  #############  #####  ######
 *  ############   ############  #############   ############  #####  ######
 *                                      ######
 *                               #############
 *                               ############
 *
 * Adyen Java API Library
 *
 * Copyright (c) 2017 Adyen B.V.
 * This file is open source and available under the MIT license.
 * See the LICENSE file for more info.
 */
package com.adyen.httpclient;

import com.adyen.Client;
import com.adyen.Config;
import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Scanner;

public class HttpURLConnectionClient implements ClientInterface {
    private static final String CHARSET = "UTF-8";
    private Proxy proxy;

    /**
     * Does a POST request.
     * config is used to obtain basic auth username, password and User-Agent
     */
    /**
     * Does a POST request.
     * config is used to obtain basic auth username, password and User-Agent
     */
    @Override
    public String request(String requestUrl, String requestBody, Config config) throws IOException, HTTPClientException {
        return request(requestUrl, requestBody, config, false);
    }

    @Override
    public String request(String requestUrl, String requestBody, Config config, boolean isApiKeyRequired) throws IOException, HTTPClientException {
        HttpURLConnection httpConnection = createRequest(requestUrl, config.getApplicationName());
        String apiKey = config.getApiKey();
        int connectionTimeoutMillis = config.getConnectionTimeoutMillis();
        // Use Api key if required or if provided
        if (isApiKeyRequired || (apiKey != null && !apiKey.isEmpty())) {
            setApiKey(httpConnection, apiKey);
        } else {
            setBasicAuthentication(httpConnection, config.getUsername(), config.getPassword());
        }

        httpConnection.setConnectTimeout(connectionTimeoutMillis);
        setContentType(httpConnection, "application/json");

        return doPostRequest(httpConnection, requestBody);
    }

    private static String getResponseBody(InputStream responseStream) throws IOException {
        //\A is the beginning of the stream boundary
        Scanner scanner = new Scanner(responseStream, CHARSET);
        String rBody = scanner.useDelimiter("\\A").next();
        scanner.close();
        responseStream.close();
        return rBody;
    }

    /**
     * Does a POST request with HTTP key-value pairs
     */
    @Override
    public String post(String requestUrl, Map<String, String> params, Config config) throws IOException, HTTPClientException {
        String postQuery = getQuery(params);
        HttpURLConnection httpConnection = createRequest(requestUrl, config.getApplicationName());
        String response = doPostRequest(httpConnection, postQuery);

        return response;
    }

    /**
     * Get HTTP querystring from Map<String,String>
     */
    private String getQuery(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> pair : params.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }

            result.append(URLEncoder.encode(pair.getKey(), CHARSET));
            result.append("=");
            result.append(URLEncoder.encode(pair.getValue(), CHARSET));
        }

        return result.toString();
    }

    /**
     * Initialize the httpConnection
     */
    private HttpURLConnection createRequest(String requestUrl, String applicationName) throws IOException {
        URL targetUrl = new URL(requestUrl);
        HttpURLConnection httpConnection;

        // Set proxy if configured
        if (proxy != null) {
            httpConnection = (HttpURLConnection) targetUrl.openConnection(proxy);
        } else {
            httpConnection = (HttpURLConnection) targetUrl.openConnection();
        }
        httpConnection.setUseCaches(false);
        httpConnection.setDoOutput(true);
        httpConnection.setRequestMethod("POST");

        httpConnection.setRequestProperty("Accept-Charset", CHARSET);
        httpConnection.setRequestProperty("User-Agent", String.format("%s %s%s", applicationName, Client.USER_AGENT_SUFFIX, Client.LIB_VERSION));

        return httpConnection;
    }

    /**
     * Adds Basic Authentication headers
     */
    private HttpURLConnection setBasicAuthentication(HttpURLConnection httpConnection, String username, String password) {
        // set basic authentication
        String authString = username + ":" + password;
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);

        httpConnection.setRequestProperty("Authorization", "Basic " + authStringEnc);
        return httpConnection;
    }

    /**
     * Sets content type
     */
    private HttpURLConnection setContentType(HttpURLConnection httpConnection, String contentType) {
        httpConnection.setRequestProperty("Content-Type", contentType);
        return httpConnection;
    }

    /**
     * Sets api key
     */
    private HttpURLConnection setApiKey(HttpURLConnection httpConnection, String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            httpConnection.setRequestProperty("x-api-key", apiKey);
        }
        return httpConnection;
    }

    /**
     * Does a POST request with raw body
     */
    private String doPostRequest(HttpURLConnection httpConnection, String requestBody) throws IOException, HTTPClientException {
        String response = null;

        OutputStream outputStream = httpConnection.getOutputStream();
        outputStream.write(requestBody.getBytes());
        outputStream.flush();

        int responseCode = httpConnection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            //Read the response from the error stream
            if (httpConnection.getErrorStream() != null) {
                response = getResponseBody(httpConnection.getErrorStream());
            }

            HTTPClientException httpClientException = new HTTPClientException(responseCode, "HTTP Exception", httpConnection.getHeaderFields(), response);

            throw httpClientException;
        }

        //InputStream is only available on successful requests >= 200 <400
        response = getResponseBody(httpConnection.getInputStream());

        // close the connection
        httpConnection.disconnect();

        return response;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }
}
