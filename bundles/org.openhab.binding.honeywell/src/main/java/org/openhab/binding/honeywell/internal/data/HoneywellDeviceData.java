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

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONArray;
import org.json.JSONException;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.types.State;

/**
 * The {@link HoneywellDeviceData} defines the Honeywell api Device data
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellDeviceData extends HoneywellAbstractData {
    private final HoneywellConnectionInterface honeywellApi;
    private final String deviceUrl;
    private float temperature = 0;
    private float humidity = 0;
    private Unit<Temperature> units = CELSIUS;
    private HoneywellThermostatUpdatable changeableValues = new HoneywellThermostatUpdatable();

    public HoneywellDeviceData(HoneywellConnectionInterface honeywellApi, String deviceUrl) {
        super();
        this.honeywellApi = honeywellApi;
        this.deviceUrl = deviceUrl;
    }

    public void updateData() throws JSONException {
        updateData(honeywellApi.getCached(deviceUrl));
        temperature = rawObject.getFloat("indoorTemperature");
        humidity = rawObject.getFloat("indoorHumidity");
        units = rawObject.getString("units").equals("Celsius") ? CELSIUS : FAHRENHEIT;
        final int allowedTimeIncrements = rawObject.getInt("allowedTimeIncrements");
        final JSONArray allowedModes = rawObject.getJSONArray("allowedModes");
        changeableValues = new HoneywellThermostatUpdatable(allowedModes, units, allowedTimeIncrements,
                rawObject.getJSONObject("changeableValues").toString());
    }

    public State getTemperature() {
        return new QuantityType<>(temperature, units);
    }

    public State getHumidity() {
        return new QuantityType<>(humidity, PERCENT);
    }

    public void postUpdate() {
        honeywellApi.postHttpHoneywell(deviceUrl, changeableValues.toJson());
    }

    public void setMode(String mode) throws IllegalArgumentException {
        changeableValues.setMode(mode);
    }

    public State getMode() {
        return changeableValues.getMode();
    }

    public void setSetpointStatus(String setpointStatus) throws IllegalArgumentException {
        changeableValues.setSetpointStatus(setpointStatus);
    }

    public State getSetpointStatus() {
        return changeableValues.getSetpointStatus();
    }

    public void setNextPeriodTime(String nextPeriodTime) {
    }

    public State getNextPeriodTime() {
        return changeableValues.getNextPeriodTime();
    }

    public void setHeatSetpoint(QuantityType<Temperature> setpoint) {
        changeableValues.setHeatSetpoint(setpoint);
    }

    public State getHeatSetpoint() {
        return changeableValues.getHeatSetpoint();
    }

    public void setCoolSetpoint(QuantityType<Temperature> setpoint) {
        changeableValues.setCoolSetpoint(setpoint);
    }

    public State getCoolSetpoint() {
        return changeableValues.getCoolSetpoint();
    }
}
