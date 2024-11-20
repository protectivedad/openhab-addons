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
package org.openhab.binding.honeywell.internal.data;

import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.BINDING_ID;
import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.HUMIDITY;
import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.HUMIDITY_TYPE;
import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.INDOOR_TEMPERATURE;
import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.INDOOR_TEMPERATURE_TYPE;
import static org.openhab.core.library.unit.ImperialUnits.*;
import static org.openhab.core.library.unit.Units.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

import com.google.gson.JsonObject;

/**
 * The {@link HoneywellAccessoryValueData} defines the Honeywell api Accessory data
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellAccessoryValueData extends HoneywellAbstractData {
    public static final String CONNECTION_GROUP = "connection";
    public static final String READING_GROUP = "readings";
    public static final String SENSOR_GROUP = "sensor";

    public static final String MOTION = "motion";
    public static final ChannelTypeUID MOTION_TYPE = new ChannelTypeUID("system", MOTION);
    public static final String OCCUPANCY = "occupancy";
    public static final ChannelTypeUID OCCUPANCY_TYPE = new ChannelTypeUID(BINDING_ID, OCCUPANCY);
    public static final String LOW_BATTERY = "low-battery";
    public static final ChannelTypeUID LOW_BATTERY_TYPE = new ChannelTypeUID("system", LOW_BATTERY);
    public static final String SIGNAL_STRENGTH = "signal-strength";
    public static final ChannelTypeUID SIGNAL_STRENGTH_TYPE = new ChannelTypeUID("system", SIGNAL_STRENGTH);
    public static final String STATUS = "status";
    public static final ChannelTypeUID STATUS_TYPE = new ChannelTypeUID(BINDING_ID, STATUS);

    public static List<Channel> getChannels(ThingUID thingUID, boolean isSensor) {
        List<Channel> retChannels = new ArrayList<>();
        ChannelGroupUID groupUID = new ChannelGroupUID(thingUID, READING_GROUP);
        retChannels.add(ChannelBuilder.create(new ChannelUID(groupUID, INDOOR_TEMPERATURE), "Number:Temperature")
                .withType(INDOOR_TEMPERATURE_TYPE).build());
        retChannels.add(ChannelBuilder.create(new ChannelUID(groupUID, HUMIDITY), "Number:Dimensionless")
                .withType(HUMIDITY_TYPE).build());
        if (isSensor) {
            groupUID = new ChannelGroupUID(thingUID, SENSOR_GROUP);
            retChannels.add(
                    ChannelBuilder.create(new ChannelUID(groupUID, MOTION), "Switch").withType(MOTION_TYPE).build());
            retChannels.add(ChannelBuilder.create(new ChannelUID(groupUID, OCCUPANCY), "Switch")
                    .withType(OCCUPANCY_TYPE).build());
            retChannels.add(ChannelBuilder.create(new ChannelUID(groupUID, LOW_BATTERY), "Switch")
                    .withType(LOW_BATTERY_TYPE).build());
        }
        groupUID = new ChannelGroupUID(thingUID, CONNECTION_GROUP);
        retChannels.add(ChannelBuilder.create(new ChannelUID(groupUID, SIGNAL_STRENGTH), "Number")
                .withType(SIGNAL_STRENGTH_TYPE).build());
        retChannels
                .add(ChannelBuilder.create(new ChannelUID(groupUID, STATUS), "Switch").withType(STATUS_TYPE).build());
        return retChannels;
    }

    private float rssiAverage = 0;
    private float temperature = 0;
    private float humidity = 0;
    private boolean motion = false;
    private boolean occupancy = false;
    private boolean batteryStatus = true;
    private boolean status = false;

    public void updateData(JsonObject rawJson) throws IOException {
        try {
            super.updateData(rawJson);
            status = HONEYWELL_OK.equals(rawObject.get("status").getAsString());
            batteryStatus = !HONEYWELL_OK.equals(rawObject.get("batteryStatus").getAsString());
            if (status) {
                temperature = rawObject.get("indoorTemperature").getAsFloat();
                humidity = rawObject.get("indoorHumidity").getAsFloat();
                rssiAverage = rawObject.get("rssiAverage").getAsFloat();
                motion = rawObject.get("motionDet").getAsBoolean();
                occupancy = rawObject.get("occupancyDet").getAsBoolean();
            }
        } catch (Exception e) {
            isValid = false;
            throw new IOException("Accessory value update is not a valid item: " + e.getMessage());
        }
        setIsValid();
    }

    public State getState(String resultType) {
        if (!isValid) {
            return UnDefType.UNDEF;
        }
        switch (resultType) {
            case LOW_BATTERY:
                return batteryStatus ? OnOffType.ON : OnOffType.OFF;
            case STATUS:
                return status ? OnOffType.ON : OnOffType.OFF;
        }
        if (!status) {
            return UnDefType.UNDEF;
        }
        switch (resultType) {
            case SIGNAL_STRENGTH:
                int strength = -1;
                if (rssiAverage > -50) {
                    strength = 4;
                } else if (rssiAverage > -60) {
                    strength = 3;
                } else if (rssiAverage > -70) {
                    strength = 2;
                } else if (rssiAverage > -80) {
                    strength = 1;
                } else {
                    strength = 0;
                }
                return new DecimalType(strength);
            case MOTION:
                return motion ? OnOffType.ON : OnOffType.OFF;
            case OCCUPANCY:
                return occupancy ? OnOffType.ON : OnOffType.OFF;
            case HUMIDITY:
                return new QuantityType<>(humidity, PERCENT);
            case INDOOR_TEMPERATURE:
                return new QuantityType<>(temperature, FAHRENHEIT);
            default:
                logger.warn("Unsupported sensor item-type '{}'", resultType);
                return UnDefType.UNDEF;
        }
    }
}
