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

import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.BINDING_ID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;

import com.google.gson.JsonObject;

/**
 * The {@link HoneywellFanData} defines the Honeywell api fan data and acts as the gatekeeper
 * ensuring the information is validated and consistent. Each refresh the item should be updated with updateData method.
 * Individual item updates are transient.
 * 
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellFanData extends HoneywellAbstractData {
    public static final String FAN_MODE = "fanmode";
    public static final ChannelTypeUID FAN_MODE_TYPE = new ChannelTypeUID(BINDING_ID, FAN_MODE);

    private final List<StateOption> allowedModes = new ArrayList<>();
    private String mode = "Unknown";

    /**
     * Update just the control information. The constraints are assumed to be relatively stable. If the update succeeds
     * the information is marked valid. Otherwise the information is marked invalid.
     * 
     * @param rawJson - The JSON formatted information from the Honeywell feed
     */
    protected void updateData(JsonObject rawJson) throws IOException {
        try {
            logger.trace("Raw FanData: '{}'", rawJson);
            super.updateData(rawJson);
            // constraints first
            this.allowedModes.clear();
            rawJson.get("allowedModes").getAsJsonArray().forEach((m) -> {
                this.allowedModes.add(new StateOption(m.getAsString(), m.getAsString()));
            });
            mode = rawObject.getAsJsonObject("changeableValues").get("mode").getAsString();
        } catch (Exception e) {
            isValid = false;
            throw new IOException("Changeable values update is not a valid item: " + e.getMessage());
        }
        setIsValid();
    }

    private boolean validMode(String mode) {
        return allowedModes.stream().anyMatch(m -> m.getValue().equals(mode));
    }

    protected State getState() {
        if (!isValid()) {
            return UnDefType.UNDEF;
        }
        return new StringType(mode);
    }

    public String setState(String cmdString) {
        if (!isValid()) {
            return "Invalid fan data, refusing to set: " + cmdString;
        }
        if (validMode(cmdString)) {
            mode = cmdString;
            return "";
        }
        return String.format("Fan mode '%s' failed", cmdString);
    }

    /**
     * 
     * @return A valid changeableValues object as a JSON formatted string
     * @throws JSONException - Object is not valid
     */
    public String toJson() throws IOException {
        if (!isValid()) {
            throw new IOException("Invalid fan data, refusing to create json");
        }
        try {
            rawObject.addProperty("mode", mode);
            return rawObject.toString();
        } catch (Exception e) {
            throw new IOException("Erro creating Fan json", e);
        }
    }

    public List<StateOption> getFanAllowedModes() {
        if (!isValid()) {
            allowedModes.clear();
            allowedModes.add(new StateOption("Unknown", "Unknown"));
        }
        return allowedModes;
    }
}
