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
import org.openhab.binding.honeywell.internal.data.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link Content} defines the Honeywell api Priority data
 * flatten json data as shown on the interactive documentation
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class PriorityData {
    private final Logger logger = LoggerFactory.getLogger(PriorityData.class);
    private final String deviceId;
    // Array of room objects
    public final HashMap<String, String> accessoryName = new HashMap<>(3);

    public PriorityData(String rawString) throws IllegalArgumentException {
        try {
            Content content = new Content(rawString);
            if (content.validObject) {
                deviceId = content.rawObject.getString("deviceId");
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
            JSONObject room = inArray.getJSONObject(i);
            processAccessories(room.getJSONArray("accessories"), room);
        }
    }

    private String getKey(int id) {
        return deviceId + "-" + Integer.toString(id);
    }

    private void processAccessories(JSONArray inArray, JSONObject room) {
        logger.debug("Processing accessories");
        for (int i = 0; i < inArray.length(); i++) {
            JSONObject accessory = inArray.getJSONObject(i);
            final String name = room.getString("roomName") + " Sensor - " + accessory.getString("type");
            final String newKey = getKey(accessory.getInt("id"));
            accessoryName.put(newKey, name);
        }
    }
}
