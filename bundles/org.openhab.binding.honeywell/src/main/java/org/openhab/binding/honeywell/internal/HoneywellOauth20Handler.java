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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.openhab.binding.honeywell.internal.config.HoneywellThermostatConfig;
import org.openhab.binding.honeywell.internal.discovery.HoneywellDiscoveryService;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellAccountHandler;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellCacheProcessor;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellHttpClientProvider;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthClientService;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellOauth20Handler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Anthony Sepa - Initial contribution
 */
// TODO: Add channel to provide communication details(?)
@NonNullByDefault
public class HoneywellOauth20Handler extends BaseBridgeHandler
        implements HoneywellConnectionInterface, HoneywellCacheProcessor, HoneywellAccountHandler {
    private final Logger logger = LoggerFactory.getLogger(HoneywellOauth20Handler.class);

    private final HttpClient secureClient;
    private final OAuthFactory oAuthFactory;
    private @Nullable OAuthClientService oAuthService;
    private @Nullable ScheduledFuture<?> cachedFuture;

    private final HashMap<HoneywellCacheProcessor, List<String>> cacheConsumers = new HashMap<>(6);
    private final HashMap<String, String> cachedData = new HashMap<>(2);

    // BridgeConfig items
    private String consumerKey = "";
    private String consumerSecret = "";
    private int timeout = 3000;

    public HoneywellOauth20Handler(Bridge thing, HoneywellHttpClientProvider honeywellClientProvider,
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
    public String getThermostatDiscoveryInfo() throws IOException, IllegalStateException {
        return getFromHoneywell(String.format(HONEYWELL_LOCATIONS_URL, consumerKey));
    }

    // Gets sensor discovery information
    @Override
    public String getSensorDiscoveryInfo(int locationId, String thermostatId)
            throws IOException, IllegalStateException {
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
        final OAuthClientService tempOAuthService = oAuthFactory.createOAuthClientService(thing.getUID().getAsString(),
                HONEYWELL_TOKEN_URL, HONEYWELL_AUTH_URL, consumerKey, consumerSecret, null, true);
        tempOAuthService.addExtraAuthField("Content-Type", URL_CONTENT_TYPE);
        tempOAuthService.addExtraAuthField("Accept", JSON_CONTENT_TYPE);
        oAuthService = tempOAuthService;

        // scheduler setup
        // wait 1/3 of the refresh time to allow sensors and thermostats to register
        cachedFuture = scheduler.scheduleWithFixedDelay(this::refreshCache, (long) Math.floor(bridgeConfig.refresh / 3),
                bridgeConfig.refresh, TimeUnit.SECONDS);

        // status setup
        updateStatus(ThingStatus.ONLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                "Waiting for authorization from Honeywell");
        scheduler.schedule(this::setThingStatus, 0, TimeUnit.SECONDS);
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        final HoneywellThermostatConfig childConfig = childThing.getConfiguration().as(HoneywellThermostatConfig.class);
        addCacheProcessor((HoneywellCacheProcessor) childHandler,
                honeywellUrl(HoneywellResourceType.THERMOSTAT, childConfig.locationId, childConfig.deviceId));
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        final HoneywellThermostatConfig childConfig = childThing.getConfiguration().as(HoneywellThermostatConfig.class);
        delCacheProcessor((HoneywellCacheProcessor) childHandler,
                honeywellUrl(HoneywellResourceType.THERMOSTAT, childConfig.locationId, childConfig.deviceId));
    }

    /**
     * Set the status based on whether the bridge can get an fresh access token (token is not tested)
     */
    private void setThingStatus() {
        try {
            getAccessToken(true);
            updateStatus(ThingStatus.ONLINE);
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    @Override
    public void dispose() {
        // stop scheduler
        logger.debug("Removing scheduler for {}", thing.getUID().getAsString());
        final ScheduledFuture<?> job = cachedFuture;
        if (job != null) {
            job.cancel(true);
            cachedFuture = null;
        }
        // stop oauth2
        logger.debug("Removing oauth20 for {}", thing.getUID().getAsString());
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
    @SuppressWarnings("unused")
    private void refreshCache() {
        logger.debug("Refreshing the caches");
        // check status
        if (!isOnline()) {
            logger.trace("Bridge is offline");
            return;
        } else if (cacheConsumers.isEmpty()) {
            logger.trace("No consumers to feed");
            return;
        }

        // Use a processedUrl list to signal when to get new data
        // After getting new data we check for a TOO_MANY_REQUESTS
        // or BLANK_JSON signal. If it is a blank JSON use the cache
        // if it exists or pipe the empty.
        final List<String> processedUrl = new ArrayList<String>(6);
        for (HoneywellCacheProcessor key : cacheConsumers.keySet()) {
            @Nullable
            String newCache;
            final @Nullable List<String> urls = cacheConsumers.get(key);

            if (null == urls) {
                logger.error("Not possible sign of the apocalypse head for the nearest bunker");
                return;
            }

            for (String honeywellUrl : urls) {
                boolean cont = true;
                if (!processedUrl.contains(honeywellUrl)) {
                    logger.trace("URL fresh data: '{}'", honeywellUrl);
                    processedUrl.add(honeywellUrl);
                    newCache = getFromHoneywell(honeywellUrl);
                    if (HONEYWELL_TOOMANY_JSON.equals(newCache)) {
                        cont = false;
                        break;
                    } else if (HONEYWELL_BLANK_JSON.equals(newCache)) {
                        logger.trace("URL blank data");
                        if (cachedData.containsKey(honeywellUrl)) {
                            logger.trace("URL blank data pulled cache");
                            newCache = cachedData.get(honeywellUrl);
                        }
                    } else {
                        cachedData.put(honeywellUrl, newCache);
                    }
                } else {
                    logger.trace("URL cache data: '{}'", honeywellUrl);
                    newCache = cachedData.get(honeywellUrl);
                }
                if (null != newCache) {
                    key.processCache(honeywellUrl, newCache);
                }
                if (!cont) {
                    break;
                }
            }
        }
    }

    // Add the cache processor first removing the oldone and any unneeded data
    @SuppressWarnings("unused")
    @Override
    public void addCacheProcessor(HoneywellCacheProcessor cacheProcessor, String honeywellUrl) {
        logger.debug("Registering cache URL: {}", honeywellUrl);
        @Nullable
        List<String> urls;
        if (cacheConsumers.containsKey(cacheProcessor)) {
            urls = cacheConsumers.get(cacheProcessor);
            if (null != urls && urls.contains(honeywellUrl)) {
                return;
            } else if (null == urls) {
                urls = new ArrayList<String>();
            }
            urls.add(honeywellUrl);
        } else {
            urls = new ArrayList<String>();
            urls.add(honeywellUrl);
        }
        cacheConsumers.put(cacheProcessor, urls);
    }

    // Remove the cache processor and cache if last processor
    @Override
    public void delCacheProcessor(HoneywellCacheProcessor cacheProcessor, String honeywellUrl) {
        logger.debug("Removing cache processor");
        if (cacheConsumers.containsKey(cacheProcessor)) {
            @Nullable
            List<String> urls = cacheConsumers.get(cacheProcessor);
            if (null != urls) {
                urls.remove(honeywellUrl);
            }
            logger.trace("Removing cache URLs: {}", urls);
            if (null == urls || urls.isEmpty()) {
                cacheConsumers.remove(cacheProcessor);
            }
            cachedData.remove(honeywellUrl);
        }
    }

    @Override
    public String honeywellUrl(HoneywellResourceType resourceType, int locationId, String deviceId, int groupId) {
        switch (resourceType) {
            case GROUP:
                return String.format(HONEYWELL_GROUP_URL, deviceId, groupId, consumerKey, locationId);
            default:
                logger.warn("Unsupported HoneywellResourceType with 4 args '{}'", resourceType);
                return "";
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
        logger.debug("handleCommand() HoneywellBridgeHandler: {}", channelUID);
    }

    // Pulls from cached information
    @Override
    public String getCached(String honeywellUrl) {
        final @Nullable String result = cachedData.get(honeywellUrl);
        return (null == result) ? HONEYWELL_BLANK_JSON : result;
    }

    /**
     * 
     * format the Url into a Uri and send in down the pike return a error JSON if something goes wrong
     * 
     * @param honeywellUrl
     * @return JSON formatted string
     */
    private String getFromHoneywell(String honeywellUrl) {
        logger.debug("GET Url '{}'", honeywellUrl);
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

    @Override
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
        logger.debug("POST Url '{}', Content '{}'", honeywellUrl, stateContent);
        try {
            final URI uri = uriFromString(honeywellUrl);
            return postHttpHoneywell(uri, stateContent, isRetry);
        } catch (URISyntaxException | MalformedURLException e) {
            // send this up the chain a malformed thing config might cause this
            logger.warn("Creating http POST request failed: {}", e.getMessage());
            return String.format(HONEYWELL_ERROR_JSON, String.format("Requesting '{}', Content '{}' failed: {}",
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
                logger.warn("Communication failure, second try for: {}", uri);
                return String.format(HONEYWELL_ERROR_JSON,
                        String.format("Requesting '{}', Content '{}' failed: {}", uri, stateContent, message));
            }
            logger.warn("Communication failure, for '{}': '{}'", uri, message);
            return postHttpHoneywell(uri, stateContent, true);
        } catch (IllegalStateException e) {
            Throwable cause = e.getCause();
            if (null != cause) {
                message = cause.getMessage();
            } else {
                message = e.getMessage();
            }
            logger.error("Communicaitons failure, for '{}': '{}'", uri, message);
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
                    logger.warn("Requesting '{}' failed: Authorization error", request.getURI());
                    if (!forceRefresh) {
                        throw new IOException("Authenticaiton failed requesting " + request.getURI());
                    } else {
                        throw new IllegalStateException("Authenticaiton failed requesting " + request.getURI());
                    }
                case HttpStatus.TOO_MANY_REQUESTS_429:
                    // API server is getting testy
                    logger.debug("Requesting '{}' (method='{}', content='{}') failed: Too many requests",
                            request.getURI(), request.getMethod(), request.getContent());
                    logger.warn("Too many requests, wait til next refresh");
                    return HONEYWELL_TOOMANY_JSON;
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
                logger.warn("Http request timed out, ignoring: {}", e.getMessage());
                return HONEYWELL_BLANK_JSON;
            }
        } catch (CancellationException | InterruptedException e) {
            logger.debug("Request to URL {} was cancelled by thing handler.", request.getURI());
            return HONEYWELL_BLANK_JSON;
        } catch (Exception e) {
            // i've seen an authorization failed get here on the first try so I do an IOException to retry
            // the forced refresh should cause it to never get here a second time look at breakig out ExecutionException
            logger.warn("Requesting '{}' (method='{}') failed: {}", request.getURI(), request.getMethod(),
                    e.getMessage());
            if (!forceRefresh) {
                throw new IOException("Unable to get a ContentResponse: " + e.getMessage());
            }
            throw new IllegalStateException("Unable to get a ContentResponse: " + e.getMessage());
        }
    }

    // Honeywell Account Handler routines
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
