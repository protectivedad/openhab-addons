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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellAuthService;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellHttpClientProvider;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellStateDescriptionProvider;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.honeywell", service = ThingHandlerFactory.class)
public class HoneywellHandlerFactory extends BaseThingHandlerFactory implements HoneywellHttpClientProvider {
    private final Logger logger = LoggerFactory.getLogger(HoneywellHandlerFactory.class);
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = new HashSet<ThingTypeUID>();
    static {
        SUPPORTED_THING_TYPES_UIDS.add(HONEYWELL_OAUTH20_BRIDGE);
        SUPPORTED_THING_TYPES_UIDS.add(HONEYWELL_THERMOSTAT_BRIDGE);
        SUPPORTED_THING_TYPES_UIDS.add(HONEYWELL_SENSOR_THING);
    }
    private final HttpClient secureClient;
    private final OAuthFactory oAuthFactory;
    private final HoneywellAuthService authService;
    private final HoneywellStateDescriptionProvider stateDescriptionProvider;

    @Activate
    public HoneywellHandlerFactory(@Reference HttpClientFactory httpClientFactory, @Reference OAuthFactory oAuthFactory,
            @Reference HoneywellAuthService authService,
            @Reference HoneywellStateDescriptionProvider stateDescriptionProvider) {
        this.oAuthFactory = oAuthFactory;
        this.authService = authService;
        this.stateDescriptionProvider = stateDescriptionProvider;
        try {
            secureClient = httpClientFactory.createHttpClient(BINDING_ID);
            secureClient.start();
        } catch (Exception e) {
            // catching exception is necessary due to the signature of HttpClient.start()
            logger.error("Failed to start secure http client: {}", e.getMessage());
            throw new IllegalStateException("Could not create secure HttpClient");
        }
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Deactivate
    public void deactivate() {
        try {
            secureClient.stop();
        } catch (Exception e) {
            // catching exception is necessary due to the signature of HttpClient.stop()
            logger.warn("Failed to stop secure http client: {}", e.getMessage());
        }
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        final ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (HONEYWELL_OAUTH20_BRIDGE.equals(thingTypeUID)) {
            final HoneywellOauth20Handler handler = new HoneywellOauth20Handler((Bridge) thing, this, oAuthFactory);
            authService.addHoneywellAccountHandler(handler);
            return handler;
        } else if (HONEYWELL_THERMOSTAT_BRIDGE.equals(thingTypeUID)) {
            return new HoneywellThermostatHandler((Bridge) thing, stateDescriptionProvider);
        } else if (HONEYWELL_SENSOR_THING.equals(thingTypeUID)) {
            return new HoneywellSensorHandler(thing);
        }
        return null;
    }

    @Override
    public void removeThing(ThingUID thingUID) {
        oAuthFactory.deleteServiceAndAccessToken(thingUID.getAsString());
    }

    @Override
    public HttpClient getSecureClient() {
        return secureClient;
    }
}
