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

import static org.openhab.binding.honeywell.internal.HoneywellOauth20Handler.*;
import static org.openhab.binding.honeywell.internal.data.HoneywellChangeableValuesData.*;
import static org.openhab.binding.honeywell.internal.data.HoneywellDeviceData.*;
import static org.openhab.binding.honeywell.internal.data.HoneywellScheduleData.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.honeywell.internal.config.HoneywellThermostatConfig;
import org.openhab.binding.honeywell.internal.data.HoneywellAccessoryValueData;
import org.openhab.binding.honeywell.internal.data.HoneywellDeviceData;
import org.openhab.binding.honeywell.internal.data.HoneywellGroupData;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellCacheProcessor;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellSensorProvider;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellStateDescriptionProvider;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellThermostatHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellThermostatHandler extends BaseBridgeHandler
        implements HoneywellCacheProcessor, HoneywellSensorProvider {
    private final Logger logger = LoggerFactory.getLogger(HoneywellThermostatHandler.class);
    private final Map<ChannelUID, String> resultPipe = new HashMap<>();

    private @NonNullByDefault({}) HoneywellOauth20Handler bridgeHandler;

    // Device cache information that gets comsumed
    private @NonNullByDefault({}) long locationId;
    private @NonNullByDefault({}) String deviceId;
    private @NonNullByDefault({}) int groupId;

    // Downloaded data
    private final HoneywellDeviceData thermostatData = new HoneywellDeviceData();
    private final HoneywellGroupData groupData = new HoneywellGroupData();

    private final Set<HoneywellCacheProcessor> groupConsumers = new HashSet<>(6);

    private final HoneywellStateDescriptionProvider stateDescriptionProvider;

    public HoneywellThermostatHandler(Bridge thing, HoneywellStateDescriptionProvider stateDescriptionProvider) {
        super(thing);
        this.stateDescriptionProvider = stateDescriptionProvider;
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        // config setup
        final HoneywellThermostatConfig thingConfig = getConfigAs(HoneywellThermostatConfig.class);
        locationId = thingConfig.locationId;
        deviceId = thingConfig.deviceId;
        groupId = thingConfig.groupId;

        this.bridgeHandler = (HoneywellOauth20Handler) getBridge().getHandler();

        // status setup
        bridgeStatusChanged();

        createChannels();
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        groupConsumers.add((HoneywellCacheProcessor) childHandler);
        if (groupData.isValid()) {
            ((HoneywellCacheProcessor) childHandler).processCache(groupData);
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        groupConsumers.remove((HoneywellCacheProcessor) childHandler);
    }

    private void bridgeStatusChanged() {
        final Bridge bridge = getBridge();
        bridgeStatusChanged(
                (null == bridge) ? new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, null)
                        : bridge.getStatusInfo());
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        } else {
            updateStatus(ThingStatus.ONLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "Waiting for information from Honeywell");
        }
    }

    private void createChannels() {
        List<Channel> channels = new ArrayList<>();
        ThingBuilder thingBuilder = editThing();
        ThingUID thingUID = thing.getUID();
        channels.addAll(HoneywellDeviceData.getChannels(thingUID));
        thingBuilder.withChannels(channels);
        updateThing(thingBuilder.build());
        resultPipe.clear();
        thing.getChannels().forEach(this::createChannel);
    }

    /**
     * create all necessary information to handle every channel
     *
     * @param channel a thing channel
     */
    private void createChannel(Channel channel) {
        final ChannelUID channelUID = channel.getUID();

        final ChannelTypeUID channelTypeUID = channel.getChannelTypeUID();
        if (channelTypeUID == null) {
            logger.warn("Cannot determine channel-type for channel '{}'", channelUID);
            return;
        }
        resultPipe.put(channelUID, channelTypeUID.getId());
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (!thermostatData.isValid()) {
            logger.trace("Thermostat information is not valid");
            return;
        }
        if (resultPipe.containsKey(channelUID)) {
            try {
                process(channelUID, resultPipe.getOrDefault(channelUID, ""), command);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing channel {}: {}", channelUID, e.getMessage());
            }
        } else {
            logger.warn("Failed to locate channel: '{}'", channelUID);
        }
    }

    private void onetimeUpdateStates() {
        resultPipe.forEach((channelUID, resultType) -> {
            switch (resultType) {
                case MODE:
                    stateDescriptionProvider.setStateOptions(channelUID,
                            thermostatData.getChangeableValues().getAllowedModes());
                    break;
                case HEATSETPOINT:
                    stateDescriptionProvider.setMinMaxStep(channelUID,
                            thermostatData.getChangeableValues().getHeatSetpointMinMaxStep(),
                            thermostatData.getSetpointPattern());
                    break;
                case COOLSETPOINT:
                    stateDescriptionProvider.setMinMaxStep(channelUID,
                            thermostatData.getChangeableValues().getCoolSetpointMinMaxStep(),
                            thermostatData.getSetpointPattern());
                    break;
            }
        });
    }

    private void updateDynamicStates() {
        resultPipe.forEach((channelUID, resultType) -> {
            switch (resultType) {
                case SETPOINTSTATUS:
                    stateDescriptionProvider.setStateOptions(channelUID,
                            thermostatData.getScheduleData().getAllowedSetpointStatus());
                    break;
                default:
                    break;
            }
        });
    }

    @Override
    public void processCache(String rawString) {
        try {
            thermostatData.updateData(rawString);
            updateDynamicStates();

            // URL and API are valid and the device has a set of valid information
            // remove pending detail
            if (thing.getStatusInfo().getStatusDetail() != ThingStatusDetail.NONE) {
                onetimeUpdateStates();
                updateProperties(thermostatData.getProperties());
                @Nullable
                String location = thing.getLocation();
                location = (null == location || location.isEmpty()) ? thing.getProperties().get("roomName") : location;
                thing.setLocation(location);
                updateStatus(ThingStatus.ONLINE);
            }
            processPipe();

            if (!groupConsumers.isEmpty()) {
                groupData.updateData(bridgeHandler.getFromHoneywell(
                        bridgeHandler.honeywellUrl(HONEYWELL_GROUP_URL, locationId, deviceId, groupId)));
                for (HoneywellCacheProcessor key : groupConsumers) {
                    key.processCache(groupData);
                }
            }
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    @Override
    public void dispose() {
        resultPipe.clear();
        super.dispose();
    }

    public void processPipe() {
        resultPipe.entrySet().parallelStream().forEach(s -> process(s.getKey(), s.getValue(), RefreshType.REFRESH));
    }

    private void process(ChannelUID channelUID, String resultType, Command command) {
        if (command instanceof RefreshType) {
            updateState(channelUID, thermostatData.getState(resultType));
            return;
        }
        String retString = thermostatData.setState(resultType, command.toString());
        if (retString.isEmpty()) {
            switch (resultType) {
                case SCHEDULESTATUS:
                    try {
                        if (UnDefType.UNDEF == thermostatData.isScheduleStatus()) {
                            throw new IOException();
                        }
                        if (OnOffType.OFF == thermostatData.isScheduleStatus()) {
                            retString = bridgeHandler.putHttpHoneywell(
                                    bridgeHandler.honeywellUrl(HONEYWELL_SCHEDULE_PAUSE_URL, locationId, deviceId), "");
                            if (retString.isEmpty()) {
                                thermostatData.getScheduleData().updateAllowedSetpointStatus();
                                retString = thermostatData.setState(SETPOINTSTATUS, PERMANENTHOLD);
                                if (retString.isEmpty()) {
                                    retString = thermostatData.setState(NEXTPERIODTIME, "");
                                }
                            }
                        } else {
                            retString = bridgeHandler.putHttpHoneywell(
                                    bridgeHandler.honeywellUrl(HONEYWELL_SCHEDULE_RESUME_URL, locationId, deviceId),
                                    "");
                            if (retString.isEmpty()) {
                                thermostatData.getScheduleData().updateAllowedSetpointStatus();
                                retString = thermostatData.setState(SETPOINTSTATUS, NOHOLD);
                            }
                        }
                        if (retString.isEmpty()) {
                            updateDynamicStates();
                            processPipe();
                        } else {
                            logger.warn("I/O error posting update: '{}'", retString);
                        }
                    } catch (IOException e) {
                        logger.warn("I/O error posting update: '{}'", e.getMessage());
                    } catch (IllegalStateException e) {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                                String.format("Configuration error posting update: '%s'", e.getMessage()));
                    }
                    return;
                default:
                    try {
                        bridgeHandler.postHttpHoneywell(
                                bridgeHandler.honeywellUrl(HONEYWELL_THERMOSTAT_URL, locationId, deviceId),
                                thermostatData.getChangeableValues().toJson());
                    } catch (IOException e) {
                        logger.warn("I/O error posting update: '{}'", e.getMessage());
                    } catch (IllegalStateException e) {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                                String.format("Configuration error posting update: '%s'", e.getMessage()));
                    }
                    return;
            }
        } else {
            logger.warn("Failed to update of channel '{}' to command '{}' received '{}'", resultType,
                    command.toString(), retString);
        }
    }

    @Override
    public String uniqueId(int sensorId) {
        return HoneywellSensorProvider.uniqueId(deviceId, sensorId);
    }

    @Override
    public @Nullable HoneywellAccessoryValueData sensor(int sensorId) {
        return groupData.getAccessoryData(sensorId);
    }
}
