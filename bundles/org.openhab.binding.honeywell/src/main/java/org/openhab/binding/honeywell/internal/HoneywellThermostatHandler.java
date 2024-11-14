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
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONException;
import org.openhab.binding.honeywell.internal.config.HoneywellResourceType;
import org.openhab.binding.honeywell.internal.config.HoneywellThermostatConfig;
import org.openhab.binding.honeywell.internal.data.HoneywellAccessoryValueData;
import org.openhab.binding.honeywell.internal.data.HoneywellDeviceData;
import org.openhab.binding.honeywell.internal.data.HoneywellGroupData;
import org.openhab.binding.honeywell.internal.data.HoneywellScheduleData.ScheduleStatus;
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

    // Device cache information that gets comsumed
    private int locationId = 9999999;
    private String deviceId = "";
    private String thermostatUrl = "";
    // private String scheduleUrl = "";
    private String scheduleResumeUrl = "";
    private String schedulePauseUrl = "";
    private final HoneywellDeviceData thermostatData = new HoneywellDeviceData();

    // Group cache information that gets passed on
    private int groupId = 0;
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
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        } else {
            final @Nullable HoneywellOauth20Handler bridgeHandler = getBridgeHandler();
            if (null == bridgeHandler) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Bridge handler not found!");
            } else {
                thermostatUrl = bridgeHandler.honeywellUrl(HoneywellResourceType.THERMOSTAT, locationId, deviceId);
                schedulePauseUrl = bridgeHandler.honeywellUrl(HoneywellResourceType.SCHEDULE_PAUSE, locationId,
                        deviceId);
                scheduleResumeUrl = bridgeHandler.honeywellUrl(HoneywellResourceType.SCHEDULE_RESUME, locationId,
                        deviceId);
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
        resultPipe.put(channelUID, channelTypeUID.getId());
        logger.debug("Channel created for: {}", channelUID);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Sent command {} for channel {}", command, channelUID);
        if (!thermostatData.isValid()) {
            logger.trace("Thermostat information is not valid");
            return;
        }
        final @Nullable String resultType = resultPipe.get(channelUID);
        if (null != resultType) {
            try {
                process(channelUID, resultType, command);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing channel {}: {}", channelUID, e.getMessage());
            }
        }
    }

    private void updateDynamicStates() {
        logger.debug("Setting dynamic states for {}", deviceId);
        resultPipe.forEach((channelUID, resultType) -> {
            switch (resultType) {
                case "mode":
                    logger.trace("Setting allowed mode options for: '{}' with: '{}'", channelUID,
                            thermostatData.getChangeableValues().getAllowedOptions());
                    stateDescriptionProvider.setStateOptions(channelUID,
                            thermostatData.getChangeableValues().getAllowedOptions());

                    break;
                case "heatsetpoint":
                    @Nullable
                    List<BigDecimal> heatMinMaxStep = thermostatData.getChangeableValues().getHeatSetpointMinMaxStep();
                    if (null != heatMinMaxStep) {
                        logger.trace("Setting heat setpoint state for: '{}' with min/max/step: '{}'", channelUID,
                                heatMinMaxStep);
                        stateDescriptionProvider.setMinMaxStep(channelUID, heatMinMaxStep,
                                thermostatData.getSetpointPattern());
                    }
                    break;
                case "coolsetpoint":
                    @Nullable
                    List<BigDecimal> coolMinMaxStep = thermostatData.getChangeableValues().getCoolSetpointMinMaxStep();
                    if (null != coolMinMaxStep) {
                        logger.trace("Setting cool setpoint state for: '{}' with min/max/step: '{}'", channelUID,
                                coolMinMaxStep);
                        stateDescriptionProvider.setMinMaxStep(channelUID, coolMinMaxStep,
                                thermostatData.getSetpointPattern());
                    }
                    break;
                default:
                    break;
            }
        });
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
        logger.debug("Processing thermostat data for {}", deviceId);
        logger.trace("Received URL: '{}'", honeywellUrl);
        logger.trace("Group URL: '{}'", groupUrl);
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

        if (honeywellUrl.equals(thermostatUrl)) {
            try {
                thermostatData.updateData(rawString);
            } catch (Exception e) {
                logger.warn("Exception while retrieving thermostat data: {}", e.getMessage());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                delCacheProcessor();
                return;
            }

            // URL and API are valid and the device has a set of valid information
            // remove pending detail
            if (thing.getStatusInfo().getStatusDetail() != ThingStatusDetail.NONE) {
                updateDynamicStates();
                updateProperties(thermostatData.getProperties());
                updateStatus(ThingStatus.ONLINE);
            }
            processPipe();
            return;
        }
        logger.warn("Thermostat recevied information it didn't signup for, ignoring");
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
        String cmdString = (command instanceof RefreshType) ? "" : command.toString();
        State state = UnDefType.UNDEF;
        String string = "";
        switch (resultType) {
            case "outtemperature":
                state = thermostatData.getOutdoorTemperature();
                break;
            case "outhumidity":
                state = thermostatData.getOutdoorHumidity();
                break;
            case "temperature":
                state = thermostatData.getTemperature();
                break;
            case "humidity":
                state = thermostatData.getHumidity();
                break;
            case "schedulestatus":
                if (cmdString.isEmpty()) {
                    state = thermostatData.getScheduleData().getScheduleStatus();
                } else {
                    string = thermostatData.getScheduleData().setScheduleStatus(cmdString);
                    if (string.isEmpty()) {
                        try {
                            final HoneywellConnectionInterface api = getBridgeHandler();
                            if (null == api) {
                                throw new IOException();
                            }
                            if (ScheduleStatus.get(cmdString).equals(Optional.of(ScheduleStatus.OFF))) {
                                string = api.putHttpHoneywell(schedulePauseUrl, "");
                            } else if (ScheduleStatus.get(cmdString).equals(Optional.of(ScheduleStatus.ON))) {
                                string = api.putHttpHoneywell(scheduleResumeUrl, "");
                            }
                            logger.trace("Schedule status update returned: '{}'", string);
                        } catch (IOException e) {
                            logger.warn("I/O error posting update: '{}'", e.getMessage());
                        } catch (JSONException e) {
                            logger.warn("ITEM error posting update: '{}'", e.getMessage());
                        } catch (IllegalStateException e) {
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                                    String.format("Configuration error posting update: '{}'", e.getMessage()));
                            delCacheProcessor();
                        }
                        return;
                    }
                }
                break;
            case "mode":
                if (cmdString.isEmpty()) {
                    state = thermostatData.getChangeableValues().getMode();
                } else {
                    string = thermostatData.getChangeableValues().setMode(cmdString);
                }
                break;
            case "setpointstatus":
                if (cmdString.isEmpty()) {
                    state = thermostatData.getChangeableValues().getSetpointStatus();
                } else {
                    string = thermostatData.getChangeableValues().setSetpointStatus(cmdString);
                }
                break;
            case "nextperiodtime":
                if (cmdString.isEmpty()) {
                    state = thermostatData.getChangeableValues().getNextPeriodTime();
                } else {
                    string = thermostatData.getChangeableValues().setNextPeriodTime(cmdString);
                }
                break;
            case "heatsetpoint":
                if (cmdString.isEmpty()) {
                    state = thermostatData.getChangeableValues().getHeatSetpoint();
                } else {
                    string = thermostatData.getChangeableValues()
                            .setHeatSetpoint(new QuantityType<Temperature>(cmdString));
                }
                break;
            case "coolsetpoint":
                if (cmdString.isEmpty()) {
                    state = thermostatData.getChangeableValues().getCoolSetpoint();
                } else {
                    string = thermostatData.getChangeableValues()
                            .setCoolSetpoint(new QuantityType<Temperature>(cmdString));
                }
                break;
            default:
                logger.warn("Unsupported thermostat item-type '{}'", resultType);
                return;
        }
        logger.trace("Thermostat result pipe '{}' '{}'", resultType, state);
        if (cmdString.isEmpty()) {
            try {
                updateState(channelUID, state);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing result: {}", e.getMessage());
            }
        } else if (string.isEmpty()) {
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
        } else {
            logger.warn("Failed to update of channel '{}' to command '{}' received '{}'", resultType, cmdString,
                    string);
        }
    }

    @Override
    public String uniqueId(int sensorId) {
        logger.trace("deviceId, sensorId, uniqueId: {}, {}, {}", deviceId, sensorId,
                HoneywellSensorProvider.uniqueId(deviceId, sensorId));
        return HoneywellSensorProvider.uniqueId(deviceId, sensorId);
    }

    @Override
    public @Nullable HoneywellAccessoryValueData sensor(int sensorId) {
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
        }
    }
}
