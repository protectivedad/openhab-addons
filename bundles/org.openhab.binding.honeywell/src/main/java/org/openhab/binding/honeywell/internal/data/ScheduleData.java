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

import java.util.Arrays;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;

/**
 * The {@link Content} defines the Honeywell api GeoFences data
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class ScheduleData extends HoneywellAbstractData {
    private final HoneywellConnectionInterface honeywellApi;
    private final String deviceUrl;
    public final String deviceID;
    private final String scheduleType;

    private enum ScheduleType {
        NONE("None"),
        GEOFENCE("Geofence"),
        GEOFENCEDWITHSLEEP("GeofencedWithSleep"),
        TIMED("Timed"),
        PARTNER("Partner");

        private String scheduleType;

        ScheduleType(String envScheduleType) {
            this.scheduleType = envScheduleType;
        }

        public String getScheduleType() {
            return scheduleType;
        }

        public static Optional<ScheduleType> get(String scheduleType) {
            return Arrays.stream(ScheduleType.values()).filter(st -> st.scheduleType.equals(scheduleType)).findFirst();
        }
    }

    public ScheduleData(HoneywellConnectionInterface honeywellApi, String deviceUrl) throws IllegalArgumentException {
        super(honeywellApi.getOnDemand(deviceUrl));
        this.honeywellApi = honeywellApi;
        this.deviceUrl = deviceUrl;
        try {
            deviceID = rawObject.getString("deviceID");
            scheduleType = ScheduleType.get(rawObject.getString("scheduleType")).get().getScheduleType();
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON object is not a valid schedule item");
        }
        logger.trace("Create schedule data for '{}'", deviceID);
    }

    public String getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(String scheduleType) {
        if (ScheduleType.get(scheduleType).isPresent()) {
            final String tempScheduleType = ScheduleType.get(scheduleType).get().getScheduleType();
            rawObject.put("scheduleType", tempScheduleType);
            scheduleType = ScheduleType.get(rawObject.getString("scheduleType")).get().getScheduleType();
        } else {
            throw new IllegalArgumentException("Not a valid thermostat mode");
        }
    }

    public void postUpdate() {
        honeywellApi.postHttpHoneywell(deviceUrl, rawObject.toString());
    }

    public void postUpdate(String command) {
        honeywellApi.postHttpHoneywell(deviceUrl, command);
    }

    public void updateData() throws IllegalArgumentException {
        updateData(honeywellApi.getOnDemand(deviceUrl));
    }
}
