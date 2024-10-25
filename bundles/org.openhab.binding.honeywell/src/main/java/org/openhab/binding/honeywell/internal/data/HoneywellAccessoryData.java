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
import static org.openhab.core.library.unit.Units.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONException;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 * The {@link HoneywellAccessoryData} defines the Honeywell api Accessory data
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellAccessoryData extends HoneywellAbstractData {
    private final float temperature;
    private final float humidity;
    private final boolean motion;
    private final boolean occupancy;
    private final String batteryStatus;

    public HoneywellAccessoryData(String rawJson) throws JSONException {
        try {
            super.updateData(rawJson);
            this.temperature = rawObject.getFloat("indoorTemperature");
            this.humidity = rawObject.getFloat("indoorHumidity");
            this.motion = rawObject.getBoolean("motionDet");
            this.occupancy = rawObject.getBoolean("occupancyDet");
            this.batteryStatus = rawObject.getString("batteryStatus");
        } catch (Exception e) {
            isValid = false;
            throw new JSONException("JSON object is not a valid sensor item");
        }
        setIsValid();
    }

    // No units in the rooms data always seems to be in fahrenheit
    public State getTemperature() {
        return (isValid) ? new QuantityType<>(temperature, FAHRENHEIT) : UnDefType.UNDEF;
    }

    public State getHumidity() {
        return (isValid) ? new QuantityType<>(humidity, PERCENT) : UnDefType.UNDEF;
    }

    public State getMotion() {
        return (isValid) ? (motion ? OnOffType.ON : OnOffType.OFF) : UnDefType.UNDEF;
    }

    public State getOccupancy() {
        return (isValid) ? (occupancy ? OnOffType.ON : OnOffType.OFF) : UnDefType.UNDEF;
    }

    public State getBatteryStatus() {
        return (isValid) ? new StringType(batteryStatus) : UnDefType.UNDEF;
    }
}
