/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.honeywell.internal.config.HoneywellChannelConfig;
import org.openhab.binding.honeywell.internal.config.HoneywellResourceType;
import org.openhab.binding.honeywell.internal.config.HoneywellScheduleConfig;
import org.openhab.binding.honeywell.internal.converter.AbstractTransformingItemConverter;
import org.openhab.binding.honeywell.internal.converter.GenericItemConverter;
import org.openhab.binding.honeywell.internal.converter.ItemValueConverter;
import org.openhab.binding.honeywell.internal.data.ScheduleData;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellCacheProcessor;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;
import org.openhab.binding.honeywell.internal.transform.ValueTransformationProvider;
import org.openhab.core.library.types.StringType;
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
 * The {@link HoneywellThermostatHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jan N. Klug - Initial contribution
 * @author Anthony Sepa - Heavily edited for use in Honeywell binding
 */
@NonNullByDefault
public class HoneywellScheduleHandler extends BaseThingHandler implements HoneywellCacheProcessor {
    private final Logger logger = LoggerFactory.getLogger(HoneywellScheduleHandler.class);
    private final ValueTransformationProvider valueTransformationProvider;
    private final Map<ChannelUID, ItemValueConverter> channels = new HashMap<>();
    private final Map<ChannelUID, String> channelTypeId = new HashMap<>();
    private final Map<ChannelUID, Consumer<ScheduleData>> channelConsumer = new HashMap<>();
    private @Nullable ScheduleData scheduleData = null;

    public HoneywellScheduleHandler(Thing thing, HttpClientProvider httpClientProvider,
            ValueTransformationProvider valueTransformationProvider) {
        super(thing);
        this.valueTransformationProvider = valueTransformationProvider;
    }

    @Override
    public void initialize() {
        bridgeStatusChanged(getBridgeStatus());
        if (null == scheduleData) {
            return;
        }
        thing.getChannels().forEach(this::createChannel);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Sent command {} for channel {}", command, channelUID);
        ItemValueConverter itemValueConverter = channels.get(channelUID);
        if (null == itemValueConverter) {
            final ScheduleData temp = scheduleData;
            if (null == temp) {
                return;
            }
            temp.updateData();
            if (command instanceof RefreshType) {
                Consumer<ScheduleData> consumer = channelConsumer.get(channelUID);
                if (null != consumer) {
                    try {
                        consumer.accept(temp);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        logger.warn("Failed processing result for channel {}: {}", channelUID, e.getMessage());
                    }
                }
                scheduleData = temp;
                return;
            }
            final String acceptedTypeId = channelTypeId.get(channelUID);
            if (null == acceptedTypeId) {
                logger.warn("Cannot find channel implementation for channel {}.", channelUID);
                scheduleData = temp;
                return;
            }
            switch (acceptedTypeId) {
                case "schedule-type":
                    logger.debug("Updating mode from {} to {}", temp.getScheduleType(), command.toString());
                    try {
                        temp.setScheduleType(command.toString());
                    } catch (Exception e) {
                        logger.warn("Unable to assign mode: {}", e.getMessage());
                    }
                    break;
                default:
                    logger.warn("Unsupported channel-type-id '{}'", acceptedTypeId);
                    return;
            }
            temp.postUpdate();
            scheduleData = temp;
            return;
        } else {
            try {
                itemValueConverter.send(command);
            } catch (IllegalArgumentException e) {
                logger.warn("Failed to convert command '{}' to channel '{}' for sending", command, channelUID);
            } catch (IllegalStateException e) {
                logger.debug("Writing to read-only channel {} not permitted", channelUID);
            }
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("Bridge status changed.");
        scheduleData = null;
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        } else {
            final HoneywellBridgeHandler bridgeHandler = getBridgeHandler();
            if (bridgeHandler == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Bridge handler not found!");
            } else {
                final HoneywellConnectionInterface honeywellApi = bridgeHandler.honeywellApi;
                final HoneywellScheduleConfig thingConfig = getConfigAs(HoneywellScheduleConfig.class);
                final String thermostatUrl = honeywellApi.honeywellUrl(HoneywellResourceType.SCHEDULE,
                        thingConfig.locationId, thingConfig.deviceId);
                honeywellApi.addProcessCache(this, thermostatUrl);
                scheduleData = new ScheduleData(honeywellApi, thermostatUrl);
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
            final ScheduleData temp = scheduleData;
            if (null == temp) {
                return;
            }
            temp.updateData();
            channelConsumer.forEach((channelUID, consumer) -> {
                try {
                    consumer.accept(temp);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    logger.warn("Failed processing result for channel {}: {}", channelUID, e.getMessage());
                }
            });
            logger.debug("Thermostat {}", temp.deviceID);
            scheduleData = temp;
        } catch (Exception e) {
            logger.warn("Exception while retrieving base data: {}", e.getMessage());
        }
    }

    @Override
    public void dispose() {
        final HoneywellBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler != null) {
            final HoneywellConnectionInterface honeywellApi = bridgeHandler.honeywellApi;
            honeywellApi.delProcessCache(this);
        }
        channels.clear();
        channelConsumer.clear();
        channelTypeId.clear();
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

        HoneywellChannelConfig channelConfig = channel.getConfiguration().as(HoneywellChannelConfig.class);
        final ItemValueConverter itemValueConverter;
        final ScheduleResultPipe scheduleResultPipe;
        switch (acceptedTypeId) {
            case "schedule-raw":
                itemValueConverter = createGenericItemConverter(channelUID, channelConfig, StringType::new);
                scheduleResultPipe = new ScheduleResultPipe(acceptedTypeId, itemValueConverter::process);
                channels.put(channelUID, itemValueConverter);
                break;
            default:
                logger.warn("Unsupported channel-type-id '{}'", acceptedTypeId);
                return;
        }
        channelTypeId.put(channelUID, acceptedTypeId);
        channelConsumer.put(channelUID, scheduleResultPipe::process);
        logger.debug("Channel created for: {}", channelUID);
    }

    private void sendHttpValue(String command) {
        final ScheduleData temp = scheduleData;
        if (null == temp) {
            return;
        }
        try {
            temp.postUpdate();
            scheduleData = temp;
        } catch (Exception e) {
            logger.warn("Error updating thermostat: {}", e.getMessage());
        }
    }

    private ItemValueConverter createItemConverter(AbstractTransformingItemConverter.Factory factory,
            ChannelUID channelUID, HoneywellChannelConfig channelConfig) {
        return factory.create(state -> updateState(channelUID, state), command -> postCommand(channelUID, command),
                command -> sendHttpValue(command),
                valueTransformationProvider.getValueTransformation(channelConfig.stateTransformation),
                valueTransformationProvider.getValueTransformation(channelConfig.commandTransformation), channelConfig);
    }

    private ItemValueConverter createGenericItemConverter(ChannelUID channelUID, HoneywellChannelConfig channelConfig,
            Function<String, State> toState) {
        AbstractTransformingItemConverter.Factory factory = (state, command, value, stateTrans, commandTrans,
                config) -> new GenericItemConverter(toState, state, command, value, stateTrans, commandTrans, config);
        return createItemConverter(factory, channelUID, channelConfig);
    }

    private class ScheduleResultPipe {
        private final String resultType;
        private final Consumer<String> consumer;

        public ScheduleResultPipe(String resultType, Consumer<String> consumer) {
            this.resultType = resultType;
            this.consumer = consumer;
        }

        public void process(ScheduleData scheduleData) {
            final String scheduleString;
            switch (resultType) {
                case "schedule-raw":
                    scheduleString = scheduleData.toString();
                    break;
                default:
                    logger.warn("Unsupported schedule item-type '{}'", resultType);
                    return;
            }
            try {
                consumer.accept(scheduleString);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing result: {}", e.getMessage());
            }
            return;
        }
    }
}
