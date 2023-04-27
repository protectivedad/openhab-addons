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
package org.openhab.binding.honeywell.internal;

import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.*;

import java.io.IOException;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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
import org.openhab.binding.honeywell.internal.discovery.HoneywellDiscoveryService;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellCacheProcessor;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthClientService;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellBridgeHandler extends BaseBridgeHandler
        implements HoneywellConnectionInterface, HoneywellAccountHandler {
    private final Logger logger = LoggerFactory.getLogger(HoneywellBridgeHandler.class);

    private final HttpClient secureClient;
    private final OAuthFactory oAuthFactory;
    private @Nullable OAuthClientService oAuthService;
    private @Nullable ScheduledFuture<?> cachedFuture;

    private final static String JSON_CONTENT_TYPE = "application/json";
    private final static String URL_CONTENT_TYPE = "application/x-www-form-urlencoded";

    // BridgeConfig items
    private final HashMap<HoneywellCacheProcessor, String> cacheConsumers = new HashMap<>(6);
    private final HashMap<String, String> cachedData = new HashMap<>(2);

    private String consumerKey = "";
    private String consumerSecret = "";
    private int timeout = 3000;

    public HoneywellBridgeHandler(Bridge thing, HoneywellHttpClientProvider honeywellClientProvider,
            OAuthFactory oAuthFactory) {
        super(thing);
        secureClient = honeywellClientProvider.getSecureClient();
        this.oAuthFactory = oAuthFactory;
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(HoneywellDiscoveryService.class);
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

    @Override
    public void initialize() {
        // config setup
        final HoneywellBridgeConfig bridgeConfig = getConfigAs(HoneywellBridgeConfig.class);
        consumerKey = bridgeConfig.consumerKey;
        consumerSecret = bridgeConfig.consumerSecret;
        timeout = bridgeConfig.timeout;

        // oauth2 setup
        final String thingUID = this.getThing().getUID().getAsString();
        final OAuthClientService tempOAuthService = oAuthFactory.createOAuthClientService(thingUID, HONEYWELL_TOKEN_URL,
                HONEYWELL_AUTH_URL, consumerKey, consumerSecret, null, true);
        tempOAuthService.addExtraAuthField("Content-Type", URL_CONTENT_TYPE);
        tempOAuthService.addExtraAuthField("Accept", JSON_CONTENT_TYPE);
        oAuthService = tempOAuthService;

        // scheduler setup
        cachedFuture = scheduler.scheduleWithFixedDelay(this::refreshCache, 1, bridgeConfig.refresh, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        // stop scheduler
        final ScheduledFuture<?> job = cachedFuture;
        if (job != null) {
            job.cancel(true);
            cachedFuture = null;
        }
        // stop oauth2
        final OAuthClientService tempOAuthService = oAuthService;
        if (tempOAuthService != null) {
            oAuthFactory.ungetOAuthService(this.getThing().getUID().getAsString());
            oAuthService = null;
        }
        super.dispose();
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
            final @Nullable String honeywellUrl = cacheConsumers.get(cacheProcessor);
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
            case THERMOSTAT:
                return String.format(HONEYWELL_THERMOSTAT_URL, deviceId, consumerKey, locationId);
            case PRIORITY:
                return String.format(HONEYWELL_PRIORITY_URL, deviceId, consumerKey, locationId);
            case GROUP:
                return String.format(HONEYWELL_GROUP_URL, deviceId, consumerKey, locationId);
            default:
                logger.warn("Unsupported HoneywellResourceType with 3 args '{}'", resourceType);
                return "";
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

    /**
     * update the internal access token and set thing status on failure
     * 
     * Store information is used to get access token
     * 
     * @return Success or failure flag
     * @throws Exception
     */
    private String getAccessToken(boolean force) throws Exception {
        final OAuthClientService tempOAuthService = oAuthService;
        if (null == tempOAuthService) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "No authentication service available");
            throw new OAuthException("No authentication service available");
        }

        final @Nullable AccessTokenResponse accessTokenResponse;
        try {
            accessTokenResponse = (force) ? tempOAuthService.refreshToken() : tempOAuthService.getAccessTokenResponse();
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "OAuth service failed getting access token response: " + e.getMessage());
            throw e;
        }
        if (null == accessTokenResponse) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "OAuth service failed visit: `http://<your openHAB address>:8080/connecthoneywell/`");
            throw new OAuthException("Failed to get the access token");
        }
        logger.debug("Succeeded at getting the access token");
        return accessTokenResponse.getAccessToken();
    }

    /**
     * for each registered url fetch the data, cache it and feed it things
     * 
     */
    private void refreshCache() {
        try {
            for (Map.Entry<String, String> cacheUpdate : cachedData.entrySet()) {
                final @Nullable String url = cacheUpdate.getKey();
                logger.trace("Refresh for {}", url);
                cachedData.put(url, getFromHoneywell(url));
            }

            cacheConsumers.forEach((consumer, url) -> {
                logger.trace("Sending out cache to {}", consumer);
                consumer.processCache();
            });
        } catch (Exception e) {
            logger.warn("Unhandled error while updating cache: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Unhandled error while updating cache: " + e.getMessage());
            return;
        }
        updateStatus(ThingStatus.ONLINE);
    }

    /**
     * Does nothing in the base implementation.
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("handleCommand() HoneywellBridgeHandler: {}", channelUID);
    }

    // Pulls from cached information
    @Override
    public String getCached(String honeywellUrl) {
        final @Nullable String result = cachedData.get(honeywellUrl);
        return (null == result) ? "{}" : result;
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
            request.header("Authorization", "Bearer " + getAccessToken(isRetry));
            request.header("Content-Type", URL_CONTENT_TYPE);
            request.header("Accept", JSON_CONTENT_TYPE);
            request.timeout(timeout, TimeUnit.MILLISECONDS);
            logger.trace("Sending to '{}': {}", uri, requestToLogString(request));
            try {
                ContentResponse response = request.send();
                switch (response.getStatus()) {
                    case HttpStatus.OK_200:
                        return response.getContentAsString();
                    case HttpStatus.UNAUTHORIZED_401:
                        logger.debug("Requesting '{}' (method='GET') failed: Authorization error", uri);
                        if (!isRetry) {
                            logger.warn("Unuathorized: Force updating access token and retrying");
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
        } catch (OAuthException | OAuthResponseException | IOException e) {
            logger.warn("OAuth2 error: {}", e.getMessage());
            throw new IllegalStateException("OAuth2 error: " + e.getMessage());
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
            request.header("Authorization", "Bearer " + getAccessToken(isRetry));
            request.header("Content-Type", JSON_CONTENT_TYPE);
            request.header("Accept", JSON_CONTENT_TYPE);
            request.timeout(timeout, TimeUnit.MILLISECONDS);
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

    @Override
    public ThingUID getUID() {
        return thing.getUID();
    }

    @Override
    public String getLabel() {
        final @Nullable String label = thing.getLabel();
        return label == null ? "" : label;
    }

    @Override
    public boolean isAuthorized() {
        final OAuthClientService tempOAuthService = oAuthService;
        if (null == tempOAuthService) {
            return false;
        }

        final AccessTokenResponse accessTokenResponse;
        try {
            accessTokenResponse = tempOAuthService.getAccessTokenResponse();
        } catch (Exception e) {
            return false;
        }
        return accessTokenResponse != null && accessTokenResponse.getAccessToken() != null
                && accessTokenResponse.getRefreshToken() != null;
    }

    @Override
    public boolean isOnline() {
        return thing.getStatus() == ThingStatus.ONLINE;
    }

    @Override
    public void authorize(String redirectUri, String reqCode) {
        try {
            final OAuthClientService tempOAuthService = oAuthService;
            if (tempOAuthService == null) {
                throw new OAuthException("OAuth service is not initialized");
            }
            logger.debug("Make call to Honeywell to get access token.");
            tempOAuthService.getAccessTokenResponseByAuthorizationCode(reqCode, redirectUri);
        } catch (RuntimeException | OAuthException | IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
        } catch (final OAuthResponseException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
        }
    }

    @Override
    public boolean equalsThingUID(String thingUID) {
        return getThing().getUID().getAsString().equals(thingUID);
    }

    @Override
    public String formatAuthorizationUrl(String redirectUri) {
        try {
            final OAuthClientService tempOAuthService = this.oAuthService;
            return (tempOAuthService == null) ? "Service is down"
                    : tempOAuthService.getAuthorizationUrl(redirectUri, null, thing.getUID().getAsString());
        } catch (final OAuthException e) {
            logger.debug("Error constructing AuthorizationUrl: ", e);
            return "";
        }
    }
}
