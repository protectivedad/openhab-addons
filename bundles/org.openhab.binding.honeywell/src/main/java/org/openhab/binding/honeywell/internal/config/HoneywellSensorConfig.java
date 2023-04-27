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
package org.openhab.binding.honeywell.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link HoneywellSensorConfig} class contains fields mapping thing configuration parameters.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellSensorConfig {
    public Integer locationId = 9999999;
    public String deviceId = "";
    public String sensorId = "";
}
