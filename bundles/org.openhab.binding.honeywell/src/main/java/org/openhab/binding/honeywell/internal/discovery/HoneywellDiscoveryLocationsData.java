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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.binding.honeywell.internal.data.HoneywellContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LocationData} defines the Honeywell api Locations data
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellDiscoveryLocationsData {
    private final Logger logger = LoggerFactory.getLogger(HoneywellDiscoveryLocationsData.class);
    // Store some information needed for later processing
    public final HashMap<Integer, String> locationName = new HashMap<>(2);
    public final HashMap<String, String> deviceName = new HashMap<>(4);
    public final HashMap<String, Integer> deviceLocation = new HashMap<>(4);

    public HoneywellDiscoveryLocationsData(String rawContent) {
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
        logger.debug("Processing locations response");
        for (int i = 0; i < content.rawArray.length(); i++) {
            final JSONObject newJson = content.rawArray.getJSONObject(i);
            final int newLocationID = newJson.getInt("locationID");
            locationName.put(newLocationID, newJson.getString("name"));
            final JSONArray devices = newJson.getJSONArray("devices");
            for (int j = 0; j < devices.length(); j++) {
                final JSONObject newDevice = devices.getJSONObject(j);
                try {
                    if ("Thermostat".equals(newDevice.getString("deviceClass"))) {
                        final String newDeviceID = newDevice.getString("deviceID");
                        deviceLocation.put(newDeviceID, newLocationID);
                        deviceName.put(newDeviceID, newDevice.getString("name"));
                    }
                } catch (Exception e) {
                    logger.error("Unable to process device entry at location '{}': {}", newLocationID, e.getMessage());
                }
            }
        }
    }
}
