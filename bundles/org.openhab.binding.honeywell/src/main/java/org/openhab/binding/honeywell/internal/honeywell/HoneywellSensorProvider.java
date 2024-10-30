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
package org.openhab.binding.honeywell.internal.honeywell;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.honeywell.internal.data.HoneywellAccessoryValueData;

/**
 * The {@link HoneywellSensorProvider} defines the interface for providing sensor instances to thing
 * handlers
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public interface HoneywellSensorProvider {
    /**
     * Return a unique id which is the thermostat device id appended with a dash and then the sensor id
     * 
     * @param sensorId
     * @return - Unique id which is used ase the representative property for discovey purposes
     */
    static String uniqueId(String deviceId, int sensorId) {
        return deviceId + "-" + Integer.toString(sensorId);
    }

    String uniqueId(int sensorId);

    @Nullable
    HoneywellAccessoryValueData sensor(int sensorId);
}
