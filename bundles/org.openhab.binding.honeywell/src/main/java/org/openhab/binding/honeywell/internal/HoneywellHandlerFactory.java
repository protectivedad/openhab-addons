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
import org.openhab.binding.honeywell.internal.transform.CascadedValueTransformationImpl;
import org.openhab.binding.honeywell.internal.transform.NoOpValueTransformation;
import org.openhab.binding.honeywell.internal.transform.ValueTransformation;
import org.openhab.binding.honeywell.internal.transform.ValueTransformationProvider;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.openhab.core.transform.TransformationHelper;
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
 * @author Jan N. Klug - Initial contribution
 * @author Anthony Sepa - Heavily edited for use in Honeywell binding
 */
@NonNullByDefault
@Component(configurationPid = "binding.honeywell", service = ThingHandlerFactory.class)
public class HoneywellHandlerFactory extends BaseThingHandlerFactory
        implements ValueTransformationProvider, HttpClientProvider {
    private final Logger logger = LoggerFactory.getLogger(HoneywellHandlerFactory.class);
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = new HashSet<ThingTypeUID>();
    static {
        SUPPORTED_THING_TYPES_UIDS.add(BRIDGE_TYPE_OAUTH20);
        SUPPORTED_THING_TYPES_UIDS.add(THERMOSTAT_HONEYWELL_THING);
        SUPPORTED_THING_TYPES_UIDS.add(SCHEDULE_HONEYWELL_THING);
        SUPPORTED_THING_TYPES_UIDS.add(SENSOR_HONEYWELL_THING);
    }
    private final HttpClient secureClient;

    @Activate
    public HoneywellHandlerFactory(@Reference HttpClientFactory httpClientFactory) {
        logger.debug("HoneywellHandlerFactory constructor");
        try {
            secureClient = httpClientFactory.createHttpClient(BINDING_ID);
            secureClient.start();
        } catch (Exception e) {
            // catching exception is necessary due to the signature of HttpClient.start()
            logger.warn("Failed to start secure http client: {}", e.getMessage());
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
        if (BRIDGE_TYPE_OAUTH20.equals(thingTypeUID)) {
            if (thing instanceof Bridge) {
                return new HoneywellBridgeHandler((Bridge) thing, this);
            }
        } else if (THERMOSTAT_HONEYWELL_THING.equals(thingTypeUID)) {
            return new HoneywellThermostatHandler(thing, this, this);
        } else if (SCHEDULE_HONEYWELL_THING.equals(thingTypeUID)) {
            return new HoneywellScheduleHandler(thing, this, this);
        } else if (SENSOR_HONEYWELL_THING.equals(thingTypeUID)) {
            return new HoneywellSensorHandler(thing, this);
        }
        return null;
    }

    @Override
    public ValueTransformation getValueTransformation(@Nullable String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return NoOpValueTransformation.getInstance();
        }
        return new CascadedValueTransformationImpl(pattern,
                name -> TransformationHelper.getTransformationService(name));
    }

    @Override
    public HttpClient getSecureClient() {
        return secureClient;
    }
}
