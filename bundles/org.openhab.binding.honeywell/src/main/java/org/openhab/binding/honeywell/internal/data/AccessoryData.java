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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 * The {@link Content} defines the Honeywell api Accessory data
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class AccessoryData {
    private final Number temperature;
    private final Number humidity;
    private final Boolean motion;

    public AccessoryData(Number temperature, Number humidity, Boolean motion) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.motion = motion;
    }

    public State getTemperature() {
        return (temperature == null) ? UnDefType.UNDEF : new QuantityType<>(temperature, CELSIUS);
    }

    public State getHumidity() {
        return (humidity == null) ? UnDefType.UNDEF : new QuantityType<>(humidity, PERCENT);
    }

    public State getMotion() {
        return motion ? OnOffType.ON : OnOffType.OFF;
    }
}
