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

import java.util.HashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.honeywell.internal.config.HoneywellSensorConfig;
import org.openhab.binding.honeywell.internal.data.HoneywellAccessoryValueData;
import org.openhab.binding.honeywell.internal.data.HoneywellGroupData;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellCacheProcessor;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellSensorHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellSensorHandler extends BaseThingHandler implements HoneywellCacheProcessor {
    private final Logger logger = LoggerFactory.getLogger(HoneywellSensorHandler.class);
    private final HashMap<ChannelUID, String> resultPipe = new HashMap<>(5);
    private int sensorId = 9;
    private @Nullable HoneywellAccessoryValueData sensorData = null;
    private String uniqueId = "";

    public HoneywellSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        // config setup
        final HoneywellSensorConfig thingConfig = getConfigAs(HoneywellSensorConfig.class);
        sensorId = thingConfig.sensorId;

        // status setup
        bridgeStatusChanged(getBridgeStatus());
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        } else {
            final @Nullable HoneywellThermostatHandler bridgeHandler = getBridgeHandler();
            if (null == bridgeHandler) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Bridge handler not found!");
            } else {
                uniqueId = bridgeHandler.uniqueId(sensorId);
                updateProperty("uniqueId", uniqueId);

                thing.getChannels().forEach(this::createChannel);
                updateStatus(ThingStatus.ONLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                        "Waiting for information from Thermostat");
            }
        }
    }

    /**
     * Return the bridge status.
     */
    private ThingStatusInfo getBridgeStatus() {
        final Bridge bridge = getBridge();
        return (null == bridge) ? new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, null)
                : bridge.getStatusInfo();
    }

    /**
     * Return the bride handler.
     */
    private @Nullable HoneywellThermostatHandler getBridgeHandler() {
        final Bridge bridge = getBridge();
        return (null == bridge) ? null : (HoneywellThermostatHandler) bridge.getHandler();
    }

    /**
     * create all necessary information to handle every channel
     *
     * @param channel a thing channel
     */
    private void createChannel(Channel channel) {
        final ChannelUID channelUID = channel.getUID();
        logger.trace("Creating channel for: {}", channelUID);

        final ChannelTypeUID channelTypeUID = channel.getChannelTypeUID();
        if (channelTypeUID == null) {
            logger.warn("Cannot determine channel-type for channel '{}'", channelUID);
            return;
        }
        resultPipe.put(channelUID, channelTypeUID.getId());
        logger.debug("Pipe created for: {}", channelUID);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Sent command {} for channel {}", command, channelUID);
        if (!(command instanceof RefreshType)) {
            return;
        }
        final HoneywellAccessoryValueData sensor = sensorData;
        if (sensor != null && sensor.isValid()) {
            final String resultType = resultPipe.get(channelUID);
            if (null != resultType) {
                try {
                    process(channelUID, resultType);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    logger.warn("Failed processing result for channel {}: {}", channelUID, e.getMessage());
                }
            }
        }
    }

    @Override
    public void processCache(HoneywellGroupData groupData) {
        logger.debug("Processing group data for {}", uniqueId);
        final @Nullable HoneywellAccessoryValueData sensor = groupData.getAccessoryData(sensorId);
        if (null == sensor || !sensor.isValid()) {
            logger.error("Sensor data for uniqueId '{}' not found in '{}'", uniqueId, groupData.availableSensors());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    String.format("Sensor '{}' not found in available sensors", sensorId));
            return;
        }
        sensorData = sensor;
        // URL and API are valid and the device has a set of valid information
        // remove pending detail
        if (thing.getStatusInfo().getStatusDetail() != ThingStatusDetail.NONE) {
            updateProperties(groupData.getProperties(sensorId));
            updateStatus(ThingStatus.ONLINE);
        }
        processPipe();
    }

    @Override
    public void dispose() {
        resultPipe.clear();
        super.dispose();
    }

    // Simplified sensor processing
    public void processPipe() {
        resultPipe.entrySet().parallelStream().forEach(s -> process(s.getKey(), s.getValue()));
    }

    private void process(ChannelUID channelUID, String resultType) {
        final State state;
        final HoneywellAccessoryValueData sensor = sensorData;
        if (null != sensor) {
            switch (resultType) {
                case "motion":
                    state = sensor.getMotion();
                    break;
                case "occupancy":
                    state = sensor.getOccupancy();
                    break;
                case "humidity":
                    state = sensor.getHumidity();
                    break;
                case "temperature":
                    state = sensor.getTemperature();
                    break;
                case "batterystatus":
                    state = sensor.getBatteryStatus();
                    break;
                default:
                    logger.warn("Unsupported sensor item-type '{}'", resultType);
                    return;
            }
            logger.trace("Sensor result pipe '{}' '{}'", resultType, state);
            try {
                updateState(channelUID, state);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing result: {}", e.getMessage());
            }
        }
    }
}
