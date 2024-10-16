/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
import org.json.JSONObject;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;

/**
 * The {@link Content} defines the Honeywell api Priority data
 * flatten json data as shown on the interactive documentation
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class PriorityData extends HoneywellAbstractData {
    private final HoneywellConnectionInterface honeywellApi;
    private final String deviceUrl;
    private final String deviceId; // Device ID of the main thermostat (T9/T10).
    // Array of room objects
    private final HashMap<String, AccessoryData> accessories = new HashMap<>(3);

    public PriorityData(HoneywellConnectionInterface honeywellApi, String deviceUrl) throws IllegalArgumentException {
        super(honeywellApi.getCached(deviceUrl));
        this.honeywellApi = honeywellApi;
        this.deviceUrl = deviceUrl;
        try {
            deviceId = rawObject.getString("deviceId");
            processRooms(rawObject.getJSONObject("currentPriority").getJSONArray("rooms"));
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON object is not a valid priority item");
        }
        logger.trace("Create priority data for '{}'", deviceId);
    }

    public void updateData() throws IllegalArgumentException {
        updateData(honeywellApi.getCached(deviceUrl));
        accessories.clear();
        processRooms(rawObject.getJSONObject("currentPriority").getJSONArray("rooms"));
    }

    private Number converFtoC(float F) {
        return Float.valueOf(String.format("%.1f", (F - 32) * 5 / 9));
    }

    private void processRooms(JSONArray inArray) {
        logger.debug("Processing rooms");
        for (int i = 0; i < inArray.length(); i++) {
            JSONObject room = inArray.getJSONObject(i);
            room.put("roomAvgTemp", converFtoC(room.getFloat("roomAvgTemp")));
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
            final String newKey = getKey(accessory.getInt("id"));
            accessories.put(newKey, new AccessoryData(converFtoC(accessory.getFloat("temperature")),
                    room.getNumber("roomAvgHumidity"), room.getBoolean("overallMotion")));
        }
    }

    public @Nullable AccessoryData getAccessoryData(String sensorId) {
        return accessories.get(sensorId);
    }

    public boolean isEmpty() {
        return accessories.isEmpty();
    }
}
