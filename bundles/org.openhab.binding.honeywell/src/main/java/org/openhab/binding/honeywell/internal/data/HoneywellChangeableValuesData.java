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

import static org.openhab.core.library.unit.ImperialUnits.FAHRENHEIT;
import static org.openhab.core.library.unit.SIUnits.CELSIUS;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;

/**
 * The {@link HoneywellChangeableValuesData} defines the Honeywell api changeableValues data and acts as the gatekeeper
 * ensuring the information is validated and consistent. Each refresh the item should be updated with updateData method.
 * Individual item updates are transient.
 * 
 * Example date:
 * "changeableValues": { "mode":"Off", "endCoolSetpoint":null, "heatCoolMode":"Heat", "endHeatSetpoint":null,
 * "thermostatSetpointStatus":"NoHold", "heatSetpoint":18, "coolSetpoint":25.5, "nextPeriodTime":"22:30:00" }
 * 
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellChangeableValuesData extends HoneywellAbstractData {
    private JSONArray allowedModes = new JSONArray();
    private Unit<Temperature> units = CELSIUS;
    private int allowedTimeIncrements = 1;
    private float minHeatSetpoint = 0;
    private float maxHeatSetpoint = 0;
    private float minCoolSetpoint = 0;
    private float maxCoolSetpoint = 0;

    private Mode mode = Mode.OFF;
    private SetpointStatus setpointStatus = SetpointStatus.NO;
    private float heatSetpoint = 0;
    private float coolSetpoint = 0;
    private String nextPeriodTime = "00:00:00";

    private DateTimeType updated = new DateTimeType();

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

    /**
     * Update the information as a set, including constraints. This should be done each time the object is created or on
     * an invalid to valid cycle. If the update succeeds the information is marked valid. Otherwise the information is
     * marked invalid.
     * 
     * @param rawJson - The JSON formatted information from the Honeywell feed
     * @param units - Temperature units used to make everything consitent.
     * @param constraintsJson - The JSON formatted constraints used to validate the changeable values information
     */
    public void updateData(JSONObject rawJson, Unit<Temperature> units, JSONObject constraintsJson) {
        logger.trace("constraintsJson: '{}'", constraintsJson.toString());
        logger.trace("changeableValuesJson: '{}'", rawJson);
        try {
            // constraints first
            allowedModes = constraintsJson.getJSONArray("allowedModes");
            allowedTimeIncrements = constraintsJson.getInt("allowedTimeIncrements");
            minHeatSetpoint = constraintsJson.getInt("minHeatSetpoint");
            maxHeatSetpoint = constraintsJson.getInt("maxHeatSetpoint");
            minCoolSetpoint = constraintsJson.getInt("minCoolSetpoint");
            maxCoolSetpoint = constraintsJson.getInt("maxCoolSetpoint");
            updateData(rawJson, units);
        } catch (Exception e) {
            isValid = false;
            throw new JSONException("Changeable values update is not a valid item: " + e.getMessage());
        }
        setIsValid();
    }

    /**
     * Update just the control information. The constraints are assumed to be relatively stable. If the update succeeds
     * the information is marked valid. Otherwise the information is marked invalid.
     * 
     * @param rawJson - The JSON formatted information from the Honeywell feed
     * @param units - Temperature units used to make everything consitent.
     */
    public void updateData(JSONObject rawJson, Unit<Temperature> units) {
        try {
            super.updateData(rawJson);
            // assumed valid
            setMode(rawObject.getString("mode"));
            setSetpointStatus(rawObject.getString("thermostatSetpointStatus"));
            nextPeriodTime = rawObject.getString("nextPeriodTime");
            heatSetpoint = rawObject.getFloat("heatSetpoint");
            coolSetpoint = rawObject.getFloat("coolSetpoint");
            this.units = units;
        } catch (Exception e) {
            isValid = false;
            throw new JSONException("Changeable values update is not a valid item: " + e.getMessage());
        }
    }

    /**
     * 
     * @return A valid changeableValues object as a JSON formatted string
     * @throws JSONException - Object is not valid
     */
    public String toJson() throws JSONException {
        if (!isValid) {
            throw new JSONException("Thermostat object is empty");
        }
        try {
            rawObject.put("mode", getMode());
            rawObject.put("thermostatSetpointStatus", getSetpointStatus());
            rawObject.put("nextPeriodTime", nextPeriodTime);
            rawObject.put("heatSetpoint", heatSetpoint);
            rawObject.put("coolSetpoint", coolSetpoint);
            return rawObject.toString();
        } catch (Exception e) {
            throw new JSONException("Thermostat object is empty");
        }
    }

    public State getMode() {
        return (isValid) ? new StringType(mode.getMode()) : UnDefType.UNDEF;
    }

    public List<StateOption> getAllowedModes() {
        List<StateOption> options = new ArrayList<>();
        if (isValid) {
            for (int i = 0; i < allowedModes.length(); i++) {
                final String mode = allowedModes.getString(i);
                if (null != mode) {
                    options.add(new StateOption(mode, mode));
                }
            }
        }
        if (options.isEmpty()) {
            options.add(new StateOption("Off", "Off"));
        }
        return options;
    }

    private boolean validMode(String mode) {
        for (int i = 0; i < allowedModes.length(); i++) {
            try {
                if (allowedModes.get(i).equals(mode)) {
                    return true;
                }
            } catch (Exception e) {
                logger.warn("problem with valid mode function");
            }
        }
        return false;
    }

    public String setMode(String mode) {
        if (validMode(mode) && Mode.get(mode).isPresent()) {
            Mode.get(mode).ifPresent((m) -> this.mode = m);
        } else {
            return String.format("Thermostat mode '{}' failed", mode);
        }
        return "";
    }

    public State getSetpointStatus() {
        return new StringType(setpointStatus.getSetpointStatus());
    }

    public String setSetpointStatus(String setpointStatus) throws IllegalArgumentException {
        if (SetpointStatus.get(setpointStatus).isPresent()) {
            SetpointStatus.get(setpointStatus).ifPresent((s) -> this.setpointStatus = s);
        } else {
            return String.format("Thermostat setpoint status '{}' failed", setpointStatus);
        }
        return "";
    }

    // Round temperatures to half degree for celsius full degree for fahrentheit
    private float setTempDigits(QuantityType<Temperature> setpoint) throws IllegalArgumentException {
        final QuantityType<Temperature> convertedSetpoint = setpoint.toUnit(units);
        if (null == convertedSetpoint) {
            throw new IllegalArgumentException(String.format("Setpoint '{}' did not convert", setpoint.toFullString()));
        }
        return (units == FAHRENHEIT) ? (float) Math.round(convertedSetpoint.floatValue())
                : (float) Math.round(convertedSetpoint.floatValue() * 2) / 2;
    }

    public State getHeatSetpoint() {
        return (isValid) ? new QuantityType<>(heatSetpoint, units) : UnDefType.UNDEF;
    }

    public String setHeatSetpoint(QuantityType<Temperature> setpoint) throws IllegalArgumentException {
        try {
            final float tempHeatSetpoint = setTempDigits(setpoint);
            if (tempHeatSetpoint < minHeatSetpoint || tempHeatSetpoint > maxHeatSetpoint) {
                return "Heat setpoint outside allowed bounds";
            }
            heatSetpoint = tempHeatSetpoint;
        } catch (Exception e) {
            return String.format("Unable to convert '{}'", setpoint.toFullString());
        }
        return "";
    }

    public State getCoolSetpoint() {
        return (isValid) ? new QuantityType<>(coolSetpoint, units) : UnDefType.UNDEF;
    }

    public String setCoolSetpoint(QuantityType<Temperature> setpoint) throws IllegalArgumentException {
        try {
            final float tempCoolSetpoint = setTempDigits(setpoint);
            if (tempCoolSetpoint < minCoolSetpoint || tempCoolSetpoint > maxCoolSetpoint) {
                return "Cool setpoint outside allowed bounds";
            }
            coolSetpoint = tempCoolSetpoint;
        } catch (Exception e) {
            return String.format("Unable to convert '{}'", setpoint.toFullString());
        }
        return "";
    }

    public State getNextPeriodTime() {
        DateTimeType stateNextPeriodTime = new DateTimeType(
                String.format("%sT%s", updated.format("%1$tF"), nextPeriodTime));
        return (isValid) ? ((updated.getInstant().isAfter(stateNextPeriodTime.getInstant()))
                ? new DateTimeType(stateNextPeriodTime.getZonedDateTime().plusDays(1))
                : stateNextPeriodTime) : UnDefType.UNDEF;
    }

    /**
     * Validate next period of time to be within the next 24 hours in the proper increments
     * 
     * @param nextPeriodTime
     * @throws IllegalArgumentException if next period time is not in the nextb 24 hours
     */
    public String setNextPeriodTime(String nextPeriodTime) throws IllegalArgumentException {
        final ZonedDateTime tempNextPeriodTime = new DateTimeType(nextPeriodTime).getZonedDateTime();

        final ZonedDateTime startOfDay = tempNextPeriodTime.truncatedTo(ChronoUnit.DAYS);
        final Duration duration = Duration.ofMinutes(allowedTimeIncrements);
        final ZonedDateTime testNextPeriodTime = startOfDay
                .plus(duration.multipliedBy(Duration.between(startOfDay, tempNextPeriodTime).dividedBy(duration)));

        if (tempNextPeriodTime.isBefore(new DateTimeType().getZonedDateTime())) {
            return "Next period time is in the past";
        } else if (tempNextPeriodTime.isAfter(new DateTimeType().getZonedDateTime().plusDays(1))) {
            return "Next period time is too far in the furture";
        } else if (!tempNextPeriodTime.isEqual(testNextPeriodTime)) {
            return String.format("Next period time not in allowed time increments of '{}'", allowedTimeIncrements);
        }
        this.nextPeriodTime = String.format("%02d:%02d:00", tempNextPeriodTime.getHour(),
                tempNextPeriodTime.getMinute());
        return "";
    }
}
