/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.honeywell.internal.honeywell;

import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.honeywell.internal.config.HoneywellBridgeConfig;
import org.openhab.binding.honeywell.internal.config.HoneywellResourceType;

/**
 * The {@link HoneywellConnectionClass} links to Honeywell API
 *
 * @author Anthony Sepa - Initial contribution
 */

@NonNullByDefault
public class HoneywellConnectionClass implements HoneywellConnectionInterface {
    private final HttpClient secureClient;

    private final static String HONEYWELL_TOKEN_URL = HONEYWELL_API + "oauth2/token";

    // BrdigeConfig items
    private final int timeout;
    private final String consumerKey;
    private final String consumerSecret;

    private final HashMap<HoneywellCacheProcessor, String> cacheConsumers = new HashMap<>(6);
    private final HashMap<String, String> cachedData = new HashMap<>(2);

    private String authorizationCode = "";
    private String refreshToken = "";
    private String accessToken = "";
    private Date updateAfter = new Date();

    public HoneywellConnectionClass(HoneywellBridgeConfig bridgeConfig, HttpClient secureClient) {
        this.secureClient = secureClient;
        timeout = bridgeConfig.timeout;
        consumerKey = bridgeConfig.consumerKey;
        consumerSecret = bridgeConfig.consumerSecret;
    }

    // Setup the refreshToken or authorizationCode
    @Override
    public void resetAccessToken(String authorizationCode, String refreshToken) {
        this.authorizationCode = authorizationCode;
        this.refreshToken = refreshToken;
        this.accessToken = "";
        this.updateAfter = new Date();
    }

    // Add the cache processor and prime the cache
    @Override
    public void addProcessCache(HoneywellCacheProcessor cacheProcessor, String honeywellUrl) {
        logger.debug("Registering cache processor.");
        logger.trace("Url {}", honeywellUrl);
        if (!cacheConsumers.containsKey(cacheProcessor)) {
            cacheConsumers.put(cacheProcessor, honeywellUrl);
        }
        if (!cachedData.containsKey(honeywellUrl)) {
            try {
                cachedData.put(honeywellUrl, getFromHoneywell(honeywellUrl));
            } catch (Exception e) {
                cacheConsumers.remove(cacheProcessor);
                cachedData.remove(honeywellUrl);
                throw e;
            }
        }
    }

    // Remove the cache processor and cache if last processor
    @Override
    public void delProcessCache(HoneywellCacheProcessor cacheProcessor) {
        logger.debug("Removing cache processor");
        if (cacheConsumers.containsKey(cacheProcessor)) {
            final String honeywellUrl = cacheConsumers.get(cacheProcessor);
            logger.trace("Url {}", honeywellUrl);
            cacheConsumers.remove(cacheProcessor);
            if (!cacheConsumers.containsValue(honeywellUrl)) {
                if (cachedData.containsKey(honeywellUrl)) {
                    cachedData.remove(honeywellUrl);
                }
            }
        }
    }

    @Override
    public String honeywellUrl(HoneywellResourceType resourceType, int locationId, String deviceId) {
        switch (resourceType) {
            case DEVICES:
                return String.format(HONEYWELL_DEVICES_URL, consumerKey, locationId);
            case SCHEDULE:
                return String.format(HONEYWELL_SCHEDULE_URL, deviceId, consumerKey, locationId);
            case THERMOSTAT:
                return String.format(HONEYWELL_THERMOSTAT_URL, deviceId, consumerKey, locationId);
            case FAN:
                return String.format(HONEYWELL_FAN_URL, deviceId, consumerKey, locationId);
            case PRIORITY:
                return String.format(HONEYWELL_PRIORITY_URL, deviceId, consumerKey, locationId);
            default:
                logger.warn("Unsupported HoneywellResourceType with 3 args '{}'", resourceType);
                return "";
        }
    }

    @Override
    public String getRefreshToken() {
        return refreshToken;
    }

    @Override
    public Boolean hasAccessToken() {
        return !accessToken.isEmpty();
    }

    private void processToken(Token currToken) {
        accessToken = currToken.getAccessToken();
        updateAfter.setTime(new Date().getTime() + currToken.getUpdateIn());
        refreshToken = currToken.getRefreshToken();
        return;
    }

