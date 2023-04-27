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
 * The {@link HoneywellGroupData} defines the Honeywell api Group data
 * flatten json data as shown on the interactive documentation
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellGroupData extends HoneywellAbstractData {
    private final HoneywellConnectionInterface honeywellApi;
    private final String deviceUrl;
    // Array of room objects
    private final HashMap<Integer, HoneywellAccessoryData> accessories = new HashMap<>(6);

    public HoneywellGroupData(HoneywellConnectionInterface honeywellApi, String deviceUrl) {
        super();
        this.honeywellApi = honeywellApi;
        this.deviceUrl = deviceUrl;
    }

    public void updateData() throws JSONException {
        updateData(honeywellApi.getCached(deviceUrl));
        accessories.clear();
        final String deviceId = rawObject.getString("deviceId");
        logger.debug("Processing rooms information for deviceId: '{}'", deviceId);
        processRooms(rawObject.getJSONArray("rooms"));
    }

    private void processRooms(JSONArray inArray) {
        for (int i = 0; i < inArray.length(); i++) {
            processAccessories(inArray.getJSONObject(i).getJSONArray("accessories"));
        }
    }

    private void processAccessories(JSONArray inArray) {
        for (int i = 0; i < inArray.length(); i++) {
            JSONObject accessory = inArray.getJSONObject(i);
            final int accessoryId = accessory.getInt("accessoryId");
            logger.debug("Storing accessory information for accessoryId: '{}'", accessoryId);
            try {
                accessories.put(accessoryId,
                        new HoneywellAccessoryData(accessory.getJSONObject("accessoryValue").toString()));
            } catch (JSONException e) {
                logger.warn("Failed to process accessory data: {}", e.getMessage());
            }
        }
    }

    public @Nullable HoneywellAccessoryData getAccessoryData(Integer accessoryId) {
        return accessories.get(accessoryId);
    }

    public boolean isEmpty() {
        return accessories.isEmpty();
    }
}
