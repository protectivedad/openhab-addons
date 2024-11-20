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

import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.HONEYWELL_BLANK_JSON;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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

    public void updateData(String rawContent) throws IOException {
        logger.trace("Raw GroupData: '{}'", rawContent);
        if (HONEYWELL_BLANK_JSON.equals(rawContent)) {
            throw new IOException();
        }
        super.updateData(rawContent);
        try {
            processRooms(rawObject.get("rooms").getAsJsonArray());
        } catch (Exception e) {
            isValid = false;
            if (isError()) {
                throw new IOException(rawObject.toString());
            }
            throw new IOException("Data received from Honeywell not understood: " + e.getMessage());
        }
        setIsValid();
    }

    private void processRooms(JsonArray inArray) throws IOException {
        for (int i = 0; i < inArray.size(); i++) {
            processAccessories(inArray.get(i).getAsJsonObject().get("accessories").getAsJsonArray(),
                    inArray.get(i).getAsJsonObject().get("name").getAsString());
        }
    }

    private void processAccessories(JsonArray inArray, String roomName) throws IOException {
        for (int i = 0; i < inArray.size(); i++) {
            JsonObject accessory = inArray.get(i).getAsJsonObject();
            final int accessoryId = accessory.get("accessoryId").getAsInt();
            final boolean wasInvalid = processValue(accessoryId, accessory.get("accessoryValue").getAsJsonObject());
            processAttribute(accessoryId, accessory.get("accessoryAttribute").getAsJsonObject(), roomName,
                    wasInvalid || !isValid());
        }
    }

    private boolean processValue(int accessoryId, JsonObject accessoryValue) throws IOException {
        final @Nullable HoneywellAccessoryValueData accessoryValueData;
        boolean wasInvalid = true;
        if (accessories.containsKey(accessoryId)) {
            accessoryValueData = accessories.get(accessoryId);
        } else {
            accessoryValueData = new HoneywellAccessoryValueData();
            accessories.put(accessoryId, accessoryValueData);
        }
        if (null != accessoryValueData) {
            wasInvalid = accessoryValueData.isValid();
            accessoryValueData.updateData(accessoryValue);
        }
        return wasInvalid;
    }

    private void processAttribute(int accessoryId, JsonObject accessoryAttribute, String roomName, boolean update)
            throws IOException {
        final @Nullable HoneywellAccessoryAttributeData accessoryAttributeData;
        if (attributes.containsKey(accessoryId)) {
            accessoryAttributeData = attributes.get(accessoryId);
        } else {
            accessoryAttributeData = new HoneywellAccessoryAttributeData();
            attributes.put(accessoryId, accessoryAttributeData);
            update = true;
        }
        if (null != accessoryAttributeData && update) {
            accessoryAttribute.addProperty("roomName", roomName);
            accessoryAttributeData.updateData(accessoryAttribute);
        }
    }

    public @Nullable HoneywellAccessoryValueData getAccessoryData(int accessoryId) {
        return accessories.get(accessoryId);
    }

    public Map<String, String> getProperties(int accessoryId) {
        try {
            @Nullable
            JsonObject attributesJson = null;
            if (attributes.containsKey(accessoryId)) {
                final @Nullable HoneywellAccessoryAttributeData attribute = attributes.get(accessoryId);
                if (null != attribute) {
                    attributesJson = attribute.rawObject;
                    Map<String, String> stringMap = attributesJson.asMap().entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getAsString()));
                    return stringMap;
                }
            }
        } catch (Exception e) {
            logger.warn("HoneywellGroupData getProperties error: {}", e.getMessage());
        }
        return new HashMap<String, String>();
    }

    public String getType(int accessoryId) {
        try {
            if (attributes.containsKey(accessoryId)) {
                final @Nullable HoneywellAccessoryAttributeData attribute = attributes.get(accessoryId);
                if (null != attribute) {
                    final String type = attribute.rawObject.get("type").getAsString();
                    return type;
                }
            }
        } catch (Exception e) {
            logger.warn("Get type failed: {}", e.getMessage());
        }
        return "";
    }

    public boolean isEmpty() {
        return accessories.isEmpty();
    }
}
