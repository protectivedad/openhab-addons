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

import java.util.HashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;

/**
 * The {@link Content} defines the Honeywell api Group data
 * flatten json data as shown on the interactive documentation
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class GroupData extends HoneywellAbstractData {
    private final HoneywellConnectionInterface honeywellApi;
    private final String deviceUrl;
    private final String deviceId; // Device ID of the main thermostat (T9/T10).
    // Array of room objects
    private final HashMap<String, AccessoryData> accessories = new HashMap<>(3);

    public GroupData(HoneywellConnectionInterface honeywellApi, String deviceUrl) throws IllegalArgumentException {
        super(honeywellApi.getCached(deviceUrl));
        this.honeywellApi = honeywellApi;
        this.deviceUrl = deviceUrl;
        try {
            deviceId = rawObject.getString("deviceId");
            processRooms(rawObject.getJSONArray("rooms"));
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON object is not a valid group item");
        }
        logger.trace("Create group data for '{}'", deviceId);
    }

    public void updateData() throws IllegalArgumentException {
        updateData(honeywellApi.getCached(deviceUrl));
        accessories.clear();
        processRooms(rawObject.getJSONArray("rooms"));
    }

    private void processRooms(JSONArray inArray) {
        logger.debug("Processing group rooms data");
        for (int i = 0; i < inArray.length(); i++) {
            JSONObject room = inArray.getJSONObject(i);
            room.put("avgTemperature", room.getNumber("avgTemperature"));
            room.put("avgHumidity", room.getNumber("avgHumidity"));
            processAccessories(room.getJSONArray("accessories"), room);
        }
    }

    private String getKey(int id) {
        return deviceId + "-" + Integer.toString(id);
    }

    private void processAccessories(JSONArray inArray, JSONObject room) {
        logger.debug("Processing group accessories");
        for (int i = 0; i < inArray.length(); i++) {
            JSONObject accessory = inArray.getJSONObject(i);
            final String newKey = getKey(accessory.getInt("accessoryId"));
            try {
                accessories.put(newKey, new AccessoryData(accessory.getJSONObject("accessoryValue").toString()));
            } catch (JSONException e) {
                logger.warn("Failed to process accessory data: {}", e.getMessage());
            }
        }
    }

    public @Nullable AccessoryData getAccessoryData(String sensorId) {
        return accessories.get(sensorId);
    }

    public boolean isEmpty() {
        return accessories.isEmpty();
    }
}
