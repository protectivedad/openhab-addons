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
import static org.openhab.core.library.unit.SIUnits.*;
import static org.openhab.core.library.unit.Units.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

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
    }

    private float outdoorTemperature = 0;
    private float displayedOutdoorHumidity = 0;
    private float temperature = 0;
    private float humidity = 0;
    private Unit<Temperature> units = CELSIUS;

    private final HoneywellChangeableValuesData changeableValues = new HoneywellChangeableValuesData();
    private final HoneywellScheduleData scheduleData = new HoneywellScheduleData();
    private final JSONObject deviceAttributes = new JSONObject();
    private final JSONObject constraintsJson = new JSONObject();

    /**
     * Update the inforation and mark the information as valid
     * 
     * @throws JSONException - If the information received isn't valid
     * @throws IOException - If there is a problem with API or URL getting the information
     */
    public synchronized void updateData(String rawContent) throws JSONException, IOException {
        logger.trace("Raw DeviceData: '{}'", rawContent);
        if (HONEYWELL_BLANK_JSON.equals(rawContent)) {
            throw new IOException();
        }
        super.updateData(rawContent);
        try {
            outdoorTemperature = rawObject.getFloat("outdoorTemperature");
            displayedOutdoorHumidity = rawObject.getFloat("displayedOutdoorHumidity");
            temperature = rawObject.getFloat("indoorTemperature");
            humidity = rawObject.getFloat("indoorHumidity");
            final Unit<Temperature> newUnits = rawObject.getString("units").equals("Celsius") ? CELSIUS : FAHRENHEIT;
            // save processing we only use these once so only process them until the device is valid
            if (!isValid() || !newUnits.equals(units)) {
                units = newUnits;
                for (String c : HONEYWELL_DEVICE_CONTRAINTS_LIST) {
                    constraintsJson.put(c, rawObject.get(c));
                }
                for (String c : HONEYWELL_DEVICE_PROPERTIES_LIST) {
                    deviceAttributes.put(c, rawObject.get(c));
                }
                changeableValues.updateData(rawObject.getJSONObject("changeableValues"), units, constraintsJson);
            } else {
                changeableValues.updateData(rawObject.getJSONObject("changeableValues"));
            }
            scheduleData.updateData(rawObject);
        } catch (Exception e) {
            isValid = false;
            logger.warn("rawObject: {}", rawObject.toString());
            if (isError()) {
                throw new JSONException(rawObject.toString());
            }
            rawObject.keys().forEachRemaining(key -> {
                logger.error("{}: {}", key, rawObject.get(key));
            });
            throw new JSONException("Data received from Honeywell not understood, see error log: " + e.getMessage());
        }
        setIsValid();
    }

    public boolean isValid() {
        return super.isValid() && changeableValues.isValid() && scheduleData.isValid();
    }

    public State getOutdoorHumidity() {
        return (isValid()) ? new QuantityType<>(displayedOutdoorHumidity, PERCENT) : UnDefType.UNDEF;
    }

    public State getOutdoorTemperature() {
        return (isValid()) ? new QuantityType<>(outdoorTemperature, units) : UnDefType.UNDEF;
    }

    public State getTemperature() {
        return (isValid()) ? new QuantityType<>(temperature, units) : UnDefType.UNDEF;
    }

    public State getHumidity() {
        return (isValid()) ? new QuantityType<>(humidity, PERCENT) : UnDefType.UNDEF;
    }

    public HoneywellChangeableValuesData getChangeableValues() {
        return changeableValues;
    }

    public HoneywellScheduleData getScheduleData() {
        return scheduleData;
    }

    public Map<String, String> getProperties() {
        final Map<String, String> stringMap = deviceAttributes.toMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue()));
        return (isValid()) ? stringMap : Collections.emptyMap();
    }

    public String getSetpointPattern() {
        return (FAHRENHEIT == units) ? "%.0f %unit%" : "%.1f %unit%";
    }
}