    private void updateAccessToken() throws IllegalStateException {
        Token currToken;

        if (!authorizationCode.isEmpty()) {
            try {
                logger.trace("Authorization code {} provided.", authorizationCode);
                currToken = new Token(postTokenHoneywell(
                        "grant_type=authorization_code&redirect_uri=none&code=" + authorizationCode));
                if (!currToken.validToken) {
                    logger.warn("Authorization code {} failed to provide token response", authorizationCode);
                    currToken = new Token(postTokenHoneywell("grant_type=refresh_token&refresh_token=" + refreshToken));
                    if (!currToken.validToken) {
                        logger.warn("Refresh token {} failed to provide token response", refreshToken);
                        throw new IllegalStateException("Unable to get a token response");
                    }
                } else {
                    authorizationCode = "";
                }

                processToken(currToken);
            } catch (Exception e) {
                logger.warn("Authorization code {} and/or Refresh token {} created an unknown error", authorizationCode,
                        refreshToken);
                throw new IllegalStateException("Token response had an unplanned exception");
            }
            authorizationCode = "";
            return;
        }

        try {
            currToken = new Token(postTokenHoneywell("grant_type=refresh_token&refresh_token=" + refreshToken));
            if (!currToken.validToken) {
                logger.warn("Refresh token {} failed to provide token response", refreshToken);
                throw new IllegalStateException("Unable to get a token response");
            }
            processToken(currToken);
        } catch (Exception e) {
            logger.warn("Refresh token {} response had an unknown error", refreshToken);
            throw new IllegalStateException("Token response had an unplanned exception");
        }
    }

    // Bridge handler schedules the job that runs the cache refresh periodically
    @Override
    public String refreshCache() {
        logger.debug("Refreshing the caches");
        if (updateAfter.before(new Date()) || !authorizationCode.isEmpty()) {
            logger.debug("Updating access token");
            try {
                updateAccessToken();
            } catch (Exception e) {
                logger.warn("Unhandled error while updating access token: {}", e.getMessage());
                return "Failed to update access token (authorizationCode: " + authorizationCode + ", refreshToken: "
                        + refreshToken + ")";
            }
        }

        try {
            for (Map.Entry<String, String> cacheUpdate : cachedData.entrySet()) {
                final String url = cacheUpdate.getKey();
                logger.trace("Refresh for {}", url);
                cachedData.put(url, getFromHoneywell(url));
            }

            cacheConsumers.forEach((consumer, url) -> {
                logger.trace("Sending out cache to {}", consumer);
                consumer.processCache();
            });
        } catch (Exception e) {
            logger.warn("Unhandled error while updating cache: {}", e.getMessage());
            return "Unhandled error while updating cache: " + e.getMessage();
        }
        return "";
    }

    @Override
    public String getOnDemand(String honeywellUrl) {
        return getFromHoneywell(honeywellUrl);
    }

    // Pulls from cached information
    @Override
    public String getCached(String honeywellUrl) {
        final String result = cachedData.get(honeywellUrl);
        return result != null ? result : "{}";
    }

    // Gets thermostat discovery information
    @Override
    public String getThermostatDiscoveryInfo() {
        return getFromHoneywell(String.format(HONEYWELL_LOCATIONS_URL, consumerKey));
    }

    // Gets sensor discovery information
    @Override
    public String getSensorDiscoveryInfo(int locationId, String thermostatId) {
        return getFromHoneywell(honeywellUrl(HoneywellResourceType.PRIORITY, locationId, thermostatId));
    }

    // Gets information from the server
    private String getFromHoneywell(String honeywellUrl) {
        URI uri;
        try {
            uri = uriFromString(honeywellUrl);
            logger.trace("Requesting get refresh from '{}'", uri);
            return getFromHoneywell(uri, false);
        } catch (IllegalArgumentException | URISyntaxException | MalformedURLException e) {
            logger.warn("Creating http get request failed: {}", e.getMessage());
        }
        return "{}";
    }

