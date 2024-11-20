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

import static org.openhab.binding.honeywell.internal.data.HoneywellAccessoryAttributeData.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.honeywell.internal.data.HoneywellContent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The {@link HoneywellDiscoveryPriorityData} defines the Honeywell api Priority data
 * flatten json data as shown on the interactive documentation
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellDiscoveryPriorityData {
    private static final Map<String, String> HONEYWELL_TYPE_FILTER = new HashMap<>(2);
    static {
        HONEYWELL_TYPE_FILTER.put(HONEYWELL_ACCESSORY_TYPE_THERMOSTAT, "Thermostat Sensor");
        HONEYWELL_TYPE_FILTER.put(HONEYWELL_ACCESSORY_TYPE_SENSOR, "Sensor");
    }

    // Array of room objects
    public final HashMap<Integer, List<String>> accessoryDetails = new HashMap<>(6);

    public HoneywellDiscoveryPriorityData(String rawString) throws IllegalArgumentException {
        try {
            final HoneywellContent content = new HoneywellContent(rawString);
            if (content.validObject) {
                processContent(
                        content.rawObject.get("currentPriority").getAsJsonObject().get("rooms").getAsJsonArray());
            } else {
                throw new IllegalArgumentException("No valid priority JSON object");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private void processContent(JsonArray inArray) {
        for (int i = 0; i < inArray.size(); i++) {
            final JsonObject room = inArray.get(i).getAsJsonObject();
            processAccessories(room.get("accessories").getAsJsonArray(), room);
        }
    }

    private void processAccessories(JsonArray inArray, JsonObject room) {
        for (int i = 0; i < inArray.size(); i++) {
            final JsonObject accessory = inArray.get(i).getAsJsonObject();
            final String type = accessory.get("type").getAsString();
            if (HONEYWELL_TYPE_FILTER.containsKey(type)) {
                final int newKey = accessory.get("id").getAsInt();
                final List<String> details = new ArrayList<>();
                details.add(type);
                details.add(String.format("%s %s", room.get("roomName").getAsString(),
                        HONEYWELL_TYPE_FILTER.get(accessory.get("type").getAsString())));
                accessoryDetails.put(newKey, details);
            }
        }
    }
}
