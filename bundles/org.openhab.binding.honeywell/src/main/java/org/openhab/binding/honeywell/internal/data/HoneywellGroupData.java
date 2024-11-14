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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final HashMap<Integer, HoneywellAccessoryValueData> accessories = new HashMap<>(6);
    private final HashMap<Integer, HoneywellAccessoryAttributeData> attributes = new HashMap<>(6);

    public void updateData(String rawContent) throws JSONException, IOException {
        logger.trace("Raw GroupData: '{}'", rawContent);
        if (HONEYWELL_BLANK_JSON.equals(rawContent)) {
            throw new IOException();
        }
        super.updateData(rawContent);
        try {
            final String deviceId = rawObject.getString("deviceId");
            logger.debug("Processing rooms information for deviceId: '{}'", deviceId);
            processRooms(rawObject.getJSONArray("rooms"));
        } catch (Exception e) {
            isValid = false;
            if (isError()) {
                throw new JSONException(rawObject.toString());
            }
            rawObject.keys().forEachRemaining(key -> {
                logger.trace("{}: {}", key, rawObject.get(key));
            });
            throw new JSONException("Data received from Honeywell not understood, see error log: " + e.getMessage());
        }
        setIsValid();
    }

    private void processRooms(JSONArray inArray) {
        for (int i = 0; i < inArray.length(); i++) {
            processAccessories(inArray.getJSONObject(i).getJSONArray("accessories"));
        }
    }

    private boolean processValue(JSONObject accessory, int accessoryId) {
        final @Nullable HoneywellAccessoryValueData accessoryValueData;
        boolean wasInvalid = true;
        if (accessories.containsKey(accessoryId)) {
            logger.debug("Accessory updating stored value set");
            accessoryValueData = accessories.get(accessoryId);
        } else {
            logger.debug("Accessory creating new value set");
            accessoryValueData = new HoneywellAccessoryValueData();
            accessories.put(accessoryId, accessoryValueData);
        }
        if (null != accessoryValueData) {
            wasInvalid = accessoryValueData.isValid();
            accessoryValueData.updateData(accessory.getJSONObject("accessoryValue"));
        }
        return wasInvalid;
    }

    private void processAttribute(JSONObject accessory, int accessoryId, boolean update) {
        final @Nullable HoneywellAccessoryAttributeData accessoryAttributeData;
        if (attributes.containsKey(accessoryId)) {
            accessoryAttributeData = attributes.get(accessoryId);
        } else {
            accessoryAttributeData = new HoneywellAccessoryAttributeData();
            attributes.put(accessoryId, accessoryAttributeData);
            update = true;
        }
        if (null != accessoryAttributeData && update) {
            accessoryAttributeData.updateData(accessory.getJSONObject("accessoryAttribute"));
        }
    }

    private void processAccessories(JSONArray inArray) {
        for (int i = 0; i < inArray.length(); i++) {
            JSONObject accessory = inArray.getJSONObject(i);
            final int accessoryId = accessory.getInt("accessoryId");
            logger.debug("Storing accessory information for accessoryId: '{}'", accessoryId);
            logger.trace("Data: {}", accessory.toString());
            final boolean wasInvalid = processValue(accessory, accessoryId);
            processAttribute(accessory, accessoryId, wasInvalid || !isValid());

        }
    }

    public @Nullable HoneywellAccessoryValueData getAccessoryData(int accessoryId) {
        return accessories.get(accessoryId);
    }

    public Map<String, String> getProperties(int accessoryId) {
        try {
            @Nullable
            JSONObject attributesJson = null;
            if (attributes.containsKey(accessoryId)) {
                final @Nullable HoneywellAccessoryAttributeData attribute = attributes.get(accessoryId);
                if (null != attribute) {
                    attributesJson = attribute.rawObject;
                    Map<String, String> stringMap = attributesJson.toMap().entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
                    return stringMap;
                }
            }
        } catch (Exception e) {
            logger.debug("HoneywellGroupData getProperties error: {}", e.getMessage());
        }
        return new HashMap<String, String>();
    }

    public Set<Integer> availableSensors() {
        return accessories.keySet();
    }

    public boolean isEmpty() {
        return accessories.isEmpty();
    }
}
