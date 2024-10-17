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
import org.openhab.binding.honeywell.internal.data.AccessoryData;
import org.openhab.binding.honeywell.internal.data.GroupData;
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
 * @author Jan N. Klug - Initial contribution
 * @author Anthony Sepa - Heavily edited for use in Honeywell binding
 */
@NonNullByDefault
public class HoneywellSensorHandler extends BaseThingHandler implements HoneywellCacheProcessor {
    private final Logger logger = LoggerFactory.getLogger(HoneywellSensorHandler.class);
    private final Map<ChannelUID, Consumer<AccessoryData>> channelConsumer = new HashMap<>();
    private @Nullable GroupData groupData = null;

    public HoneywellSensorHandler(Thing thing, HttpClientProvider httpClientProvider) {
        super(thing);
    }

    @Override
    public void initialize() {
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
        if (!(command instanceof RefreshType)) {
            return;
        }
        final GroupData tempGroupData = groupData;
        if (null != tempGroupData) {
            tempGroupData.updateData();
            final HoneywellSensorConfig thingConfig = getConfigAs(HoneywellSensorConfig.class);
            AccessoryData sensor = tempGroupData.getAccessoryData(thingConfig.sensorId);
            if (null != sensor) {
                Consumer<AccessoryData> consumer = channelConsumer.get(channelUID);
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
            HoneywellBridgeHandler bridgeHandler = getBridgeHandler();
            if (bridgeHandler == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Bridge handler not found!");
            } else {
                final HoneywellConnectionInterface honeywellApi = bridgeHandler.honeywellApi;
                final HoneywellSensorConfig thingConfig = getConfigAs(HoneywellSensorConfig.class);
                final String groupUrl = honeywellApi.honeywellUrl(HoneywellResourceType.GROUP, thingConfig.locationId,
                        thingConfig.deviceId);
                try {
                    honeywellApi.addProcessCache(this, groupUrl);
                    groupData = new GroupData(honeywellApi, groupUrl);
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
        Bridge bridge = getBridge();
        if (bridge != null) {
            return bridge.getStatusInfo();
        } else {
            return new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, null);
        }
    }

    /**
     * Return the bride handler.
     */
    public @Nullable HoneywellBridgeHandler getBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            return null;
        }
        return (HoneywellBridgeHandler) bridge.getHandler();
    }

    @Override
    public void processCache() {
        if (channelConsumer.isEmpty()) {
            return;
        }
        try {
            final GroupData temp = groupData;
            if (null == temp) {
                return;
            }
            temp.updateData();
            final HoneywellSensorConfig thingConfig = getConfigAs(HoneywellSensorConfig.class);
            AccessoryData sensor = temp.getAccessoryData(thingConfig.sensorId);
            if (null != sensor) {
                channelConsumer.forEach((channelUID, consumer) -> {
                    try {
                        consumer.accept(sensor);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        logger.warn("Failed processing cache for channel {}: {}", channelUID, e.getMessage());
                    }
                });
            }
            groupData = temp;
        } catch (Exception e) {
            logger.warn("Exception while retrieving base data: {}", e.getMessage());
        }
    }

    @Override
    public void dispose() {
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
            case "humidity":
            case "temperature":
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

        public void process(AccessoryData accessoryData) {
            final State accessoryChannel;
            switch (resultType) {
                case "motion":
                    accessoryChannel = accessoryData.getMotion();
                    break;
                case "humidity":
                    accessoryChannel = accessoryData.getHumidity();
                    break;
                case "temperature":
                    accessoryChannel = accessoryData.getTemperature();
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
