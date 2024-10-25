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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONException;
import org.openhab.binding.honeywell.internal.config.HoneywellResourceType;
import org.openhab.binding.honeywell.internal.config.HoneywellThermostatConfig;
import org.openhab.binding.honeywell.internal.data.HoneywellAccessoryData;
import org.openhab.binding.honeywell.internal.data.HoneywellDeviceData;
import org.openhab.binding.honeywell.internal.data.HoneywellGroupData;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellCacheProcessor;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellSensorProvider;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellStateDescriptionProvider;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
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
 * @author Anthony Sepa - Initial contribution
 */
// TODO: Think about a TOOMANYREQUEST before any data is loaded including constraints
@NonNullByDefault
public class HoneywellThermostatHandler extends BaseBridgeHandler
        implements HoneywellCacheProcessor, HoneywellSensorProvider {
    private final Logger logger = LoggerFactory.getLogger(HoneywellThermostatHandler.class);
    private final Map<ChannelUID, String> channelTypeId = new HashMap<>();
    private final Map<ChannelUID, Consumer<HoneywellDeviceData>> channelConsumer = new HashMap<>();
    private int locationId = 9999999;
    private String deviceId = "";
    private int groupId = 0;
    private String thermostatUrl = "";
    private final HoneywellDeviceData thermostatData = new HoneywellDeviceData();
    private String groupUrl = "";
    private final HoneywellGroupData groupData = new HoneywellGroupData();
    private final HashMap<HoneywellCacheProcessor, String> cacheConsumers = new HashMap<>(6);

    private final HoneywellStateDescriptionProvider stateDescriptionProvider;

    public HoneywellThermostatHandler(Bridge thing, HoneywellStateDescriptionProvider stateDescriptionProvider) {
        super(thing);
        this.stateDescriptionProvider = stateDescriptionProvider;
    }

    @Override
    public void initialize() {
        // config setup
        final HoneywellThermostatConfig thingConfig = getConfigAs(HoneywellThermostatConfig.class);
        locationId = thingConfig.locationId;
        deviceId = thingConfig.deviceId;
        groupId = thingConfig.groupId;

        // status setup
        bridgeStatusChanged(getBridgeStatus());
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        final @Nullable HoneywellOauth20Handler bridgeHandler = getBridgeHandler();
        if (null == bridgeHandler) {
            return;
        }
        groupUrl = bridgeHandler.honeywellUrl(HoneywellResourceType.GROUP, locationId, deviceId, groupId);
        addCacheProcessor((HoneywellCacheProcessor) childHandler, groupUrl);
        if (groupData.isValid()) {
            ((HoneywellCacheProcessor) childHandler).processCache(groupData);
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        final @Nullable HoneywellOauth20Handler bridgeHandler = getBridgeHandler();
        if (null == bridgeHandler) {
            return;
        }
        delCacheProcessor((HoneywellCacheProcessor) childHandler, groupUrl);
    }

    /**
     * Return the bridge status.
     */
    protected ThingStatusInfo getBridgeStatus() {
        final Bridge bridge = getBridge();
        return (null == bridge) ? new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, null)
                : bridge.getStatusInfo();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            groupUrl = "";
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            groupUrl = "";
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        } else if (bridgeStatusInfo.getStatusDetail() == ThingStatusDetail.CONFIGURATION_PENDING) {
            groupUrl = "";
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
        } else {
            final @Nullable HoneywellOauth20Handler bridgeHandler = getBridgeHandler();
            if (null == bridgeHandler) {
                groupUrl = "";
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Bridge handler not found!");
            } else {
                thermostatUrl = bridgeHandler.honeywellUrl(HoneywellResourceType.THERMOSTAT, locationId, deviceId);
                updateStatus(ThingStatus.ONLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                        "Waiting for information from Honeywell");

                // save processing only setup channels with a bridge
                thing.getChannels().forEach(this::createChannel);
            }
        }
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
        final String acceptedTypeId = channelTypeUID.getId();

        final ThermostatResultPipe thermostatResultPipe;
        switch (acceptedTypeId) {
            case "temperature":
            case "humidity":
            case "mode":
            case "setpointstatus":
            case "nextperiodtime":
            case "heatsetpoint":
            case "coolsetpoint":
                thermostatResultPipe = new ThermostatResultPipe(acceptedTypeId,
                        state -> updateState(channelUID, state));
                break;
            default:
                logger.warn("Unsupported channel-type-id '{}'", acceptedTypeId);
                return;
        }
        channelTypeId.put(channelUID, acceptedTypeId);
        channelConsumer.put(channelUID, thermostatResultPipe::process);
        logger.debug("Channel created for: {}", channelUID);
    }

    @SuppressWarnings("unused")
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Sent command {} for channel {}", command, channelUID);
        if (!thermostatData.isValid()) {
            logger.trace("Thermostat information is not valid");
            return;
        }
        if (command instanceof RefreshType) {
            final @Nullable Consumer<HoneywellDeviceData> consumer = channelConsumer.get(channelUID);
            if (null != consumer) {
                try {
                    consumer.accept(thermostatData);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    logger.warn("Failed processing refresh for channel {}: {}", channelUID, e.getMessage());
                }
            }
            return;
        }
        final @Nullable String acceptedTypeId = channelTypeId.get(channelUID);
        if (null == acceptedTypeId) {
            logger.warn("Cannot find channel implementation for channel {}.", channelUID);
            return;
        }
        final QuantityType<Temperature> setpoint;
        final String returnMsg;
        switch (acceptedTypeId) {
            case "mode":
                logger.debug("Updating mode from {} to {}", thermostatData.getChangeableValues().getMode(),
                        command.toString());
                returnMsg = thermostatData.getChangeableValues().setMode(command.toString());
                break;
            case "setpointstatus":
                logger.debug("Updating setpoint status from {} to {}",
                        thermostatData.getChangeableValues().getSetpointStatus(), command.toString());
                returnMsg = thermostatData.getChangeableValues().setSetpointStatus(command.toString());
                break;
            case "heatsetpoint":
                setpoint = new QuantityType<>(command.toString());
                returnMsg = thermostatData.getChangeableValues().setHeatSetpoint(setpoint);
                break;
            case "coolsetpoint":
                setpoint = new QuantityType<>(command.toString());
                returnMsg = thermostatData.getChangeableValues().setCoolSetpoint(setpoint);
                break;
            case "nextperiodtime":
                logger.debug("Updating next period time to {}", command.toString());
                returnMsg = thermostatData.getChangeableValues().setNextPeriodTime(command.toString());
                break;
            default:
                logger.warn("Unsupported channel-type-id '{}'", acceptedTypeId);
                return;
        }
        // update failed
        if (!returnMsg.isEmpty()) {
            logger.warn("Failed to update of channel '{}' to command '{}'", acceptedTypeId, command.toString());
            return;
        }
        try {
            final HoneywellConnectionInterface api = getBridgeHandler();
            if (null == api) {
                throw new IOException();
            }
            api.postHttpHoneywell(thermostatUrl, thermostatData.getChangeableValues().toJson());
        } catch (IOException e) {
            logger.warn("I/O error posting update: '{}'", e.getMessage());
        } catch (JSONException e) {
            logger.warn("ITEM error posting update: '{}'", e.getMessage());
        } catch (IllegalStateException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    String.format("Configuration error posting update: '{}'", e.getMessage()));
            delCacheProcessor();
        }
    }

    private void updateDynamicStates() {
        logger.trace("Setting allowed modes {}", thermostatData.getChangeableValues().getAllowedModes());
        stateDescriptionProvider.setStateOptions(new ChannelUID(getThing().getUID(), "mode"),
                thermostatData.getChangeableValues().getAllowedModes());
        stateDescriptionProvider.setStatePattern(new ChannelUID(getThing().getUID(), "heatSetpoint"),
                thermostatData.getSetpointPattern());
        stateDescriptionProvider.setStatePattern(new ChannelUID(getThing().getUID(), "coolSetpoint"),
                thermostatData.getSetpointPattern());
    }

    /**
     * Return the bride handler.
     */
    protected @Nullable HoneywellOauth20Handler getBridgeHandler() {
        final Bridge bridge = getBridge();
        return (null == bridge) ? null : (HoneywellOauth20Handler) bridge.getHandler();
    }

    private void delCacheProcessor() {
        final HoneywellOauth20Handler bridgeHandler = getBridgeHandler();
        if (null != bridgeHandler) {
            bridgeHandler.delCacheProcessor(this, groupUrl);
        }
    }

    @Override
    public void processCache(String honeywellUrl, String rawString) {
        if (honeywellUrl.equals(groupUrl)) {
            try {
                groupData.updateData(rawString);
            } catch (Exception e) {
                logger.warn("Exception while retrieving group data: {}", e.getMessage());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                delCacheProcessor();
                return;
            }
            for (HoneywellCacheProcessor key : cacheConsumers.keySet()) {
                key.processCache(groupData);
            }
            return;
        }

        try {
            thermostatData.updateData(rawString);
        } catch (Exception e) {
            logger.warn("Exception while retrieving thermostat data: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            delCacheProcessor();
            return;
        }
        updateDynamicStates();

        // URL and API are valid and the device has a set of valid information
        // remove pending detail
        if (thing.getStatusInfo().getStatusDetail() != ThingStatusDetail.NONE) {
            updateStatus(ThingStatus.ONLINE);
        }

        if (channelConsumer.isEmpty()) {
            return;
        }
        channelConsumer.forEach((channelUID, consumer) -> {
            try {
                consumer.accept(thermostatData);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing cache for channel {}: {}", channelUID, e.getMessage());
            }
        });
    }

    @Override
    public void dispose() {
        channelConsumer.clear();
        channelTypeId.clear();
        super.dispose();
    }

    private class ThermostatResultPipe {
        private final String resultType;
        private final Consumer<State> consumer;

        public ThermostatResultPipe(String resultType, Consumer<State> consumer) {
            this.resultType = resultType;
            this.consumer = consumer;
        }

        public void process(HoneywellDeviceData thermostatData) {
            final State thermostatState;
            switch (resultType) {
                case "temperature":
                    thermostatState = thermostatData.getTemperature();
                    break;
                case "humidity":
                    thermostatState = thermostatData.getHumidity();
                    break;
                case "mode":
                    thermostatState = thermostatData.getChangeableValues().getMode();
                    break;
                case "setpointstatus":
                    thermostatState = thermostatData.getChangeableValues().getSetpointStatus();
                    break;
                case "nextperiodtime":
                    thermostatState = thermostatData.getChangeableValues().getNextPeriodTime();
                    break;
                case "heatsetpoint":
                    thermostatState = thermostatData.getChangeableValues().getHeatSetpoint();
                    break;
                case "coolsetpoint":
                    thermostatState = thermostatData.getChangeableValues().getCoolSetpoint();
                    break;
                default:
                    logger.warn("Unsupported thermostat item-type '{}'", resultType);
                    return;
            }
            logger.trace("Thermostat result pipe '{}' '{}'", resultType, thermostatState);
            try {
                consumer.accept(thermostatState);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing result: {}", e.getMessage());
            }
            return;
        }
    }

    @Override
    public String uniqueId(int sensorId) {
        return String.format("%s-%s", deviceId, sensorId);
    }

    @Override
    public @Nullable HoneywellAccessoryData sensor(int sensorId) {
        return groupData.getAccessoryData(sensorId);
    }

    // Add the cache processor first removing the oldone and any unneeded data
    @Override
    public void addCacheProcessor(HoneywellCacheProcessor cacheProcessor, String groupUrl) {
        logger.debug("Registering cache URL: {}", groupUrl);
        cacheConsumers.put(cacheProcessor, groupUrl);
        final HoneywellOauth20Handler bridgeHandler = getBridgeHandler();
        if (null == bridgeHandler) {
            return;
        }
        bridgeHandler.addCacheProcessor(this, groupUrl);
    }

    // Remove the cache processor and cache if last processor
    @Override
    public void delCacheProcessor(HoneywellCacheProcessor cacheProcessor, String honeywellUrl) {
        logger.debug("Removing cache processor");
        if (cacheConsumers.containsKey(cacheProcessor)) {
            logger.trace("Removing cache URL: {}", honeywellUrl);
            cacheConsumers.remove(cacheProcessor);
            // TODO: cache the groupdata to allow more than one group feed
            // if (!cacheConsumers.containsValue(honeywellUrl)) {
            // if (cachedData.containsKey(honeywellUrl)) {
            // logger.debug("Removing cache data");
            // cachedData.remove(honeywellUrl);
            // }
            // }
        }
    }
}
