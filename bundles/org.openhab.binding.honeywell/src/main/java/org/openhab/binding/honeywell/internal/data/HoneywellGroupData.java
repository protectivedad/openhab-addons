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

import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The {@link HoneywellGroupData} defines the Honeywell api Group data
 * flatten json data as shown on the interactive documentation
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellGroupData extends HoneywellAbstractData {
    // Array of room objects
    private final HashMap<Integer, HoneywellAccessoryData> accessories = new HashMap<>(6);

    public void updateData(String rawContent) throws JSONException, IOException {
        logger.trace("Raw GroupData: '{}'", rawContent);
        if (HONEYWELL_BLANK_JSON.equals(rawContent)) {
            throw new IOException();
        }
        super.updateData(rawContent);
        try {
            final String deviceId = rawObject.getString("deviceId");
            accessories.clear();
            logger.debug("Processing rooms information for deviceId: '{}'", deviceId);
            processRooms(rawObject.getJSONArray("rooms"));
        } catch (Exception e) {
            if (isError()) {
                throw new JSONException(rawObject.toString());
            }
            rawObject.keys().forEachRemaining(key -> {
                logger.trace("{}: {}", key, rawObject.get(key));
            });
            throw new JSONException("Data received from Honeywell not understood, see error log");
        }
        setIsValid();
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
            accessories.put(accessoryId,
                    new HoneywellAccessoryData(accessory.getJSONObject("accessoryValue").toString()));
        }
    }

    public @Nullable HoneywellAccessoryData getAccessoryData(Integer accessoryId) {
        return accessories.get(accessoryId);
    }

    public Set<Integer> availableSensors() {
        return accessories.keySet();
    }

    public boolean isEmpty() {
        return accessories.isEmpty();
    }
}
