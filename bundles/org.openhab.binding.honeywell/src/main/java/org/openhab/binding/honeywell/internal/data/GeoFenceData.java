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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link Content} defines the Honeywell api GeoFences data
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class GeoFenceData extends HoneywellAbstractData {
    public final Integer geoFenceID; // Unique geoFenceID.

    public GeoFenceData(String rawJson) {
        super(rawJson);
        try {
            geoFenceID = rawObject.getInt("geoFenceID");
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON object is not a valid geofence item");
        }
        logger.trace("Create geofence data for '{}'", geoFenceID);
    }
}
