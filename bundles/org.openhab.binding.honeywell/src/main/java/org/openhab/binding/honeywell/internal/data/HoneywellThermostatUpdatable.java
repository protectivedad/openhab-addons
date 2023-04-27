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
import java.util.Arrays;
import java.util.Optional;

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONArray;
import org.json.JSONException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;

/**
 * The {@link HoneywellThermostatUpdatable} defines the Honeywell api Accessory data
 * 
 * Example date:
 * "changeableValues": { "mode":"Off", "endCoolSetpoint":null, "heatCoolMode":"Heat", "endHeatSetpoint":null,
 * "thermostatSetpointStatus":"NoHold", "heatSetpoint":18, "coolSetpoint":25.5, "nextPeriodTime":"22:30:00" }
 * 
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellThermostatUpdatable extends HoneywellAbstractData {
    private DateTimeType updated = new DateTimeType();
    private JSONArray allowedModes = new JSONArray();
    private Unit<Temperature> units = CELSIUS;
    private int allowedTimeIncrements = 1;
    private Mode mode = Mode.OFF;
    private SetpointStatus setpointStatus = SetpointStatus.NO;
    private float heatSetpoint = 0;
    private float coolSetpoint = 0;
    private String nextPeriodTime = "";
    private boolean isValid = false;

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

    public HoneywellThermostatUpdatable() {
    }

    public HoneywellThermostatUpdatable(JSONArray allowedModes, Unit<Temperature> units, int allowedTimeIncrements,
            String rawJson) {
        try {
            super.updateData(rawJson);
            this.allowedModes = allowedModes;
            this.units = units;
            this.allowedTimeIncrements = allowedTimeIncrements;
            setMode(rawObject.getString("mode"));
            setSetpointStatus(rawObject.getString("thermostatSetpointStatus"));
            nextPeriodTime = rawObject.getString("nextPeriodTime");
            heatSetpoint = rawObject.getFloat("heatSetpoint");
            coolSetpoint = rawObject.getFloat("coolSetpoint");
        } catch (Exception e) {
            throw new JSONException("JSON object is not a valid updatable thermostat item: " + e.getMessage());
        }
        isValid = true;
    }

    private void flatten() {
        if (isValid) {
            rawObject.put("mode", getMode());
            rawObject.put("thermostatSetpointStatus", getSetpointStatus());
            rawObject.put("nextPeriodTime", nextPeriodTime);
            rawObject.put("heatSetpoint", heatSetpoint);
            rawObject.put("coolSetpoint", coolSetpoint);
        }
    }

    public String toJson() throws JSONException {
        flatten();
        return rawObject.toString();
    }

    public State getMode() {
        return new StringType(mode.getMode());
    }

    private boolean validMode(String mode) {
        for (int i = 0; i <= allowedModes.length(); i++) {
            try {
                if (allowedModes.get(i).equals(mode)) {
                    return true;
                }
            } catch (Exception e) {
                // ignore exception
            }
        }
        return false;
    }

    public void setMode(String mode) throws IllegalArgumentException {
        if (Mode.get(mode).isPresent() && validMode(mode)) {
            Mode.get(mode).ifPresent((m) -> this.mode = m);
        } else {
            throw new IllegalArgumentException("Not a valid thermostat mode");
        }
    }

    public State getSetpointStatus() {
        return new StringType(setpointStatus.getSetpointStatus());
    }

    public void setSetpointStatus(String setpointStatus) throws IllegalArgumentException {
        SetpointStatus.get(setpointStatus).ifPresentOrElse((s) -> {
            this.setpointStatus = s;
        }, () -> {
            throw new IllegalArgumentException("Not a valid thermostat setpoint status");
        });
    }

    // Round temperatures to half degree for celsius full degree for fahrentheit
    private float setTempDigits(QuantityType<Temperature> setpoint) {
        final QuantityType<Temperature> convertedSetpoint = setpoint.toUnit(units);
        if (null == convertedSetpoint)
            return 0;
        return (units == FAHRENHEIT) ? (float) Math.round(convertedSetpoint.floatValue())
                : (float) Math.round(convertedSetpoint.floatValue() * 2) / 2;
    }

    public State getHeatSetpoint() {
        return new QuantityType<>(heatSetpoint, units);
    }

    public void setHeatSetpoint(QuantityType<Temperature> setpoint) {
        heatSetpoint = setTempDigits(setpoint);
    }

    public State getCoolSetpoint() {
        return new QuantityType<>(coolSetpoint, units);
    }

    public void setCoolSetpoint(QuantityType<Temperature> setpoint) {
        coolSetpoint = setTempDigits(setpoint);
    }

    public State getNextPeriodTime() {
        DateTimeType stateNextPeriodTime = new DateTimeType(
                String.format("%sT%s", updated.format("%1$tF"), nextPeriodTime));
        return (updated.getInstant().isAfter(stateNextPeriodTime.getInstant()))
                ? new DateTimeType(stateNextPeriodTime.getZonedDateTime().plusDays(1))
                : stateNextPeriodTime;
    }

    /**
     * Validate next period of time to be within the next 24 hours in the proper increments
     * 
     * @param nextPeriodTime
     * @throws IllegalArgumentException if next period time is not in the nextb 24 hours
     */
    public void setNextPeriodTime(String nextPeriodTime) throws IllegalArgumentException {
        final ZonedDateTime tempNextPeriodTime = new DateTimeType(nextPeriodTime).getZonedDateTime();

        final ZonedDateTime startOfDay = tempNextPeriodTime.truncatedTo(ChronoUnit.DAYS);
        final Duration duration = Duration.ofMinutes(allowedTimeIncrements);
        final ZonedDateTime testNextPeriodTime = startOfDay
                .plus(duration.multipliedBy(Duration.between(startOfDay, tempNextPeriodTime).dividedBy(duration)));

        if (tempNextPeriodTime.isBefore(new DateTimeType().getZonedDateTime())) {
            throw new IllegalArgumentException("Next period time is in the past");
        } else if (tempNextPeriodTime.isAfter(new DateTimeType().getZonedDateTime().plusDays(1))) {
            throw new IllegalArgumentException("Next period time is too far in the furture");
        } else if (!tempNextPeriodTime.isEqual(testNextPeriodTime)) {
            throw new IllegalArgumentException(
                    "Next period time allowed time increments is " + String.valueOf(allowedTimeIncrements));
        }
        this.nextPeriodTime = String.format("%02d:%02d:00", tempNextPeriodTime.getHour(),
                tempNextPeriodTime.getMinute());
    }
}
