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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openhab.binding.honeywell.internal.data.HoneywellContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellDiscoveryPriorityData} defines the Honeywell api Priority data
 * flatten json data as shown on the interactive documentation
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellDiscoveryPriorityData {
    private final Logger logger = LoggerFactory.getLogger(HoneywellDiscoveryPriorityData.class);

    private static final Map<String, String> HONEYWELL_TYPE_FILTER = new HashMap<>(2);
    static {
        HONEYWELL_TYPE_FILTER.put("Thermostat", "Thermostat Sensor");
        HONEYWELL_TYPE_FILTER.put("IndoorAirSensor", "Sensor");
    }

    // Array of room objects
    public final HashMap<Integer, String> accessoryName = new HashMap<>(6);

    public HoneywellDiscoveryPriorityData(String rawString) throws IllegalArgumentException {
        try {
            final HoneywellContent content = new HoneywellContent(rawString);
            if (content.validObject) {
                processContent(content.rawObject.getJSONObject("currentPriority").getJSONArray("rooms"));
            } else {
                throw new IllegalArgumentException("No valid priority JSON object");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private void processContent(JSONArray inArray) {
        logger.debug("Processing rooms");
        for (int i = 0; i < inArray.length(); i++) {
            final JSONObject room = inArray.getJSONObject(i);
            processAccessories(room.getJSONArray("accessories"), room);
        }
    }

    private void processAccessories(JSONArray inArray, JSONObject room) {
        logger.debug("Processing accessories");
        for (int i = 0; i < inArray.length(); i++) {
            final JSONObject accessory = inArray.getJSONObject(i);
            if (HONEYWELL_TYPE_FILTER.containsKey(accessory.getString("type"))) {
                final String name = String.format("%s %s", room.getString("roomName"),
                        HONEYWELL_TYPE_FILTER.get(accessory.getString("type")));
                final int newKey = accessory.getInt("id");
                accessoryName.put(newKey, name);
            }
        }
    }
}
