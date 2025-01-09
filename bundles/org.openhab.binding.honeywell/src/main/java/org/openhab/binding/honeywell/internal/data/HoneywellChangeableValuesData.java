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
import static org.openhab.core.library.unit.ImperialUnits.FAHRENHEIT;
import static org.openhab.core.library.unit.SIUnits.CELSIUS;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.honeywell.internal.data.HoneywellScheduleData.SetpointStatus;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;

import com.google.gson.JsonObject;

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
    public static final String MODE = "mode";
    public static final ChannelTypeUID MODE_TYPE = new ChannelTypeUID(BINDING_ID, MODE);
    public static final String SETPOINTSTATUS = "setpointstatus";
    public static final ChannelTypeUID SETPOINTSTATUS_TYPE = new ChannelTypeUID(BINDING_ID, SETPOINTSTATUS);
    public static final String NEXTPERIODTIME = "nextperiodtime";
    public static final ChannelTypeUID NEXTPERIODTIME_TYPE = new ChannelTypeUID(BINDING_ID, NEXTPERIODTIME);
    public static final String HEATSETPOINT = "heatsetpoint";
    public static final ChannelTypeUID HEATSETPOINT_TYPE = new ChannelTypeUID(BINDING_ID, HEATSETPOINT);
    public static final String COOLSETPOINT = "coolsetpoint";
    public static final ChannelTypeUID COOLSETPOINT_TYPE = new ChannelTypeUID(BINDING_ID, COOLSETPOINT);

    // Round temperatures to half degree for celsius full degree for fahrentheit
    static float setTempDigits(QuantityType<Temperature> setpoint, Unit<Temperature> units)
            throws IllegalArgumentException {
        final QuantityType<Temperature> convertedSetpoint = setpoint.toUnit(units);
        if (null == convertedSetpoint) {
            throw new IllegalArgumentException(String.format("Setpoint '%s' did not convert", setpoint.toFullString()));
        }
        return (units == FAHRENHEIT) ? (float) Math.round(convertedSetpoint.floatValue())
                : (float) Math.round(convertedSetpoint.floatValue() * 2) / 2;
    }

    static float filterTemp(QuantityType<Temperature> setpoint, List<BigDecimal> setpointMinMaxStep)
            throws IllegalArgumentException {
        try {
            final Unit<Temperature> units = (0 == BigDecimal.valueOf(1).compareTo(setpointMinMaxStep.get(2)))
                    ? FAHRENHEIT
                    : CELSIUS;
            final float floatHeatSetpoint = setTempDigits(setpoint, units);
            final BigDecimal tempHeatSetpoint = BigDecimal.valueOf(floatHeatSetpoint);
            if (-1 == tempHeatSetpoint.compareTo(setpointMinMaxStep.get(0))
                    || 1 == tempHeatSetpoint.compareTo(setpointMinMaxStep.get(1))) {
                throw new IllegalArgumentException(
                        String.format("Setpoint '%s' outside allowed bounds min '%s', max '%s'", tempHeatSetpoint,
                                setpointMinMaxStep.get(0), setpointMinMaxStep.get(1)));
            }
            return floatHeatSetpoint;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    // Global lists set in HoneywellChangeableValuesData
    private final List<BigDecimal> heatSetpointMinMaxStep = new ArrayList<>();
    private final List<BigDecimal> coolSetpointMinMaxStep = new ArrayList<>();

    private final List<StateOption> allowedSetpointStatus;

    private final List<StateOption> allowedModes = new ArrayList<>();
    private Unit<Temperature> units = CELSIUS;
    private int allowedTimeIncrements = 1;

    private String mode = "Unknown";
    private SetpointStatus setpointStatus = SetpointStatus.NO;
    private float heatSetpoint = 0;
    private float coolSetpoint = 0;
    private String nextPeriodTime = "";

    private DateTimeType updated = new DateTimeType();

    public HoneywellChangeableValuesData(List<StateOption> allowedSetpointStatus) {
        this.allowedSetpointStatus = allowedSetpointStatus;
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
    protected void updateData(JsonObject rawJson, Unit<Temperature> units, JsonObject constraintsJson)
            throws IOException {
        logger.trace("Raw ConstraintsData: '{}'", constraintsJson.toString());
        logger.trace("Raw ChangeableValuesData: '{}'", rawJson);
        try {
            // constraints first
            allowedModes.clear();
            constraintsJson.get("allowedModes").getAsJsonArray().forEach((m) -> {
                allowedModes.add(new StateOption(m.getAsString(), m.getAsString()));
            });
            allowedTimeIncrements = constraintsJson.get("allowedTimeIncrements").getAsInt();
            this.units = units;
            BigDecimal step = BigDecimal.valueOf((FAHRENHEIT == units) ? 1 : 0.5);
            heatSetpointMinMaxStep.clear();
            heatSetpointMinMaxStep.add(constraintsJson.get("minHeatSetpoint").getAsBigDecimal());
            heatSetpointMinMaxStep.add(constraintsJson.get("maxHeatSetpoint").getAsBigDecimal());
            heatSetpointMinMaxStep.add(step);
            coolSetpointMinMaxStep.clear();
            coolSetpointMinMaxStep.add(constraintsJson.get("minCoolSetpoint").getAsBigDecimal());
            coolSetpointMinMaxStep.add(constraintsJson.get("maxCoolSetpoint").getAsBigDecimal());
            coolSetpointMinMaxStep.add(step);
            updateData(rawJson);
        } catch (Exception e) {
            isValid = false;
            throw new IOException("Changeable constraints update not a valid item: " + e.getMessage());
        }
    }

    /**
     * Update just the control information. The constraints are assumed to be relatively stable. If the update succeeds
     * the information is marked valid. Otherwise the information is marked invalid.
     * 
     * @param rawJson - The JSON formatted information from the Honeywell feed
     * @param units - Temperature units used to make everything consitent.
     */
    protected void updateData(JsonObject rawJson) throws IOException {
        try {
            super.updateData(rawJson);
            // assumed valid
            mode = rawObject.get("mode").getAsString();
            SetpointStatus.get(rawObject.get("thermostatSetpointStatus").getAsString())
                    .ifPresent((s) -> this.setpointStatus = s);
            try {
                nextPeriodTime = rawObject.get("nextPeriodTime").getAsString();
            } catch (Exception e) {
                nextPeriodTime = "";
            }
            heatSetpoint = rawObject.get("heatSetpoint").getAsFloat();
            coolSetpoint = rawObject.get("coolSetpoint").getAsFloat();
        } catch (Exception e) {
            isValid = false;
            throw new IOException("Changeable values update is not a valid item: " + e.getMessage());
        }
        setIsValid();
        updated = new DateTimeType();
    }

    private boolean validMode(String mode) {
        return allowedModes.stream().anyMatch(m -> m.getValue().equals(mode));
    }

    private boolean validSetpointStatus(String setpointStatus) {
        return allowedSetpointStatus.stream().anyMatch(s -> s.getValue().equals(setpointStatus));
    }

    public State getState(String resultType) {
        if (!isValid()) {
            return UnDefType.UNDEF;
        }
        switch (resultType) {
            case MODE:
                return new StringType(mode);
            case SETPOINTSTATUS:
                return new StringType(setpointStatus.getSetpointStatus().getValue());
            case NEXTPERIODTIME:
                if (nextPeriodTime.isEmpty()) {
                    return UnDefType.UNDEF;
                }
                DateTimeType stateNextPeriodTime = new DateTimeType(
                        String.format("%sT%s", updated.format("%1$tF"), nextPeriodTime));
                return (updated.getInstant().isAfter(stateNextPeriodTime.getInstant()))
                        ? new DateTimeType(stateNextPeriodTime.getZonedDateTime().plusDays(1))
                        : stateNextPeriodTime;
            case HEATSETPOINT:
                return new QuantityType<>(heatSetpoint, units);
            case COOLSETPOINT:
                return new QuantityType<>(coolSetpoint, units);
            default:
                logger.warn("Unsupported changeable values item-type '{}'", resultType);
                return UnDefType.UNDEF;
        }
    }

    public String setState(String resultType, String cmdString) {
        if (!isValid()) {
            return "Invalid changeable values data, refusing to set: " + resultType;
        }
        switch (resultType) {
            case MODE:
                if (validMode(cmdString)) {
                    mode = cmdString;
                    return "";
                }
                return String.format("Thermostat mode '%s' failed", cmdString);
            case SETPOINTSTATUS:
                if (validSetpointStatus(cmdString) && SetpointStatus.get(cmdString).isPresent()) {
                    SetpointStatus.get(cmdString).ifPresent((s) -> this.setpointStatus = s);
                    return "";
                }
                return String.format("Thermostat setpoint status '%s' failed", setpointStatus);
            case NEXTPERIODTIME:
                if (cmdString.isEmpty()) {
                    this.nextPeriodTime = "";
                    return "";
                }
                final ZonedDateTime tempNextPeriodTime = new DateTimeType(cmdString).getZonedDateTime();

                final ZonedDateTime startOfDay = tempNextPeriodTime.truncatedTo(ChronoUnit.DAYS);
                final Duration duration = Duration.ofMinutes(allowedTimeIncrements);
                final ZonedDateTime testNextPeriodTime = startOfDay.plus(
                        duration.multipliedBy(Duration.between(startOfDay, tempNextPeriodTime).dividedBy(duration)));

                if (tempNextPeriodTime.isBefore(new DateTimeType().getZonedDateTime())) {
                    return "Next period time is in the past";
                } else if (tempNextPeriodTime.isAfter(new DateTimeType().getZonedDateTime().plusDays(1))) {
                    return "Next period time is too far in the furture";
                } else if (!tempNextPeriodTime.isEqual(testNextPeriodTime)) {
                    return String.format("Next period time not in allowed time increments of '%s'",
                            allowedTimeIncrements);
                }
                this.nextPeriodTime = String.format("%02d:%02d:00", tempNextPeriodTime.getHour(),
                        tempNextPeriodTime.getMinute());
                return "";
            case HEATSETPOINT:
                try {
                    heatSetpoint = filterTemp(new QuantityType<Temperature>(cmdString), heatSetpointMinMaxStep);
                    return "";
                } catch (Exception e) {
                    return String.format("Unable to convert '%s', error '%s'", cmdString, e.getMessage());
                }
            case COOLSETPOINT:
                try {
                    coolSetpoint = filterTemp(new QuantityType<Temperature>(cmdString), coolSetpointMinMaxStep);
                    return "";
                } catch (Exception e) {
                    return String.format("Unable to convert '%s', error '%s'", cmdString, e.getMessage());
                }
            default:
                logger.warn("Unsupported thermostat item-type '{}'", resultType);
                return "Unsupported thermostat item-type: " + resultType;
        }
    }

    /**
     * 
     * @return A valid changeableValues object as a JSON formatted string
     * @throws JSONException - Object is not valid
     */
    public String toJson() throws IOException {
        if (!isValid()) {
            throw new IOException("Thermostat object is empty");
        }
        try {
            rawObject.addProperty("mode", mode);
            rawObject.addProperty("thermostatSetpointStatus", setpointStatus.getSetpointStatus().getValue().toString());
            if (!nextPeriodTime.isEmpty()) {
                rawObject.addProperty("nextPeriodTime", nextPeriodTime);
            }
            rawObject.addProperty("heatSetpoint", heatSetpoint);
            rawObject.addProperty("coolSetpoint", coolSetpoint);
            return rawObject.toString();
        } catch (Exception e) {
            throw new IOException("Thermostat object is empty");
        }
    }

    public List<StateOption> getAllowedModes() {
        if (!isValid()) {
            allowedModes.clear();
            allowedModes.add(new StateOption("Off", "Off"));
        }
        return allowedModes;
    }

    public List<BigDecimal> getHeatSetpointMinMaxStep() {
        return heatSetpointMinMaxStep;
    }

    public List<BigDecimal> getCoolSetpointMinMaxStep() {
        return coolSetpointMinMaxStep;
    }
}
