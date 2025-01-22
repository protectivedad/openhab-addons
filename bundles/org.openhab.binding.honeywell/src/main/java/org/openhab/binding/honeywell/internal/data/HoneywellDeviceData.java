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
import static org.openhab.binding.honeywell.internal.data.HoneywellChangeableValuesData.*;
import static org.openhab.binding.honeywell.internal.data.HoneywellFanData.*;
import static org.openhab.core.library.unit.ImperialUnits.FAHRENHEIT;
import static org.openhab.core.library.unit.SIUnits.CELSIUS;
import static org.openhab.core.library.unit.Units.PERCENT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;

import com.google.gson.JsonObject;

/**
 * The {@link HoneywellDeviceData} defines the Honeywell api Device data
 * { "operationStatus":{ "mode":"EquipmentOff", "fanRequest":false, "circulationFanRequest":false},
 * "outdoorTemperature":9, "macID":"112233445566", "indoorHumidityStatus":"Measured",
 * "deviceOsVersion":"RCHT9610WFW2004", "units":"Celsius", "maxHeatSetpoint":32,
 * "currentSchedulePeriod":{ "period":"Home", "day":"Monday"}, "indoorHumidity":56, "changeSource":{ "by":"partner",
 * "name":"openHAB No Callback"}, "vacationHold":{ "enabled":false}, "scheduleStatus":"Resume", "deadband":0,
 * "hasDualSetpointStatus":false, "indoorTemperature":23, "inBuiltSensorState":{ "roomId":1, "roomName":"Dining
 * Room"}, "deviceSettings":{}, "deviceRegistrationDate":"2021-11-25T04:06:56.6366667", "deviceType":"Thermostat",
 * "settings":{ "specialMode":{}, "devicePairingEnabled":true,
 * "temperatureMode":{"air":true}, "hardwareSettings":{"brightness":0,"maxBrightness":0}},
 * "allowedModes":["Heat","Off"], "deviceInternalID":3712863, "dataSyncStatus":"Completed",
 * "groups":[{"rooms":[0,1],"name":"default","id":0}], "scheduleCapabilities":{
 * "availableScheduleTypes":["None","Geofenced", "TimedNorthAmerica"], "schedulableFan":true},
 * "allowedTimeIncrements":15, "deviceID":"LCC-112233445566", "priorityType":"PickARoom",
 * "userDefinedDeviceName":"Dining
 * Room","minHeatSetpoint":10,"isAlive":true,"scheduleType":{"scheduleType":"Geofence"},"deviceSerialNo":"9999XX999999",
 * "service":{ "mode":"Up"}, "changeableValues":{ "mode":"Heat", "endCoolSetpoint":null, "heatCoolMode":"Heat",
 * "endHeatSetpoint":null, "thermostatSetpointStatus":"NoHold", "heatSetpoint":16.5, "coolSetpoint":25.5,
 * "nextPeriodTime":"22:00:00" }, "name":"Dining Room", "isUpgrading":false, "deviceClass":"Thermostat",
 * "deviceModel":"T9-T10", "maxCoolSetpoint":-18, "displayedOutdoorHumidity":82, "isProvisioned":true,
 * "minCoolSetpoint":-18}
 * 
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellDeviceData extends HoneywellAbstractData {
    public static final String MEASUREMENT_GROUP = "measurements";
    public static final String SETTING_GROUP = "settings";

    public static final String OUTDOOR_TEMPERATURE = "outdoor-temperature";
    public static final ChannelTypeUID OUTDOOR_TEMPERATURE_TYPE = new ChannelTypeUID("system", OUTDOOR_TEMPERATURE);
    public static final String ATMOSPHERIC_HUMIDITY = "atmospheric-humidity";
    public static final ChannelTypeUID ATMOSPHERIC_HUMIDITY_TYPE = new ChannelTypeUID("system", ATMOSPHERIC_HUMIDITY);
    public static final String SCHEDULESTATUS = "schedulestatus";
    public static final ChannelTypeUID SCHEDULESTATUS_TYPE = new ChannelTypeUID(BINDING_ID, SCHEDULESTATUS);

    private static final List<String> HONEYWELL_DEVICE_CONTRAINTS_LIST = new ArrayList<String>();
    static {
        HONEYWELL_DEVICE_CONTRAINTS_LIST.add("allowedModes");
        HONEYWELL_DEVICE_CONTRAINTS_LIST.add("allowedTimeIncrements");
        HONEYWELL_DEVICE_CONTRAINTS_LIST.add("minHeatSetpoint");
        HONEYWELL_DEVICE_CONTRAINTS_LIST.add("maxHeatSetpoint");
        HONEYWELL_DEVICE_CONTRAINTS_LIST.add("minCoolSetpoint");
        HONEYWELL_DEVICE_CONTRAINTS_LIST.add("maxCoolSetpoint");
    }
    private static final List<String> HONEYWELL_DEVICE_PROPERTIES_LIST = new ArrayList<String>();
    static {
        HONEYWELL_DEVICE_PROPERTIES_LIST.add("deviceType");
        HONEYWELL_DEVICE_PROPERTIES_LIST.add("deviceClass");
        HONEYWELL_DEVICE_PROPERTIES_LIST.add("deviceModel");
        HONEYWELL_DEVICE_PROPERTIES_LIST.add("deviceOsVersion");
        HONEYWELL_DEVICE_PROPERTIES_LIST.add("deviceSerialNo");
        HONEYWELL_DEVICE_PROPERTIES_LIST.add("macID");
        HONEYWELL_DEVICE_PROPERTIES_LIST.add("name");
    }

    private final List<StateOption> allowedSetpointStatus = new ArrayList<>();
    private final HoneywellChangeableValuesData changeableValues = new HoneywellChangeableValuesData(
            allowedSetpointStatus);
    private final HoneywellScheduleData scheduleData = new HoneywellScheduleData(allowedSetpointStatus);
    private final HoneywellFanData fanData = new HoneywellFanData();
    private final JsonObject deviceAttributes = new JsonObject();
    private final JsonObject constraintsJson = new JsonObject();

    private float outdoorTemperature = 0;
    private float displayedOutdoorHumidity = 0;
    private float temperature = 0;
    private float humidity = -1;
    private Unit<Temperature> units = CELSIUS;

    /**
     * Update the inforation and mark the information as valid
     * 
     * @throws JSONException - If the information received isn't valid
     * @throws IOException - If there is a problem with API or URL getting the information
     */
    public synchronized void updateData(String rawContent) throws IOException {
        logger.trace("Raw DeviceData: '{}'", rawContent);
        if (HONEYWELL_BLANK_JSON.equals(rawContent)) {
            throw new IOException();
        }
        super.updateData(rawContent);
        try {
            outdoorTemperature = rawObject.get("outdoorTemperature").getAsFloat();
            displayedOutdoorHumidity = rawObject.get("displayedOutdoorHumidity").getAsFloat();
            temperature = rawObject.get("indoorTemperature").getAsFloat();
            if (rawObject.has("indoorHumidity")) {
                humidity = rawObject.get("indoorHumidity").getAsFloat();
            }
            final Unit<Temperature> newUnits = rawObject.get("units").getAsString().equals("Celsius") ? CELSIUS
                    : FAHRENHEIT;

            scheduleData.updateData(rawObject);
            if (rawObject.getAsJsonObject("settings").has("fan")) {
                fanData.updateData(rawObject.getAsJsonObject("settings").getAsJsonObject("fan"));
            }
            // save processing we only use these once so only process them until the device is valid
            if (!isValid() || !newUnits.equals(units)) {
                units = newUnits;
                for (String c : HONEYWELL_DEVICE_CONTRAINTS_LIST) {
                    constraintsJson.add(c, rawObject.get(c));
                }
                for (String c : HONEYWELL_DEVICE_PROPERTIES_LIST) {
                    deviceAttributes.add(c, rawObject.get(c));
                }
                if (rawObject.has("inBuiltSensorState")) {
                    deviceAttributes.addProperty("roomName",
                            rawObject.get("inBuiltSensorState").getAsJsonObject().get("roomName").getAsString());
                }
                changeableValues.updateData(rawObject.get("changeableValues").getAsJsonObject(), units,
                        constraintsJson);
            } else {
                changeableValues.updateData(rawObject.get("changeableValues").getAsJsonObject());
            }
        } catch (Exception e) {
            isValid = false;
            if (isError()) {
                throw new IOException(rawObject.toString());
            }
            logger.error("Rawdata: {}", rawObject.toString());
            throw new IOException("Thermostat data received from Honeywell not understood: " + e.getMessage());
        }
        setIsValid();
    }

    public List<Channel> getChannels(ThingUID thingUID) {
        List<Channel> retChannels = new ArrayList<>();
        ChannelGroupUID groupUID = new ChannelGroupUID(thingUID, MEASUREMENT_GROUP);
        retChannels.add(ChannelBuilder.create(new ChannelUID(groupUID, OUTDOOR_TEMPERATURE), "Number:Temperature")
                .withType(OUTDOOR_TEMPERATURE_TYPE).build());
        retChannels.add(ChannelBuilder.create(new ChannelUID(groupUID, ATMOSPHERIC_HUMIDITY), "Number:Dimensionless")
                .withType(ATMOSPHERIC_HUMIDITY_TYPE).build());
        retChannels.add(ChannelBuilder.create(new ChannelUID(groupUID, INDOOR_TEMPERATURE), "Number:Temperature")
                .withType(INDOOR_TEMPERATURE_TYPE).build());
        if (hasHumidity()) {
            retChannels.add(ChannelBuilder.create(new ChannelUID(groupUID, HUMIDITY), "Number:Dimensionless")
                    .withType(HUMIDITY_TYPE).build());
        }
        groupUID = new ChannelGroupUID(thingUID, SETTING_GROUP);
        retChannels.addAll(changeableValues.getChannels(thingUID, groupUID));
        retChannels.add(ChannelBuilder.create(new ChannelUID(groupUID, SCHEDULESTATUS), "String")
                .withType(SCHEDULESTATUS_TYPE).build());
        if (fanData.isValid()) {
            retChannels.add(ChannelBuilder.create(new ChannelUID(groupUID, FAN_MODE), "String").withType(FAN_MODE_TYPE)
                    .build());
        }
        return retChannels;
    }

    private boolean hasHumidity() {
        return (-1 != humidity);
    }

    public boolean isValid() {
        return super.isValid() && changeableValues.isValid() && scheduleData.isValid();
    }

    public HoneywellChangeableValuesData getChangeableValues() {
        return changeableValues;
    }

    public HoneywellScheduleData getScheduleData() {
        return scheduleData;
    }

    public HoneywellFanData getFanData() {
        return fanData;
    }

    public Map<String, String> getProperties() {
        final Map<String, String> stringMap = deviceAttributes.asMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getAsString()));
        return (isValid()) ? stringMap : Collections.emptyMap();
    }

    public String getSetpointPattern() {
        return (FAHRENHEIT == units) ? "%.0f %unit%" : "%.1f %unit%";
    }

    public State getState(String resultType) {
        if (!isValid()) {
            return UnDefType.UNDEF;
        }
        switch (resultType) {
            case OUTDOOR_TEMPERATURE:
                return new QuantityType<>(outdoorTemperature, units);
            case ATMOSPHERIC_HUMIDITY:
                return new QuantityType<>(displayedOutdoorHumidity, PERCENT);
            case INDOOR_TEMPERATURE:
                return new QuantityType<>(temperature, units);
            case HUMIDITY:
                return new QuantityType<>(humidity, PERCENT);
            case CHANGEABLEVALUES_MODE:
            case CHANGEABLEVALUES_SETPOINTSTATUS:
            case CHANGEABLEVALUES_NEXTPERIODTIME:
            case CHANGEABLEVALUES_HEATSETPOINT:
            case CHANGEABLEVALUES_COOLSETPOINT:
                return getChangeableValues().getState(resultType);
            case SCHEDULESTATUS:
                return getScheduleData().getScheduleStatus();
            case FAN_MODE:
                return getFanData().getState();
            default:
                logger.warn("Unsupported thermostat item-type '{}'", resultType);
                return UnDefType.UNDEF;
        }
    }

    public State isScheduleStatus() {
        return getScheduleData().isScheduleStatus();
    }

    public String setState(String resultType, String cmdString) {
        switch (resultType) {
            case CHANGEABLEVALUES_MODE:
            case CHANGEABLEVALUES_SETPOINTSTATUS:
            case CHANGEABLEVALUES_NEXTPERIODTIME:
            case CHANGEABLEVALUES_HEATSETPOINT:
            case CHANGEABLEVALUES_COOLSETPOINT:
                return getChangeableValues().setState(resultType, cmdString);
            case SCHEDULESTATUS:
                return getScheduleData().setScheduleStatus(cmdString);
            case FAN_MODE:
                return getFanData().setState(cmdString);
            default:
                logger.warn("Unsupported thermostat item-type '{}'", resultType);
                return "Unsupported thermostat item-type: " + resultType;
        }
    }
}
