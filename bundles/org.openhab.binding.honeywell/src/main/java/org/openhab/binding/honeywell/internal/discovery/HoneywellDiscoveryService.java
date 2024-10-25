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
package org.openhab.binding.honeywell.internal.discovery;

import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.honeywell.internal.HoneywellOauth20Handler;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellDiscoveryService} is responsible processing the
 * results of searches for Honeywell devices
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellDiscoveryService extends AbstractDiscoveryService implements ThingHandlerService {
    private Logger logger = LoggerFactory.getLogger(HoneywellDiscoveryService.class);

    private static final int SEARCH_TIME = 0;
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = new HashSet<ThingTypeUID>();
    static {
        SUPPORTED_THING_TYPES_UIDS.add(BRIDGE_TYPE_THERMOSTAT);
        SUPPORTED_THING_TYPES_UIDS.add(SENSOR_HONEYWELL_THING);
    }

    private @Nullable ScheduledFuture<?> discoveryFuture;
    private @Nullable HoneywellOauth20Handler bridgeHandler;

    public HoneywellDiscoveryService() {
        super(SUPPORTED_THING_TYPES_UIDS, SEARCH_TIME, false);
        logger.debug("Started discovery service");
    }

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof HoneywellOauth20Handler) {
            bridgeHandler = (HoneywellOauth20Handler) handler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return bridgeHandler;
    }

    @Override
    public void deactivate() {
        final ScheduledFuture<?> job = discoveryFuture;
        if (job != null) {
            job.cancel(true);
            discoveryFuture = null;
        }
        super.deactivate();
    }

    @Override
    protected void startScan() {
        logger.debug("Starting scan job");
        final ScheduledFuture<?> job = discoveryFuture;
        if (job == null || job.isDone()) {
            discoveryFuture = scheduler.schedule(this::discoveryThings, 1, TimeUnit.SECONDS);
        }
    }

    private void discoveryThings() {
        HoneywellOauth20Handler handler = bridgeHandler;
        if ((null == handler) || (handler.getThing().getStatus() != ThingStatus.ONLINE)) {
            return;
        }
        final HoneywellConnectionInterface honeywellApi = handler;
        final ThingUID bridgeUid = handler.getThing().getUID();
        try {
            final HoneywellDiscoveryLocationsData locationsData = new HoneywellDiscoveryLocationsData(
                    honeywellApi.getThermostatDiscoveryInfo());
            logger.trace("Locations {}", locationsData.locationName);
            locationsData.deviceLocation.forEach((thermostatId, locationId) -> {
                final @Nullable String deviceName = locationsData.deviceName.get(thermostatId);
                final ThingUID thermostatUid = new ThingUID(BRIDGE_TYPE_THERMOSTAT, bridgeUid, thermostatId);
                final String thermostatLabel = deviceName + " Thermostat";
                final DiscoveryResult thermostatResult = DiscoveryResultBuilder.create(thermostatUid)
                        .withBridge(bridgeUid).withProperty("locationId", (int) locationId)
                        .withProperty("deviceId", thermostatId).withRepresentationProperty("deviceId")
                        .withLabel(thermostatLabel).build();
                logger.trace("Thermostat: {}@{}", thermostatId, locationId);
                logger.trace("Result: {}", thermostatResult);
                thingDiscovered(thermostatResult);
                try {
                    final HoneywellDiscoveryPriorityData priorityData = new HoneywellDiscoveryPriorityData(
                            honeywellApi.getSensorDiscoveryInfo(locationId, thermostatId));
                    priorityData.accessoryName.forEach((sensorId, name) -> {
                        final String uniqueId = String.format("%s-%s", thermostatId, sensorId);
                        final ThingUID sensorUid = new ThingUID(SENSOR_HONEYWELL_THING, thermostatUid, uniqueId);
                        final String sensorLabel = name;
                        final DiscoveryResult sensorResult = DiscoveryResultBuilder.create(sensorUid)
                                .withBridge(thermostatUid).withProperty("sensorId", sensorId)
                                .withProperty("uniqueId", uniqueId).withRepresentationProperty("uniqueId")
                                .withLabel(sensorLabel).build();
                        logger.trace("Sensor: {}", sensorId);
                        logger.trace("Result: {}", sensorResult);
                        thingDiscovered(sensorResult);
                    });
                } catch (Exception e) {
                    logger.warn("Exception while retrieving priority data: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warn("Exception while retrieving locations data: {}", e.getMessage());
        }
    }
}
