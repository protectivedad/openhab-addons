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

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.honeywell.internal.config.HoneywellResourceType;
import org.openhab.binding.honeywell.internal.config.HoneywellThermostatConfig;
import org.openhab.binding.honeywell.internal.data.HoneywellDeviceData;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellCacheProcessor;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;
import org.openhab.core.library.types.QuantityType;
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
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellThermostatHandler extends BaseThingHandler implements HoneywellCacheProcessor {
    private final Logger logger = LoggerFactory.getLogger(HoneywellThermostatHandler.class);
    private final Map<ChannelUID, String> channelTypeId = new HashMap<>();
    private final Map<ChannelUID, Consumer<HoneywellDeviceData>> channelConsumer = new HashMap<>();
    private @Nullable HoneywellDeviceData thermostatData = null;

    public HoneywellThermostatHandler(Thing thing, HoneywellHttpClientProvider httpClientProvider) {
        super(thing);
    }

    @Override
    public void initialize() {
        bridgeStatusChanged(getBridgeStatus());
        if (null == thermostatData) {
            return;
        }
        thing.getChannels().forEach(this::createChannel);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Sent command {} for channel {}", command, channelUID);
        final HoneywellDeviceData tempThermostatData = thermostatData;
        if (null == tempThermostatData) {
            return;
        }
        if (command instanceof RefreshType) {
            tempThermostatData.updateData();
            final @Nullable Consumer<HoneywellDeviceData> consumer = channelConsumer.get(channelUID);
            if (null != consumer) {
                try {
                    consumer.accept(tempThermostatData);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    logger.warn("Failed processing refresh for channel {}: {}", channelUID, e.getMessage());
                }
            }
            thermostatData = tempThermostatData;
            return;
        }
        final @Nullable String acceptedTypeId = channelTypeId.get(channelUID);
        if (null == acceptedTypeId) {
            logger.warn("Cannot find channel implementation for channel {}.", channelUID);
            return;
        }
        final QuantityType<Temperature> setpoint;
        switch (acceptedTypeId) {
            case "thermostat-mode":
                logger.debug("Updating mode from {} to {}", tempThermostatData.getMode(), command.toString());
                try {
                    tempThermostatData.setMode(command.toString());
                } catch (Exception e) {
                    logger.warn("Unable to assign mode: {}", e.getMessage());
                }
                break;
            case "setpointstatus":
                logger.debug("Updating setpoint status from {} to {}", tempThermostatData.getSetpointStatus(),
                        command.toString());
                try {
                    tempThermostatData.setSetpointStatus(command.toString());
                } catch (Exception e) {
                    logger.warn("Unable to assign setpoint status: {}", e.getMessage());
                }
                break;
            case "heatsetpoint":
                setpoint = new QuantityType<>(command.toString());
                tempThermostatData.setHeatSetpoint(setpoint);
                break;
            case "coolsetpoint":
                setpoint = new QuantityType<>(command.toString());
                tempThermostatData.setCoolSetpoint(setpoint);
                break;
            case "nextperiodtime":
                logger.debug("Updating next period time to {}", command.toString());
                tempThermostatData.setNextPeriodTime(command.toString());
                break;
            default:
                logger.warn("Unsupported channel-type-id '{}'", acceptedTypeId);
                return;
        }
        tempThermostatData.postUpdate();
        thermostatData = tempThermostatData;
        return;
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("Bridge status changed.");
        thermostatData = null;
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        } else {
            final @Nullable HoneywellBridgeHandler bridgeHandler = getBridgeHandler();
            if (null == bridgeHandler) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Bridge handler not found!");
            } else {
                final HoneywellThermostatConfig thingConfig = getConfigAs(HoneywellThermostatConfig.class);
                final String thermostatUrl = bridgeHandler.honeywellUrl(HoneywellResourceType.THERMOSTAT,
                        thingConfig.locationId, thingConfig.deviceId);
                try {
                    bridgeHandler.addProcessCache(this, thermostatUrl);
                    thermostatData = new HoneywellDeviceData((HoneywellConnectionInterface) bridgeHandler,
                            thermostatUrl);
                } catch (Exception e) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                    thermostatData = null;
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
        if (channelConsumer.isEmpty()) {
            return;
        }
        try {
            final HoneywellDeviceData tempThermostatData = thermostatData;
            if (null == tempThermostatData) {
                return;
            }
            tempThermostatData.updateData();
            channelConsumer.forEach((channelUID, consumer) -> {
                try {
                    consumer.accept(tempThermostatData);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    logger.warn("Failed processing cache for channel {}: {}", channelUID, e.getMessage());
                }
            });
            thermostatData = tempThermostatData;
        } catch (Exception e) {
            logger.warn("Exception while retrieving thermostat data: {}", e.getMessage());
        }
    }

    @Override
    public void dispose() {
        final HoneywellBridgeHandler bridgeHandler = getBridgeHandler();
        if (null != bridgeHandler) {
            bridgeHandler.delProcessCache(this);
        }
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
            case "thermostat-mode":
            case "heatsetpoint":
            case "coolsetpoint":
            case "humidity":
            case "temperature":
            case "setpointstatus":
            case "nextperiodtime":
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
                case "thermostat-mode":
                    thermostatState = thermostatData.getMode();
                    break;
                case "heatsetpoint":
                    thermostatState = thermostatData.getHeatSetpoint();
                    break;
                case "coolsetpoint":
                    thermostatState = thermostatData.getCoolSetpoint();
                    break;
                case "humidity":
                    thermostatState = thermostatData.getHumidity();
                    break;
                case "temperature":
                    thermostatState = thermostatData.getTemperature();
                    break;
                case "setpointstatus":
                    thermostatState = thermostatData.getSetpointStatus();
                    break;
                case "nextperiodtime":
                    thermostatState = thermostatData.getNextPeriodTime();
                    break;
                default:
                    logger.warn("Unsupported thermostat item-type '{}'", resultType);
                    return;
            }
            try {
                consumer.accept(thermostatState);
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing result: {}", e.getMessage());
            }
            return;
        }
    }
}
