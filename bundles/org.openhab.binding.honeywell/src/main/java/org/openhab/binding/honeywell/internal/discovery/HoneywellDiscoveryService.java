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
import org.openhab.binding.honeywell.internal.honeywell.HoneywellSensorProvider;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellDiscoveryService} is responsible processing the
 * results of searches for Honeywell devices
 *
 * @author Anthony Sepa - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = HoneywellDiscoveryService.class)
@NonNullByDefault
public class HoneywellDiscoveryService extends AbstractThingHandlerDiscoveryService<HoneywellOauth20Handler> {
    private Logger logger = LoggerFactory.getLogger(HoneywellDiscoveryService.class);

    private static final int SEARCH_TIMEOUT_SECONDS = 0;
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = new HashSet<ThingTypeUID>();
    static {
        SUPPORTED_THING_TYPES_UIDS.add(HONEYWELL_THERMOSTAT_BRIDGE);
        SUPPORTED_THING_TYPES_UIDS.add(HONEYWELL_SENSOR_THING);
    }

    private @Nullable ScheduledFuture<?> discoveryFuture;

    public HoneywellDiscoveryService() {
        super(HoneywellOauth20Handler.class, SUPPORTED_THING_TYPES_UIDS, SEARCH_TIMEOUT_SECONDS, false);
    }

    @Override
    public void initialize() {
        thingHandler.registerDiscoveryThings(this::discoveryThings);
        super.initialize();
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
    public void startScan() {
        logger.debug("Starting scan job");
        final ScheduledFuture<?> job = discoveryFuture;
        if (job == null || job.isDone()) {
            discoveryFuture = scheduler.schedule(this::discoveryThings, 1, TimeUnit.SECONDS);
        }
    }

    public void discoveryThings() {
        if (thingHandler.getThing().getStatus() != ThingStatus.ONLINE || !thingHandler.isAuthorized()) {
            return;
        }
        final HoneywellOauth20Handler honeywellApi = thingHandler;
        final ThingUID bridgeUid = thingHandler.getThing().getUID();
        try {
            final HoneywellDiscoveryLocationsData locationsData = new HoneywellDiscoveryLocationsData(
                    honeywellApi.getThermostatDiscoveryInfo());
            locationsData.deviceLocation.forEach((thermostatId, locationId) -> {
                final @Nullable String deviceName = locationsData.deviceName.get(thermostatId);
                final ThingUID thermostatUid = new ThingUID(HONEYWELL_THERMOSTAT_BRIDGE, bridgeUid, thermostatId);
                final String thermostatLabel = deviceName + " Thermostat";
                final DiscoveryResult thermostatResult = DiscoveryResultBuilder.create(thermostatUid)
                        .withBridge(bridgeUid).withProperty("locationId", (int) locationId)
                        .withProperty("deviceId", thermostatId).withRepresentationProperty("deviceId")
                        .withLabel(thermostatLabel).build();
                thingDiscovered(thermostatResult);
                try {
                    final HoneywellDiscoveryPriorityData priorityData = new HoneywellDiscoveryPriorityData(
                            honeywellApi.getSensorDiscoveryInfo(locationId, thermostatId));
                    priorityData.accessoryDetails.forEach((sensorId, details) -> {
                        final String uniqueId = HoneywellSensorProvider.uniqueId(thermostatId, sensorId);
                        final ThingUID sensorUid = new ThingUID(HONEYWELL_SENSOR_THING, thermostatUid, uniqueId);
                        final @Nullable String sensorType = details.get(0);
                        final @Nullable String sensorLabel = details.get(1);
                        final DiscoveryResult sensorResult = DiscoveryResultBuilder.create(sensorUid)
                                .withBridge(thermostatUid).withProperty("sensorId", sensorId)
                                .withProperty("uniqueId", uniqueId).withRepresentationProperty("uniqueId")
                                .withProperty("type", sensorType).withLabel(sensorLabel).build();
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
