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
import org.openhab.binding.honeywell.internal.config.HoneywellResourceType;
import org.openhab.binding.honeywell.internal.config.HoneywellSensorConfig;
import org.openhab.binding.honeywell.internal.data.HoneywellAccessoryData;
import org.openhab.binding.honeywell.internal.data.HoneywellGroupData;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellCacheProcessor;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;
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
    private final Map<ChannelUID, Consumer<HoneywellAccessoryData>> channelConsumer = new HashMap<>();
    private @Nullable HoneywellGroupData groupData = null;
    private int locationId = 9999999;
    private String deviceId = "";
    private int sensorId = 9;

    public HoneywellSensorHandler(Thing thing, HoneywellHttpClientProvider httpClientProvider) {
        super(thing);
    }

    @Override
    public void initialize() {
        final HoneywellSensorConfig thingConfig = getConfigAs(HoneywellSensorConfig.class);
        locationId = thingConfig.locationId;
        deviceId = thingConfig.deviceId;
        sensorId = thingConfig.sensorId;

        final String uniqueId = String.format("%s-%s", deviceId, sensorId);
        updateProperty("uniqueId", uniqueId);

        bridgeStatusChanged(getBridgeStatus());
        if (null == groupData) {
            return;
        }

        thing.getChannels().forEach(this::createChannel);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Sent command {} for channel {}", command, channelUID);
        if ((null == groupData) || !(command instanceof RefreshType)) {
            return;
        }
        final HoneywellGroupData tempGroupData = groupData;
        if (null != tempGroupData) {
            tempGroupData.updateData();
            HoneywellAccessoryData sensor = tempGroupData.getAccessoryData(sensorId);
            if (null != sensor) {
                final @Nullable Consumer<HoneywellAccessoryData> consumer = channelConsumer.get(channelUID);
                if (null != consumer) {
                    try {
                        consumer.accept(sensor);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        logger.warn("Failed processing result for channel {}: {}", channelUID, e.getMessage());
                    }
                }
            }
            groupData = tempGroupData;
        }

        return;
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("Bridge status changed.");
        groupData = null;
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    "Bridge OFFLINE: " + bridgeStatusInfo.getDescription());
        } else if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Bridge !ONLINE: " + bridgeStatusInfo.getDescription());
        } else {
            final @Nullable HoneywellBridgeHandler bridgeHandler = getBridgeHandler();
            if (null == bridgeHandler) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Bridge handler not found!");
            } else {
                final String groupUrl = bridgeHandler.honeywellUrl(HoneywellResourceType.GROUP, locationId, deviceId);
                try {
                    bridgeHandler.addProcessCache(this, groupUrl);
                    groupData = new HoneywellGroupData((HoneywellConnectionInterface) bridgeHandler, groupUrl);
                } catch (Exception e) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                    groupData = null;
                }
            }
        }
    }

    /**
     * Return the bridge status.
     */
    public ThingStatusInfo getBridgeStatus() {
        final Bridge bridge = getBridge();
        return (null == bridge) ? new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, null)
                : bridge.getStatusInfo();
    }

    /**
     * Return the bride handler.
     */
    public @Nullable HoneywellBridgeHandler getBridgeHandler() {
        final Bridge bridge = getBridge();
        return (null == bridge) ? null : (HoneywellBridgeHandler) bridge.getHandler();
    }

    @Override
    public void processCache() {
        if (channelConsumer.isEmpty() || (null == groupData)) {
            return;
        }
        try {
            final @Nullable HoneywellGroupData tempGroupData = groupData;
            if (null == tempGroupData) {
                return;
            }
            logger.debug("We have group data to comsume");
            tempGroupData.updateData();
            HoneywellAccessoryData sensor = tempGroupData.getAccessoryData(sensorId);
            if (null != sensor) {
                logger.debug("We have sensor data");
                channelConsumer.forEach((channelUID, consumer) -> {
                    try {
                        consumer.accept(sensor);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        logger.warn("Failed processing cache for channel {}: {}", channelUID, e.getMessage());
                    }
                });
            }
            groupData = tempGroupData;
        } catch (Exception e) {
            logger.warn("Exception while retrieving base data: {}", e.getMessage());
        }
    }

    @Override
    public void dispose() {
        final HoneywellBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler != null) {
            bridgeHandler.delProcessCache(this);
        }
        channelConsumer.clear();
        super.dispose();
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
            case "motion":
            case "occupancy":
            case "humidity":
            case "temperature":
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

    private class AccessoryResultPipe {
        private final String resultType;
        private final Consumer<State> consumer;

        public AccessoryResultPipe(String resultType, Consumer<State> consumer) {
            this.resultType = resultType;
            this.consumer = consumer;
        }

        public void process(HoneywellAccessoryData accessoryData) {
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
            try {
                consumer.accept(accessoryChannel);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing result: {}", e.getMessage());
            }
        }
    }
}
