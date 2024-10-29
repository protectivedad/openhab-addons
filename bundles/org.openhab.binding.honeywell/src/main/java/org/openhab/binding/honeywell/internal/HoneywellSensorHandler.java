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
import java.util.Map;
import java.util.function.Consumer;

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
    private final Map<ChannelUID, Consumer<HoneywellAccessoryValueData>> channelConsumer = new HashMap<>();
    private int sensorId = 9;
    private @Nullable HoneywellGroupData groupData = null;
    private String uniqueId = "";

    public HoneywellSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        // config setup
        final HoneywellSensorConfig thingConfig = getConfigAs(HoneywellSensorConfig.class);
        sensorId = thingConfig.sensorId;
        groupData = null;

        // status setup
        bridgeStatusChanged(getBridgeStatus());
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        } else if (bridgeStatusInfo.getStatusDetail() == ThingStatusDetail.CONFIGURATION_PENDING) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
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
    protected ThingStatusInfo getBridgeStatus() {
        final Bridge bridge = getBridge();
        return (null == bridge) ? new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, null)
                : bridge.getStatusInfo();
    }

    /**
     * Return the bride handler.
     */
    protected @Nullable HoneywellThermostatHandler getBridgeHandler() {
        final Bridge bridge = getBridge();
        return (null == bridge) ? null : (HoneywellThermostatHandler) bridge.getHandler();
    }

    /**
     * create all necessary information to handle every channel
     *
     * @param channel a thing channel
     */
    private void createChannel(Channel channel) {
        ChannelUID channelUID = channel.getUID();
        logger.trace("Creating channel for: {}", channelUID);

        final ChannelTypeUID channelTypeUID = channel.getChannelTypeUID();
        if (channelTypeUID == null) {
            logger.warn("Cannot determine channel-type for channel '{}'", channelUID);
            return;
        }
        final String acceptedTypeId = channelTypeUID.getId();

        final AccessoryResultPipe accessoryResultPipe;
        switch (acceptedTypeId) {
            case "temperature":
            case "humidity":
            case "motion":
            case "occupancy":
            case "batterystatus":
                accessoryResultPipe = new AccessoryResultPipe(acceptedTypeId, state -> updateState(channelUID, state));
                break;
            default:
                logger.warn("Unsupported channel-type-id '{}'", acceptedTypeId);
                return;
        }
        channelConsumer.put(channelUID, accessoryResultPipe::process);
        logger.debug("Channel created for: {}", channelUID);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Sent command {} for channel {}", command, channelUID);
        final HoneywellGroupData groupData = this.groupData;
        if (null == groupData) {
            return;
        }
        final @Nullable HoneywellAccessoryValueData sensorData = groupData.getAccessoryData(sensorId);
        if (null == sensorData) {
            return;
        }
        final @Nullable Consumer<HoneywellAccessoryValueData> consumer = channelConsumer.get(channelUID);
        if (null != consumer) {
            try {
                consumer.accept(sensorData);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing result for channel {}: {}", channelUID, e.getMessage());
            }
        }
    }

    @Override
    public void processCache(HoneywellGroupData groupData) {
        logger.debug("Processing group data for {}", uniqueId);
        this.groupData = groupData;
        final HoneywellAccessoryValueData sensor = groupData.getAccessoryData(sensorId);
        if (null == sensor) {
            logger.error("Sensor data for uniqueId '{}' not found in '{}'", uniqueId, groupData.availableSensors());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    String.format("Sensor '{}' not found in available sensors", sensorId));
            return;
        }

        // URL and API are valid and the device has a set of valid information
        // remove pending detail
        if (thing.getStatusInfo().getStatusDetail() != ThingStatusDetail.NONE) {
            final Map<String, String> properties = groupData.getProperties(sensorId);
            if (null != properties) {
                updateProperties(properties);
            }
            updateStatus(ThingStatus.ONLINE);
        }

        if (channelConsumer.isEmpty()) {
            return;
        }
        channelConsumer.forEach((channelUID, consumer) -> {
            try {
                consumer.accept(sensor);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing cache for channel {}: {}", channelUID, e.getMessage());
            }
        });
    }

    @Override
    public void dispose() {
        channelConsumer.clear();
        super.dispose();
    }

    private class AccessoryResultPipe {
        private final String resultType;
        private final Consumer<State> consumer;

        public AccessoryResultPipe(String resultType, Consumer<State> consumer) {
            this.resultType = resultType;
            this.consumer = consumer;
        }

        public void process(HoneywellAccessoryValueData accessoryData) {
            final State accessoryChannel;
            switch (resultType) {
                case "motion":
                    accessoryChannel = accessoryData.getMotion();
                    break;
                case "occupancy":
                    accessoryChannel = accessoryData.getOccupancy();
                    break;
                case "humidity":
                    accessoryChannel = accessoryData.getHumidity();
                    break;
                case "temperature":
                    accessoryChannel = accessoryData.getTemperature();
                    break;
                case "batterystatus":
                    accessoryChannel = accessoryData.getBatteryStatus();
                    break;
                default:
                    logger.warn("Unsupported sensor item-type '{}'", resultType);
                    return;
            }
            logger.trace("Sensor result pipe '{}' '{}'", resultType, accessoryChannel);
            try {
                consumer.accept(accessoryChannel);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing result: {}", e.getMessage());
            }
        }
    }
}
