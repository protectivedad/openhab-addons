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

import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.*;
import static org.openhab.core.library.unit.ImperialUnits.*;
import static org.openhab.core.library.unit.Units.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * The {@link HoneywellAccessoryValueData} defines the Honeywell api Accessory data
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellAccessoryValueData extends HoneywellAbstractData {
    public class AccessoryValue {
        public int rssiAverage;
        public float indoorTemperature;
        public float indoorHumidity;
        public boolean motionDet;
        public boolean excludeMotion;
        public boolean occupancyDet;
        public String occupancySensitivity = "";
        public String batteryStatus = "";
        public String status = "";
    }

    public static final String CONNECTION_GROUP = "connection";
    public static final String READING_GROUP = "readings";
    public static final String SENSOR_GROUP = "sensor";

    public static final String MOTION = "motion";
    public static final ChannelTypeUID MOTION_TYPE = new ChannelTypeUID("system", MOTION);
    public static final String OCCUPANCY = "occupancy";
    public static final ChannelTypeUID OCCUPANCY_TYPE = new ChannelTypeUID(BINDING_ID, OCCUPANCY);
    public static final String SENSITIVITY = "sensitivity";
    public static final ChannelTypeUID SENSITIVITY_TYPE = new ChannelTypeUID(BINDING_ID, SENSITIVITY);
    public static final String EXCLUDE = "exclude";
    public static final ChannelTypeUID EXCLUDE_TYPE = new ChannelTypeUID(BINDING_ID, EXCLUDE);
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

    private AccessoryValue sensor = new AccessoryValue();

    public void updateData(JsonObject rawJson) throws IOException {
        try {
            super.updateData(rawJson);
            final @Nullable AccessoryValue tempSensor = new Gson().fromJson(rawJson, AccessoryValue.class);
            if (null != tempSensor) {
                sensor = tempSensor;
                setIsValid();
            }
        } catch (Exception e) {
            isValid = false;
            throw new IOException("Accessory value update is not a valid item: " + e.getMessage());
        }
    }

    private boolean status() {
        return HONEYWELL_OK.equals(sensor.status);
    }

    public State getState(String resultType) {
        if (!isValid) {
            return UnDefType.UNDEF;
        }
        switch (resultType) {
            case LOW_BATTERY:
                return HONEYWELL_OK.equals(sensor.batteryStatus) ? OnOffType.OFF : OnOffType.ON;
            case STATUS:
                return status() ? OnOffType.ON : OnOffType.OFF;
        }
        if (!status()) {
            return UnDefType.UNDEF;
        }
        switch (resultType) {
            case SIGNAL_STRENGTH:
                int strength = -1;
                if (sensor.rssiAverage > -40) {
                    strength = 4;
                } else if (sensor.rssiAverage > -55) {
                    strength = 3;
                } else if (sensor.rssiAverage > -70) {
                    strength = 2;
                } else if (sensor.rssiAverage > -85) {
                    strength = 1;
                } else {
                    strength = 0;
                }
                return new DecimalType(strength);
            case MOTION:
                return sensor.motionDet ? OnOffType.ON : OnOffType.OFF;
            case OCCUPANCY:
                return sensor.occupancyDet ? OnOffType.ON : OnOffType.OFF;
            case HUMIDITY:
                return new QuantityType<>(sensor.indoorHumidity, PERCENT);
            case INDOOR_TEMPERATURE:
                return new QuantityType<>(sensor.indoorTemperature, FAHRENHEIT);
            default:
                logger.warn("Unsupported sensor item-type '{}'", resultType);
                return UnDefType.UNDEF;
        }
    }
}
