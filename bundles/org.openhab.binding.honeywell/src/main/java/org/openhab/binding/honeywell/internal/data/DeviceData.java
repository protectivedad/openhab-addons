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

import static org.openhab.core.library.unit.SIUnits.*;
import static org.openhab.core.library.unit.Units.*;

import java.util.Arrays;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 * The {@link Content} defines the Honeywell api Device data
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class DeviceData extends HoneywellAbstractData {
    private final HoneywellConnectionInterface honeywellApi;
    private final String deviceUrl;
    public final String deviceID;
    public JSONObject changeableValues;

    private enum Mode {
        OFF("Off"),
        HEAT("Heat"),
        COOL("Cool");

        private String mode;

        Mode(String envMode) {
            this.mode = envMode;
        }

        public String getMode() {
            return mode;
        }

        public static Optional<Mode> get(String mode) {
            return Arrays.stream(Mode.values()).filter(m -> m.mode.equals(mode)).findFirst();
        }
    }

    public DeviceData(HoneywellConnectionInterface honeywellApi, String deviceUrl) throws IllegalArgumentException {
        super(honeywellApi.getCached(deviceUrl));
        this.honeywellApi = honeywellApi;
        this.deviceUrl = deviceUrl;
        try {
            deviceID = rawObject.getString("deviceID");
            changeableValues = rawObject.getJSONObject("changeableValues");
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON object is not a valid device item");
        }
        logger.trace("Create device data for '{}'", deviceID);
    }

    public void updateData() throws IllegalArgumentException {
        try {
            updateData(honeywellApi.getCached(deviceUrl));
            changeableValues = rawObject.getJSONObject("changeableValues");
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON object is not a valid device item");
        }
    }

    public String getModes() {
        return rawObject.getJSONArray("allowedModes").toString();
    }

    public String getMode() {
        return Mode.get(changeableValues.getString("mode")).get().getMode();
    }

    private boolean validMode(String mode) {
        final JSONArray allowedModes = rawObject.getJSONArray("allowedModes");
        for (int i = 0; i <= allowedModes.length(); i++) {
            if (allowedModes.get(i).equals(mode)) {
                return true;
            }
        }
        return false;
    }

    public void setMode(String mode) {
        if (Mode.get(mode).isPresent() && validMode(mode)) {
            final String tempMode = Mode.get(mode).get().getMode();
            changeableValues.put("mode", tempMode);
        } else {
            throw new IllegalArgumentException("Not a valid thermostat mode");
        }
    }

    public State getHumidity() {
        final Number humidity = rawObject.getNumber("indoorHumidity");
        return (humidity == null) ? UnDefType.UNDEF : new QuantityType<>(humidity, PERCENT);
    }

    public State getTemperature() {
        final Number temperature = rawObject.getNumber("indoorTemperature");
        return (temperature == null) ? UnDefType.UNDEF : new QuantityType<>(temperature, CELSIUS);
    }

    public State getHeatSetpoint() {
        final Number temperature = rawObject.getNumber("heatSetpoint");
        return (temperature == null) ? UnDefType.UNDEF : new QuantityType<>(temperature, CELSIUS);
    }

    public void setHeatSetpoint(Number setpoint) {
        changeableValues.put("heatSetpoint", setpoint);
    }

    public State getCoolSetpoint() {
        final Number temperature = rawObject.getNumber("coolSetpoint");
        return (temperature == null) ? UnDefType.UNDEF : new QuantityType<>(temperature, CELSIUS);
    }

    public void setCoolSetpoint(Number setpoint) {
        changeableValues.put("coolSetpoint", setpoint);
    }

    public void postUpdate() {
        honeywellApi.postHttpHoneywell(deviceUrl, changeableValues.toString());
    }

    public void postUpdate(String command) {
        honeywellApi.postHttpHoneywell(deviceUrl, command);
    }

    public void setChangeableValues(String rawString) throws IllegalArgumentException {
        try {
            Content content = new Content(rawString);
            if (content.validObject) {
                changeableValues = content.rawObject;
            } else {
                throw new IllegalArgumentException("Not a valid generic JSON object");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public String getThermostat() {
        final JSONObject tempThermostat = new JSONObject();
        tempThermostat.put("deviceID", deviceID);
        tempThermostat.put("userDefinedDeviceName", rawObject.getString("userDefinedDeviceName"));
        tempThermostat.put("indoorTemperature", getTemperature());
        tempThermostat.put("indoorHumidity", getHumidity());
        tempThermostat.put("allowedModes", rawObject.getJSONArray("allowedModes"));
        tempThermostat.put("changeableValues", changeableValues);
        return tempThermostat.toString();
    }
}
