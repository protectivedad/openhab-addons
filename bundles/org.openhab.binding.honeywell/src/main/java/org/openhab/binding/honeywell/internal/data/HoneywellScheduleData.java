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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;

/**
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellScheduleData extends HoneywellAbstractData {
    private final JSONArray availableScheduleTypes = new JSONArray();
    private final JSONObject scheduleType = new JSONObject();
    private final JSONObject currentSchedulePeriod = new JSONObject();
    private final JSONObject vacationHold = new JSONObject();
    private ScheduleStatus scheduleStatus = ScheduleStatus.OFF;
    private PriorityType priorityType = PriorityType.HOUSE;

    public enum ScheduleStatus {
        OFF("Pause"),
        ON("Resume");

        private String scheduleStatus;

        ScheduleStatus(String scheduleStatus) {
            this.scheduleStatus = scheduleStatus;
        }

        public String getStatus() {
            return scheduleStatus;
        }

        public static Optional<ScheduleStatus> get(String scheduleStatus) {
            return Arrays.stream(ScheduleStatus.values()).filter(m -> m.scheduleStatus.equals(scheduleStatus))
                    .findFirst();
        }
    }

    private enum PriorityType {
        HOUSE("WholeHouse"),
        PICK("PickARoom"),
        FOLLOW("FollowMe");

        private String priorityType;

        PriorityType(String envPriorityType) {
            this.priorityType = envPriorityType;
        }

        public String getPriorityType() {
            return priorityType;
        }

        public static Optional<PriorityType> get(String priorityType) {
            return Arrays.stream(PriorityType.values()).filter(m -> m.priorityType.equals(priorityType)).findFirst();
        }
    }

    public synchronized void updateData(JSONObject rawJson) throws JSONException {
        logger.trace("Raw DeviceData: '{}'", rawJson);
        super.updateData(rawJson);
        try {
            availableScheduleTypes
                    .put(rawObject.getJSONObject("scheduleCapabilities").getJSONArray("availableScheduleTypes"));
            rawObject.getJSONObject("scheduleType").keys().forEachRemaining(key -> {
                scheduleType.put(key, rawObject.getJSONObject("scheduleType").get(key));
            });
            rawObject.getJSONObject("currentSchedulePeriod").keys().forEachRemaining(key -> {
                currentSchedulePeriod.put(key, rawObject.getJSONObject("currentSchedulePeriod").get(key));
            });
            rawObject.getJSONObject("vacationHold").keys().forEachRemaining(key -> {
                vacationHold.put(key, rawObject.getJSONObject("vacationHold").get(key));
            });
            setScheduleStatus(rawObject.getString("scheduleStatus"));
            setPriorityType(rawObject.getString("priorityType"));
        } catch (Exception e) {
            isValid = false;
            throw new JSONException("Schedule data update is not a valid item: " + e.getMessage());
        }
        setIsValid();
    }

    public List<StateOption> getAvailableScheduleTypes() {
        List<StateOption> options = new ArrayList<>();
        if (isValid) {
            for (int i = 0; i < availableScheduleTypes.length(); i++) {
                final String scheduleType = availableScheduleTypes.getString(i);
                if (null != scheduleType) {
                    options.add(new StateOption(scheduleType, scheduleType));
                }
            }
        }
        if (options.isEmpty()) {
            options.add(new StateOption("None", "None"));
        }
        return options;
    }

    public State getScheduleType() {
        String scheduleType = "";
        String scheduleSubType = "";
        try {
            scheduleType = this.scheduleType.getString("scheduleType");
            scheduleSubType = this.scheduleType.getString("scheduleSubType");
        } catch (Exception e) {
            // move on
        }
        if (scheduleType.isEmpty() || !isValid) {
            return UnDefType.UNDEF;
        } else if (scheduleSubType.isEmpty()) {
            return new StringType(scheduleType);
        }
        return new StringType(String.format("%s-%s", scheduleType, scheduleSubType));
    }

    public State getCurrentSchedulePeriod() {
        try {
            return (isValid) ? new StringType(String.format("%s-%s", currentSchedulePeriod.getString("day"),
                    currentSchedulePeriod.getString("period"))) : UnDefType.UNDEF;
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    public State getVacationHold() {
        try {
            return (isValid) ? (vacationHold.getBoolean("enabled") ? OnOffType.ON : OnOffType.OFF) : UnDefType.UNDEF;
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    public String setScheduleStatus(String scheduleStatus) {
        if (ScheduleStatus.get(scheduleStatus).isPresent()) {
            ScheduleStatus.get(scheduleStatus).ifPresent((s) -> this.scheduleStatus = s);
        } else {
            return String.format("Schedule status '{}' failed", scheduleStatus);
        }
        return "";
    }

    public State getScheduleStatus() {
        return (isValid) ? new StringType(scheduleStatus.getStatus()) : UnDefType.UNDEF;
    }

    public String setPriorityType(String priorityType) {
        if (PriorityType.get(priorityType).isPresent()) {
            PriorityType.get(priorityType).ifPresent((t) -> this.priorityType = t);
        } else {
            return String.format("Priority type '{}' failed", priorityType);
        }
        return "";
    }

    public State getPriorityType() {
        return (isValid) ? new StringType(priorityType.getPriorityType()) : UnDefType.UNDEF;
    }
}