    private String getFromHoneywell(URI uri, boolean isRetry) throws IllegalStateException {
        try {
            Request request = secureClient.newRequest(uri).method(HttpMethod.GET);
            addAuthorization(request, false);
            addContentType(request, false);
            addCommonItems(request);
            logger.trace("Sending to '{}': {}", uri, requestToLogString(request));
            try {
                ContentResponse response = request.send();
                switch (response.getStatus()) {
                    case HttpStatus.OK_200:
                        return response.getContentAsString();
                    case HttpStatus.UNAUTHORIZED_401:
                        logger.debug("Requesting '{}' (method='GET') failed: Authorization error", uri);
                        if (!isRetry) {
                            logger.warn("Unuathorized: Updating access token and retrying");
                            updateAccessToken();
                            return getFromHoneywell(uri, true);
                        }
                        logger.warn("Authentication failure after access token refresh, failing here");
                        throw new IllegalStateException("Authorization failed, check credentials");
                    case HttpStatus.TOO_MANY_REQUESTS_429:
                        logger.debug("Requesting '{}' (method='GET') failed: Too many requests", uri);
                        logger.warn("Too many requests, failing here");
                        throw new IllegalStateException("Too many requests, reduce refresh");
                    case HttpStatus.BAD_REQUEST_400:
                        logger.debug("Requesting '{}' (method='GET') failed: Bad Request", uri);
                        logger.warn("Bad request, failing here");
                        throw new IllegalStateException("Configuration is incorrect");
                    default:
                        logger.warn("Requesting '{}' (method='{}', content='{}') failed: {} {}", request.getURI(),
                                request.getMethod(), request.getContent(), response.getStatus(), response.getReason());
                        break;
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                logger.debug("Requesting '{}' (method='GET') failed: {}", uri, e.getMessage());
                if (!isRetry) {
                    logger.warn("{}: Retrying", e.getMessage());
                    // Give it a second and the retry once
                    Thread.sleep(1000);
                    return getFromHoneywell(uri, true);
                }
                logger.warn("{}: Requesting '{}' (method='GET'), failing here", e.getMessage(), uri);
                throw new IllegalStateException("Conection refused twice in a row, check network");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Request to URL {} failed: {}", uri, e.getMessage());
        }
        return "{}";
    }

    @Override
    public String postHttpHoneywell(String honeywellUrl, String stateContent) {
        return postHttpHoneywell(honeywellUrl, stateContent, false);
    }

    private String postHttpHoneywell(String honeywellUrl, String stateContent, boolean isRetry) {
        logger.trace("Url {}, Content {}", honeywellUrl, stateContent);
        URI uri;
        try {
            uri = uriFromString(honeywellUrl);
            logger.trace("Requesting post refresh from '{}'", uri);
            return postHttpHoneywell(uri, stateContent, isRetry);
        } catch (IllegalArgumentException | URISyntaxException | MalformedURLException e) {
            logger.warn("Creating http post request failed: {}", e.getMessage());
        }
        return "{}";
    }

    private String postHttpHoneywell(URI uri, String stateContent, boolean isRetry) {
        try {
            Request request = secureClient.newRequest(uri).method(HttpMethod.POST)
                    .content(new StringContentProvider(stateContent));
            addAuthorization(request, false);
            addContentType(request, true);
            addCommonItems(request);
            logger.trace("Sending to '{}': {}", uri, requestToLogString(request));
            try {
                ContentResponse response = request.send();
                switch (response.getStatus()) {
                    case HttpStatus.OK_200:
                        return response.getContentAsString();
                    case HttpStatus.UNAUTHORIZED_401:
                        logger.debug("Requesting '{}' (method='{}', content='{}') failed: Authorization error",
                                request.getURI(), request.getMethod(), request.getContent());
                        if (!isRetry) {
                            logger.warn("Authentication failure for '{}', refreshing token", uri);
                            updateAccessToken();
                            return postHttpHoneywell(uri, stateContent, true);
                        }
                        logger.warn("Authentication failure after access token refresh, failing here");
                        break;
                    case HttpStatus.TOO_MANY_REQUESTS_429:
                        logger.warn("Too many requests");
                        break;
                    case HttpStatus.BAD_REQUEST_400:
                        logger.debug("Requesting '{}' (method='GET') failed: Bad Request", uri);
                        logger.warn("Bad request, failing here");
                        break;
                    default:
                        logger.warn("Requesting '{}' (method='{}', content='{}') failed: {} {}", request.getURI(),
                                request.getMethod(), request.getContent(), response.getStatus(), response.getReason());
                        break;
                }
            } catch (Exception e) {
                logger.warn("Requesting '{}' (method='POST') exception: {}", uri, e.getMessage());
                if (!isRetry) {
                    // Give it a second and the retry once
                    Thread.sleep(1000);
                    return postHttpHoneywell(uri, stateContent, true);
                }
            }
        } catch (CancellationException e) {
            logger.debug("Request to URL {} was cancelled by thing handler.", uri);
        } catch (Exception e) {
            logger.warn("Request to URL {} failed: {}", uri, e.getMessage());
        }
        return "{}";
    }

    private String postTokenHoneywell(String stateContent) {
        try {
            final URI uri = uriFromString(HONEYWELL_TOKEN_URL);
            Request request = secureClient.newRequest(uri).method(HttpMethod.POST)
                    .content(new StringContentProvider(stateContent));
            addAuthorization(request, true);
            addContentType(request, false);
            addCommonItems(request);
            logger.trace("Sending to '{}': {}", uri, requestToLogString(request));
            try {
                ContentResponse response = request.send();
                switch (response.getStatus()) {
                    case HttpStatus.OK_200:
                        return response.getContentAsString();
                    case HttpStatus.UNAUTHORIZED_401:
                        logger.debug("Requesting '{}' (method='{}', content='{}') failed: Authorization error",
                                request.getURI(), request.getMethod(), request.getContent());
                        logger.warn("Authentication failure during token refresh {} failing here", uri);
                        break;
                    case HttpStatus.TOO_MANY_REQUESTS_429:
                        logger.warn("Too many requests");
                        break;
                    default:
                        logger.warn("Requesting '{}' (method='{}', content='{}') failed: {} {}", request.getURI(),
                                request.getMethod(), request.getContent(), response.getStatus(), response.getReason());
                        break;
                }
            } catch (Exception e) {
                logger.warn("Requesting '{}' (method='POST') exception: {}", uri, e.getMessage());
            }
        } catch (CancellationException e) {
            logger.debug("Request for token was cancelled by thing handler.");
        } catch (Exception e) {
            logger.warn("Request for token failed: {}", e.getMessage());
        }
        return "{}";
    }

    private void addCommonItems(Request request) {
        request.timeout(timeout, TimeUnit.MILLISECONDS);
        request.header("Accept", "application/json");
    }

    private void addAuthorization(Request request, boolean isBasic) {
        if (isBasic) {
            request.header("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString((consumerKey + ":" + consumerSecret).getBytes()));
        } else {
            request.header("Authorization", "Bearer " + accessToken);
        }
    }

    private void addContentType(Request request, boolean isJson) {
        if (isJson) {
            request.header("Content-Type", "application/json");
        } else {
            request.header("Content-Type", "application/x-www-form-urlencoded");
        }
    }

    /**
     * create a log string from a {@link org.eclipse.jetty.client.api.Request}
     *
     * @param request the request to log
     * @return the string representing the request
     */
    public static String requestToLogString(Request request) {
        ContentProvider contentProvider = request.getContent();
        String contentString = contentProvider == null ? "null"
                : StreamSupport.stream(contentProvider.spliterator(), false)
                        .map(b -> StandardCharsets.UTF_8.decode(b).toString()).collect(Collectors.joining(", "));
        String logString = "Method = {" + request.getMethod() + "}, Headers = {"
                + request.getHeaders().stream().map(HttpField::toString).collect(Collectors.joining(", "))
                + "}, Content = {" + contentString + "}";

        return logString;
    }

    /**
     * create an URI from a string, escaping all necessary characters
     *
     * @param s the URI as unescaped string
     * @return URI correspondign to the input string
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    public static URI uriFromString(String s) throws MalformedURLException, URISyntaxException {
        URL url = new URL(s);
        return new URI(url.getProtocol(), url.getUserInfo(), IDN.toASCII(url.getHost()), url.getPort(), url.getPath(),
                url.getQuery(), url.getRef());
    }
}
