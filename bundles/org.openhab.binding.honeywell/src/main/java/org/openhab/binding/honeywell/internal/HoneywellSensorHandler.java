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

import static org.openhab.binding.honeywell.internal.data.HoneywellAccessoryAttributeData.*;

import java.util.HashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.honeywell.internal.config.HoneywellSensorConfig;
import org.openhab.binding.honeywell.internal.data.HoneywellAccessoryValueData;
import org.openhab.binding.honeywell.internal.data.HoneywellGroupData;
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
public class HoneywellSensorHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(HoneywellSensorHandler.class);
    private final HashMap<ChannelUID, String> resultPipe = new HashMap<>(5);
    private @NonNullByDefault({}) int sensorId;
    private HoneywellAccessoryValueData sensorData = new HoneywellAccessoryValueData();

    public HoneywellSensorHandler(Thing thing) {
        super(thing);
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        // config setup
        final HoneywellSensorConfig thingConfig = getConfigAs(HoneywellSensorConfig.class);
        sensorId = thingConfig.sensorId;

        final Bridge bridge = getBridge();

        // status setup
        bridgeStatusChanged(bridge.getStatusInfo());
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        } else {
            updateStatus(ThingStatus.ONLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "Waiting for information from Thermostat");
        }
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
        if (!(command instanceof RefreshType)) {
            return;
        }
        if (sensorData.isValid()) {
            try {
                process(channelUID, resultPipe.getOrDefault(channelUID, "not-found"));
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.warn("Failed processing result for channel {}: {}", channelUID, e.getMessage());
            }
        }
    }

    public void processCache(HoneywellGroupData groupData) {
        final @Nullable HoneywellAccessoryValueData sensor = groupData.getAccessoryData(sensorId);
        if (null == sensor || !sensor.isValid()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    String.format("Sensor '%s' not found in available sensors", sensorId));
            return;
        }
        sensorData = sensor;

        // Sensor data is valid and the device has a set of valid information
        // create channels and remove pending detail
        if (thing.getStatusInfo().getStatusDetail() != ThingStatusDetail.NONE) {
            updateProperties(groupData.getProperties(sensorId));
            @Nullable
            String location = thing.getLocation();
            location = (null == location || location.isEmpty()) ? thing.getProperties().get("roomName") : location;
            thing.setLocation(location);
            updateThing(
                    editThing()
                            .withChannels(HoneywellAccessoryValueData.getChannels(thing.getUID(),
                                    HONEYWELL_ACCESSORY_TYPE_SENSOR.equals(thing.getProperties().get("type"))))
                            .build());
            resultPipe.clear();
            thing.getChannels().forEach(this::createChannel);
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
        final State state = sensorData.getState(resultType);
        try {
            updateState(channelUID, state);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Failed processing result: {}", e.getMessage());
        }
    }
}
