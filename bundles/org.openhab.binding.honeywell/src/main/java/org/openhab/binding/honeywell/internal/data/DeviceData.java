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

import static org.openhab.core.library.unit.ImperialUnits.*;
import static org.openhab.core.library.unit.SIUnits.*;
import static org.openhab.core.library.unit.Units.*;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Optional;

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
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
    private final Unit<Temperature> units;
    private final Integer allowedTimeIncrements;
    private JSONObject changeableValues;
    public final String deviceID;

    private enum SetpointStatus {
        NO("NoHold"),
        TEMPORARY("TemporaryHold"),
        PERMANENT("PermanentHold"),
        UNTIL("HoldUntil");

        private String setpointstatus;

        SetpointStatus(String envSetpointStatus) {
            this.setpointstatus = envSetpointStatus;
        }

        public String getSetpointStatus() {
            return setpointstatus;
        }

        public static Optional<SetpointStatus> get(String setpointstatus) {
            return Arrays.stream(SetpointStatus.values()).filter(m -> m.setpointstatus.equals(setpointstatus))
                    .findFirst();
        }
    }

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
            units = rawObject.getString("units").equals("Celsius") ? CELSIUS : FAHRENHEIT;
            allowedTimeIncrements = rawObject.getInt("allowedTimeIncrements");
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

    public State getModes() {
        return new StringType(rawObject.getJSONArray("allowedModes").toString());
    }

    public State getMode() {
        return new StringType(Mode.get(changeableValues.getString("mode")).get().getMode());
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

    public State getSetpointStatus() {
        return new StringType(
                SetpointStatus.get(changeableValues.getString("thermostatSetpointStatus")).get().getSetpointStatus());
    }

    public void setSetpointStatus(String setpointstatus) {
        if (SetpointStatus.get(setpointstatus).isPresent()) {
            final String tempSetpointStatus = SetpointStatus.get(setpointstatus).get().getSetpointStatus();
            changeableValues.put("thermostatSetpointStatus", tempSetpointStatus);
        } else {
            throw new IllegalArgumentException("Not a valid thermostat setpoint status");
        }
    }

    public State getHumidity() {
        final Number humidity = rawObject.getNumber("indoorHumidity");
        return (humidity == null) ? UnDefType.UNDEF : new QuantityType<>(humidity, PERCENT);
    }

    public State getTemperature() {
        final Number temperature = rawObject.getNumber("indoorTemperature");
        return (temperature == null) ? UnDefType.UNDEF : new QuantityType<>(temperature, units);
    }

    private float setTempDigits(QuantityType<Temperature> setpoint) {
        final QuantityType<Temperature> convertedSetpoint = setpoint.toUnit(units);
        if (null == convertedSetpoint)
            return 0;
        return (units == FAHRENHEIT) ? (float) Math.round(convertedSetpoint.floatValue())
                : (float) Math.round(convertedSetpoint.floatValue() * 2) / 2;
    }

    public State getHeatSetpoint() {
        final Float temperature = changeableValues.getFloat("heatSetpoint");
        return new QuantityType<>(temperature, units);
    }

    public void setHeatSetpoint(QuantityType<Temperature> setpoint) {
        changeableValues.put("heatSetpoint", setTempDigits(setpoint));
    }

    public State getCoolSetpoint() {
        final Float temperature = changeableValues.getFloat("coolSetpoint");
        return new QuantityType<>(temperature, units);
    }

    public void setCoolSetpoint(QuantityType<Temperature> setpoint) {
        changeableValues.put("coolSetpoint", setTempDigits(setpoint));
    }

    public State getNextPeriodTime() {
        DateTimeType now = new DateTimeType();
        DateTimeType tempPeriodTime = new DateTimeType(
                String.format("%sT%s", now.format("%1$tF"), changeableValues.getString("nextPeriodTime")));
        if (now.getInstant().isAfter(tempPeriodTime.getInstant()))
            tempPeriodTime = new DateTimeType(tempPeriodTime.getZonedDateTime().plusDays(1));

        logger.trace("Retreived next time period: '{}'", tempPeriodTime.toString());

        return tempPeriodTime;
    }

    public void setNextPeriodTime(String periodtime) {
        logger.trace("Setting nextPeriodTime to: '{}'", periodtime);
        ZonedDateTime time = new DateTimeType(periodtime).getZonedDateTime()
                .plusSeconds(allowedTimeIncrements * 30 - 1);
        if (time.isBefore(new DateTimeType().getZonedDateTime()))
            logger.warn("Next period time is before current time.");
        Integer hours = time.getHour();
        Integer mins = (int) (allowedTimeIncrements * Math.floor(time.getMinute() / allowedTimeIncrements));
        logger.trace("Parsed hours/minutes as {}/{}", hours, mins);
        changeableValues.put("nextPeriodTime", String.format("%02d:%02d:00", hours, mins));
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

    public State getChangeableValues() {
        return new StringType(changeableValues.toString());
    }

    public State getThermostat() {
        final JSONObject tempThermostat = new JSONObject();
        tempThermostat.put("deviceID", deviceID);
        tempThermostat.put("userDefinedDeviceName", rawObject.getString("userDefinedDeviceName"));
        tempThermostat.put("indoorTemperature", getTemperature());
        tempThermostat.put("indoorHumidity", getHumidity());
        tempThermostat.put("allowedModes", rawObject.getJSONArray("allowedModes"));
        tempThermostat.put("changeableValues", changeableValues);
        return new StringType(tempThermostat.toString());
    }
}
