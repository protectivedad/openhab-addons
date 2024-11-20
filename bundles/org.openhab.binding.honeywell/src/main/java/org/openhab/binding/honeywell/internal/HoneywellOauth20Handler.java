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
import static org.openhab.core.library.unit.Units.SECOND;

import java.io.IOException;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.measure.quantity.Time;

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
import org.openhab.binding.honeywell.internal.config.HoneywellThermostatConfig;
import org.openhab.binding.honeywell.internal.discovery.HoneywellDiscoveryService;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellCacheProcessor;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellHttpClientProvider;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthClientService;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellOauth20Handler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellOauth20Handler extends BaseBridgeHandler implements HoneywellCacheProcessor {
    private final Logger logger = LoggerFactory.getLogger(HoneywellOauth20Handler.class);

    public static final String HONEYWELL_END = "?apikey=%s&locationId=%s";
    public static final String HONEYWELL_API = "https://api.honeywell.com/";
    public static final String HONEYWELL_CONTENT_URL = HONEYWELL_API + "v2";
    public static final String HONEYWELL_TOKEN_URL = HONEYWELL_API + "oauth2/token";
    public static final String HONEYWELL_AUTH_URL = HONEYWELL_API + "oauth2/authorize";
    public static final String HONEYWELL_LOCATIONS_URL = HONEYWELL_CONTENT_URL + "/locations?apikey=%s";
    public static final String HONEYWELL_DEVICES_STUB = HONEYWELL_CONTENT_URL + "/devices";
    public static final String HONEYWELL_THERMOSTAT_STUB = HONEYWELL_DEVICES_STUB + "/thermostats";
    public static final String HONEYWELL_THERMOSTAT_URL = HONEYWELL_THERMOSTAT_STUB + "/%s" + HONEYWELL_END;
    public static final String HONEYWELL_SCHEDULE_STUB = HONEYWELL_DEVICES_STUB + "/schedule/%s";
    public static final String HONEYWELL_SCHEDULE_URL = HONEYWELL_SCHEDULE_STUB + HONEYWELL_END + "&type=%s";
    public static final String HONEYWELL_SCHEDULE_PAUSE_URL = HONEYWELL_SCHEDULE_STUB + "/status/pause" + HONEYWELL_END;
    public static final String HONEYWELL_SCHEDULE_RESUME_URL = HONEYWELL_SCHEDULE_STUB + "/status/resume"
            + HONEYWELL_END;
    public static final String HONEYWELL_PRIORITY_URL = HONEYWELL_THERMOSTAT_STUB + "/%s/priority" + HONEYWELL_END;
    public static final String HONEYWELL_GROUP_URL = HONEYWELL_THERMOSTAT_STUB + "/%s/group/%s/rooms" + HONEYWELL_END;

    private final HttpClient secureClient;
    private final OAuthFactory oAuthFactory;
    private @Nullable OAuthClientService oAuthService;
    private @Nullable ScheduledFuture<?> cachedFuture;

    private final HashMap<HoneywellCacheProcessor, String> cacheConsumers = new HashMap<>(6);
    private final HashMap<String, String> cachedData = new HashMap<>(2);

    // BridgeConfig items
    private String consumerKey = "";
    private String consumerSecret = "";
    private int refresh = 0;
    private int timeout = 3000;

    private final HashMap<ChannelUID, String> resultPipe = new HashMap<>(5);
    private boolean optimized = false;
    private boolean wasConnected = false;
    private boolean connected = false;
    private int stableTimer = 0;

    public HoneywellOauth20Handler(Bridge thing, HoneywellHttpClientProvider honeywellClientProvider,
            OAuthFactory oAuthFactory) {
        super(thing);
        secureClient = honeywellClientProvider.getSecureClient();
        this.oAuthFactory = oAuthFactory;
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(HoneywellDiscoveryService.class);
    }

    // Gets thermostat discovery information
    public String getThermostatDiscoveryInfo() throws IOException, IllegalStateException {
        return getFromHoneywell(String.format(HONEYWELL_LOCATIONS_URL, consumerKey));
    }

    // Gets sensor discovery information
    public String getSensorDiscoveryInfo(int locationId, String thermostatId)
            throws IOException, IllegalStateException {
        return getFromHoneywell(honeywellUrl(HONEYWELL_PRIORITY_URL, locationId, thermostatId));
    }

    @Override
    public void initialize() {
        // config setup
        final HoneywellBridgeConfig bridgeConfig = getConfigAs(HoneywellBridgeConfig.class);
        consumerKey = bridgeConfig.consumerKey;
        consumerSecret = bridgeConfig.consumerSecret;
        refresh = bridgeConfig.refresh;
        timeout = bridgeConfig.timeout;
        optimized = false;
        wasConnected = false;
        connected = false;
        stableTimer = 0;

        // oauth2 setup
        final OAuthClientService tempOAuthService = oAuthFactory.createOAuthClientService(thing.getUID().getAsString(),
                HONEYWELL_TOKEN_URL, HONEYWELL_AUTH_URL, consumerKey, consumerSecret, null, true);
        tempOAuthService.addExtraAuthField("Content-Type", URL_CONTENT_TYPE);
        tempOAuthService.addExtraAuthField("Accept", JSON_CONTENT_TYPE);
        oAuthService = tempOAuthService;

        // status setup
        updateStatus(ThingStatus.ONLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                "Waiting for authorization from Honeywell");
        scheduler.schedule(this::setThingStatus, 0, TimeUnit.SECONDS);

        // scheduler setup
        // wait 1/3 of the refresh time to allow sensors and thermostats to register
        dynamicScheduler((int) Math.floor(refresh / 3));

        thing.getChannels().forEach(this::createChannel);
    }

    private void dynamicScheduler(int refresh) {
        // make sure it has been called with the current refresh time before trying to optimize
        logger.trace("Dynamic scheduler stableTimer, optimized, connected, refresh: ({}, {}, {}, {})", stableTimer,
                optimized, connected, refresh);
        if (this.refresh == refresh) {
            final boolean isStable = stableTimer >= (2 * 60 * 60);
            stableTimer = (connected) ? stableTimer + ((!isStable) ? refresh : 0) : 0;
            optimized = (!connected) ? true : optimized;
            if (!connected && wasConnected) {
                this.refresh = refresh + 10;
            } else if (connected) {
                this.refresh = refresh + ((isStable) ? ((!optimized && refresh > 30) ? -10 : 0) : 0);
                stableTimer = (isStable && !optimized) ? 0 : stableTimer;
            }
            wasConnected = connected;
        }
        logger.debug("Starting schedule with refresh of {} seconds", refresh);
        try {
            cachedFuture = scheduler.schedule(this::refreshCache, refresh, TimeUnit.SECONDS);
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    /**
     * create all necessary information to handle every channel
     *
     * @param channel a thing channel
     */
    private void createChannel(Channel channel) {
        final ChannelUID channelUID = channel.getUID();

        final ChannelTypeUID channelTypeUID = channel.getChannelTypeUID();
        if (channelTypeUID == null) {
            logger.warn("Cannot determine channel-type for channel '{}'", channelUID);
            return;
        }
        resultPipe.put(channelUID, channelTypeUID.getId());
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        final HoneywellThermostatConfig childConfig = childThing.getConfiguration().as(HoneywellThermostatConfig.class);
        final String honeywellUrl = honeywellUrl(HONEYWELL_THERMOSTAT_URL, childConfig.locationId,
                childConfig.deviceId);
        cacheConsumers.put((HoneywellCacheProcessor) childHandler, honeywellUrl);
        cachedData.putIfAbsent(honeywellUrl, HONEYWELL_BLANK_JSON);
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        final HoneywellThermostatConfig childConfig = childThing.getConfiguration().as(HoneywellThermostatConfig.class);
        cacheConsumers.remove((HoneywellCacheProcessor) childHandler);
        cachedData.remove(honeywellUrl(HONEYWELL_THERMOSTAT_URL, childConfig.locationId, childConfig.deviceId));
    }

    /**
     * Set the status based on whether the bridge can get an fresh access token (token is not tested). It could still
     * fail with a TOO_MANY_REQUESTS error.
     */
    private void setThingStatus() {
        try {
            getAccessToken(true);
            updateStatus(ThingStatus.ONLINE);
            connected = true;
            optimized = false;
            processPipe();
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    @Override
    public void dispose() {
        // stop scheduler
        ScheduledFuture<?> job;
        job = cachedFuture;
        if (job != null) {
            job.cancel(true);
            cachedFuture = null;
        }
        // stop oauth2
        final OAuthClientService tempOAuthService = oAuthService;
        if (tempOAuthService != null) {
            oAuthFactory.ungetOAuthService(thing.getUID().getAsString());
            oAuthService = null;
        }
        super.dispose();
    }

    /**
     * For each registered comsumer feed it information catching it for any future consumers.
     * 
     */
    private void refreshCache() {
        // check status
        if (!isOnline() || cacheConsumers.isEmpty()) {
            dynamicScheduler((int) Math.floor(refresh / 3));
            return;
        }

        // refresh the caches
        connected = true;
        for (String honeywellUrl : cachedData.keySet()) {
            final String newCache = getFromHoneywell(honeywellUrl);
            if (HONEYWELL_TOOMANY_JSON.equals(newCache)) {
                connected = false;
                break;
            } else if (!HONEYWELL_BLANK_JSON.equals(newCache)) {
                cachedData.put(honeywellUrl, newCache);
            }
        }

        // feed the things
        for (HoneywellCacheProcessor key : cacheConsumers.keySet()) {
            final @Nullable String honeywellUrl = cacheConsumers.get(key);
            if (null != honeywellUrl) {
                final @Nullable String newCache = cachedData.get(honeywellUrl);
                if (null != newCache) {
                    key.processCache(newCache);
                }
            }
        }

        dynamicScheduler(refresh);

        // feed bridge channels
        processPipe();
    }

    public String honeywellUrl(String resourceUrl, int locationId, String deviceId, String type) {
        return String.format(resourceUrl, deviceId, consumerKey, locationId, type);
    }

    public String honeywellUrl(String resourceUrl, int locationId, String deviceId, int groupId) {
        return String.format(resourceUrl, deviceId, groupId, consumerKey, locationId);
    }

    public String honeywellUrl(String resourceUrl, int locationId, String deviceId) {
        return String.format(resourceUrl, deviceId, consumerKey, locationId);
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
    private String getAccessToken(boolean force) throws IOException, IllegalStateException {
        final OAuthClientService tempOAuthService = oAuthService;
        if (null == tempOAuthService) {
            throw new IllegalStateException("No authentication service available");
        }

        final @Nullable AccessTokenResponse accessTokenResponse;
        try {
            accessTokenResponse = (force) ? tempOAuthService.refreshToken() : tempOAuthService.getAccessTokenResponse();
        } catch (OAuthException | OAuthResponseException e) {
            throw new IllegalStateException(
                    String.format("OAuth service failed getting access token response: " + e.getMessage()));
        }

        if (null == accessTokenResponse) {
            throw new IllegalStateException(
                    "OAuth service failed visit: `http://<your openHAB address>:8080/connecthoneywell/`");
        }
        return accessTokenResponse.getAccessToken();
    }

    /**
     * Does nothing in the base implementation.
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (!(command instanceof RefreshType)) {
            return;
        }
        final @Nullable String resultType = resultPipe.get(channelUID);
        if (null != resultType) {
            try {
                process(channelUID, resultType);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing result for channel {}: {}", channelUID, e.getMessage());
            }
        }
    }

    public String putHttpHoneywell(String honeywellUrl, String stateContent) {
        final URI uri;
        try {
            uri = uriFromString(honeywellUrl);
            return putHttpHoneywell(uri, stateContent, false).trim();
        } catch (URISyntaxException | MalformedURLException e) {
            // send this up the chain a malformed thing config might cause this
            logger.warn("Creating http GET request failed: {}", e.getMessage());
            return String.format(HONEYWELL_ERROR_JSON,
                    String.format("Requesting '%s' failed: %s", honeywellUrl, e.getMessage()));
        } catch (Exception e) {
            logger.warn("Unknown exception from Honeywell: {}", e.getMessage());
            return String.format(HONEYWELL_ERROR_JSON,
                    String.format("Requesting '%s' failed: %s", honeywellUrl, e.getMessage()));
        }
    }

    private String putHttpHoneywell(URI uri, String stateContent, boolean isRetry) {
        Request request = secureClient.newRequest(uri).method(HttpMethod.PUT);
        if (stateContent.isEmpty()) {
            request.header("Content-Type", String.format("%s;%s", URL_CONTENT_TYPE, "charset=utf-8"));
        } else {
            request.header("Content-Type", JSON_CONTENT_TYPE).content(new StringContentProvider(stateContent));
        }
        try {
            return sumbitHttpHoneywell(request, isRetry);
        } catch (IOException e) {
            if (isRetry) {
                logger.warn("Communication failure, second try for: {}", uri);
                return String.format(HONEYWELL_ERROR_JSON,
                        String.format("Requesting '%s' failed: %s", uri, e.getMessage()));
            }
            logger.warn("Communication failure, first try: '{}'", uri);
            return putHttpHoneywell(uri, stateContent, true);
        } catch (IllegalStateException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            return HONEYWELL_BLANK_JSON;
        }
    }

    /**
     * 
     * format the Url into a Uri and send in down the pike return a error JSON if something goes wrong
     * 
     * @param honeywellUrl
     * @return JSON formatted string
     */
    public String getFromHoneywell(String honeywellUrl) {
        final URI uri;
        try {
            uri = uriFromString(honeywellUrl);
            return getFromHoneywell(uri, false).trim();
        } catch (URISyntaxException | MalformedURLException e) {
            // send this up the chain a malformed thing config might cause this
            logger.warn("Creating http GET request failed: {}", e.getMessage());
            return String.format(HONEYWELL_ERROR_JSON,
                    String.format("Requesting '{}' failed: {}", honeywellUrl, e.getMessage()));
        } catch (Exception e) {
            logger.error("Unknown exception from Honeywell: {}", e.getMessage());
            return String.format(HONEYWELL_ERROR_JSON,
                    String.format("Requesting '{}' failed: {}", honeywellUrl, e.getMessage()));
        }
    }

    /**
     * 
     * retryable portion of the GET request to API bridge will taken OFFLINE for any IllegalState exceptions
     * for IO exceptions it will retry once and then send an error JSON up the pike
     * 
     * @param uri
     * @param isRetry
     * @return - JSON formatted string
     */
    private String getFromHoneywell(URI uri, boolean isRetry) {
        Request request = secureClient.newRequest(uri).method(HttpMethod.GET);
        request.header("Content-Type", URL_CONTENT_TYPE);
        try {
            return sumbitHttpHoneywell(request, isRetry);
        } catch (IOException e) {
            if (isRetry) {
                logger.warn("Communication failure, second try for: {}", uri);
                return String.format(HONEYWELL_ERROR_JSON,
                        String.format("Requesting '{}' failed: {}", uri, e.getMessage()));
            }
            logger.warn("Communication failure, first try: '{}'", uri);
            return getFromHoneywell(uri, true);
        } catch (IllegalStateException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            return HONEYWELL_BLANK_JSON;
        }
    }

    public String postHttpHoneywell(String honeywellUrl, String stateContent) {
        return postHttpHoneywell(honeywellUrl, stateContent, false).trim();
    }

    /**
     * 
     * @param honeywellUrl
     * @param stateContent
     * @param isRetry
     * @return JSON String
     */
    private String postHttpHoneywell(String honeywellUrl, String stateContent, boolean isRetry) {
        try {
            final URI uri = uriFromString(honeywellUrl);
            return postHttpHoneywell(uri, stateContent, isRetry);
        } catch (URISyntaxException | MalformedURLException e) {
            // send this up the chain a malformed thing config might cause this
            logger.warn("Creating http POST request failed: {}", e.getMessage());
            return String.format(HONEYWELL_ERROR_JSON, String.format("Requesting '%s', Content '%s' failed: %s",
                    honeywellUrl, stateContent, e.getMessage()));
        }
    }

    /**
     * 
     * @param uri
     * @param stateContent
     * @param isRetry
     * @return JSON String
     */
    private String postHttpHoneywell(URI uri, String stateContent, boolean isRetry) {
        Request request = secureClient.newRequest(uri).method(HttpMethod.POST)
                .content(new StringContentProvider(stateContent));
        request.header("Content-Type", JSON_CONTENT_TYPE);
        final String message;
        try {
            return sumbitHttpHoneywell(request, isRetry);
        } catch (IOException e) {
            Throwable cause = e.getCause();
            if (null != cause) {
                message = cause.getMessage();
            } else {
                message = e.getMessage();
            }
            if (isRetry) {
                return String.format(HONEYWELL_ERROR_JSON,
                        String.format("Requesting '%s', Content '%s' failed: '%s'", uri, stateContent, message));
            }
            return postHttpHoneywell(uri, stateContent, true);
        } catch (IllegalStateException e) {
            Throwable cause = e.getCause();
            if (null != cause) {
                message = cause.getMessage();
            } else {
                message = e.getMessage();
            }
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
            return HONEYWELL_BLANK_JSON;
        }
    }

    /**
     * 
     * send request to the API return the response, empty JSON, or error JSON
     * 
     * @param request - Request to send requiring a few common items
     * @param forceRefresh
     * @return JSON formatted string a thing issue sould result in a error or empty string
     * @throws IllegalStateException - bridge configuration issue
     * @throws IOException - bridge retriable issue
     */
    private String sumbitHttpHoneywell(Request request, boolean forceRefresh)
            throws IOException, IllegalStateException {
        request.header("Authorization", "Bearer " + getAccessToken(forceRefresh));
        request.header("Accept", JSON_CONTENT_TYPE);
        request.timeout(timeout, TimeUnit.MILLISECONDS);
        logger.trace("Sending to '{}': {}", request.getURI(), requestToLogString(request));
        try {
            ContentResponse response = request.send();
            logger.trace("Receiving from '{}': '{}'", request.getURI(), response.toString());
            switch (response.getStatus()) {
                case HttpStatus.OK_200:
                    return response.getContentAsString();
                case HttpStatus.UNAUTHORIZED_401:
                    // on the first try this means the access token has been invalidated before known expiry
                    // on the retry this should never happen, our getAccessToken should cause an exception
                    logger.debug("Requesting '{}' (method='{}', content='{}') failed: Authorization error",
                            request.getURI(), request.getMethod(), request.getContent());
                    if (!forceRefresh) {
                        throw new IOException("Authenticaiton failed requesting " + request.getURI());
                    } else {
                        throw new IllegalStateException("Authenticaiton failed requesting " + request.getURI());
                    }
                case HttpStatus.TOO_MANY_REQUESTS_429:
                    // API server is getting testy
                    logger.debug("Requesting '{}' (method='{}', content='{}') failed: Too many requests",
                            request.getURI(), request.getMethod(), request.getContent());
                    return HONEYWELL_TOOMANY_JSON;
                case HttpStatus.NO_CONTENT_204:
                    // Schedule status pause and resume return this error, is okay
                    return response.getContentAsString();
                case HttpStatus.BAD_REQUEST_400:
                case HttpStatus.NOT_FOUND_404:
                default:
                    logger.warn("Requesting '{}' (method='{}', content='{}') failed: {} {}", request.getURI(),
                            request.getMethod(), request.getContent(), response.getStatus(), response.getReason());
                    return response.getContentAsString();
            }
        } catch (TimeoutException | ExecutionException e) {
            if (!forceRefresh) {
                // http timed out but we have an access token
                throw new IOException(e);
            } else {
                return HONEYWELL_BLANK_JSON;
            }
        } catch (CancellationException | InterruptedException e) {
            logger.debug("Request to URL {} was cancelled by thing handler.", request.getURI());
            return HONEYWELL_BLANK_JSON;
        } catch (Exception e) {
            // i've seen an authorization failed get here on the first try so I do an IOException to retry
            // the forced refresh should cause it to never get here a second time look at breakig out ExecutionException
            if (!forceRefresh) {
                throw new IOException("Unable to get a ContentResponse: " + e.getMessage());
            }
            logger.warn("Requesting '{}' (method='{}') failed: {}", request.getURI(), request.getMethod(),
                    e.getMessage());
            throw new IllegalStateException("Unable to get a ContentResponse: " + e.getMessage());
        }
    }

    // Honeywell Account Handler routines
    public ThingUID getUID() {
        return thing.getUID();
    }

    public String getLabel() {
        final @Nullable String label = thing.getLabel();
        return label == null ? "" : label;
    }

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

    public boolean isOnline() {
        return thing.getStatus() == ThingStatus.ONLINE;
    }

    public void authorize(String redirectUri, String reqCode) {
        try {
            final OAuthClientService tempOAuthService = oAuthService;
            if (tempOAuthService == null) {
                throw new OAuthException("OAuth service is not initialized");
            }
            tempOAuthService.getAccessTokenResponseByAuthorizationCode(reqCode, redirectUri);
        } catch (RuntimeException | OAuthException | IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
        } catch (final OAuthResponseException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
        }
    }

    public boolean equalsThingUID(String thingUID) {
        return getThing().getUID().getAsString().equals(thingUID);
    }

    public String formatAuthorizationUrl(String redirectUri) {
        try {
            final OAuthClientService tempOAuthService = this.oAuthService;
            return (tempOAuthService == null) ? "Service is down"
                    : tempOAuthService.getAuthorizationUrl(redirectUri, null, thing.getUID().getAsString());
        } catch (final OAuthException e) {
            logger.warn("Error constructing AuthorizationUrl: '{}'", e.getMessage());
            return "";
        }
    }

    // Simplified sensor processing
    public void processPipe() {
        resultPipe.entrySet().parallelStream().forEach(s -> process(s.getKey(), s.getValue()));
    }

    private void process(ChannelUID channelUID, String resultType) {
        final State state;
        switch (resultType) {
            case "connected":
                state = connected ? OnOffType.ON : OnOffType.OFF;
                break;
            case "optimized":
                state = optimized ? OnOffType.ON : OnOffType.OFF;
                break;
            case "refresh":
                state = new QuantityType<Time>(refresh, SECOND);
                break;
            default:
                logger.warn("Unsupported bridge item-type '{}'", resultType);
                return;
        }
        try {
            updateState(channelUID, state);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Failed processing result: {}", e.getMessage());
        }
    }
}
