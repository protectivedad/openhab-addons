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
package org.openhab.binding.honeywell.internal.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.honeywell.internal.data.HoneywellContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The {@link LocationData} defines the Honeywell api Locations data
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellDiscoveryLocationsData {
    private final Logger logger = LoggerFactory.getLogger(HoneywellDiscoveryLocationsData.class);
    public final HashMap<String, Entry<Long, String>> thermostat = new HashMap<>(4);

    public HoneywellDiscoveryLocationsData(String rawContent) {
        logger.trace("HoneywellDiscoveryLocationsData: {}", rawContent);
        addLocations(rawContent);
    }

    private void addLocations(String rawContent) throws IllegalArgumentException {
        try {
            final HoneywellContent content = new HoneywellContent(rawContent);
            if (content.validArray) {
                processContent(content);
            } else {
                throw new IllegalArgumentException("No valid locations JSON array");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private void processContent(HoneywellContent content) {
        for (int i = 0; i < content.rawArray.size(); i++) {
            final JsonObject newJson = content.rawArray.get(i).getAsJsonObject();
            final long newLocationID = newJson.get("locationID").getAsLong();
            final JsonArray devices = newJson.get("devices").getAsJsonArray();
            for (int j = 0; j < devices.size(); j++) {
                final JsonObject newDevice = devices.get(j).getAsJsonObject();
                try {
                    if ("Thermostat".equals(newDevice.get("deviceClass").getAsString())) {
                        final String deviceID = newDevice.get("deviceID").getAsString();
                        logger.trace("Adding thermostat: '{}'", deviceID);
                        thermostat.put(deviceID,
                                Map.entry(newLocationID, newDevice.get("name").getAsString() + " Thermostat"));
                    }
                } catch (Exception e) {
                    logger.warn("Unable to process device entry at location '{}': {}", newLocationID, e.getMessage());
                }
            }
        }
    }
}
