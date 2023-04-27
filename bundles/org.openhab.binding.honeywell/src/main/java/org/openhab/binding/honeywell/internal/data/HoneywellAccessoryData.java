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
            throw new JSONException("JSON object is not a valid sensor item");
        }
    }

    // No units in the rooms data always seems to be in fahrenheit
    public State getTemperature() {
        return new QuantityType<>(temperature, FAHRENHEIT);
    }

    public State getHumidity() {
        return new QuantityType<>(humidity, PERCENT);
    }

    public State getMotion() {
        return motion ? OnOffType.ON : OnOffType.OFF;
    }

    public State getOccupancy() {
        return occupancy ? OnOffType.ON : OnOffType.OFF;
    }

    public State getBatteryStatus() {
        return new StringType(batteryStatus);
    }
}
