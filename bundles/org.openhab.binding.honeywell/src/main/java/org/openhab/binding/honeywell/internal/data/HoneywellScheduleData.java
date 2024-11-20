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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellScheduleData extends HoneywellAbstractData {
    private final List<StateOption> allowedSetpointStatus;

    private final JsonArray availableScheduleTypes = new JsonArray();
    private JsonObject currentSchedulePeriod = new JsonObject();
    private JsonObject vacationHold = new JsonObject();
    private ScheduleType scheduleType = ScheduleType.NONE;
    private ScheduleSubType scheduleSubType = ScheduleSubType.NULL;
    private ScheduleStatus scheduleStatus = ScheduleStatus.OFF;
    private PriorityType priorityType = PriorityType.HOUSE;

    protected enum SetpointStatus {
        NO(new StateOption("NoHold", "Schedule")),
        PERMANENT(new StateOption(PERMANENTHOLD, "Hold")),
        TEMPORARY(new StateOption("TemporaryHold", "Hold til Next Schedule")),
        UNTIL(new StateOption("HoldUntil", "Hold til Next Period"));

        private StateOption setpointStatus;

        SetpointStatus(StateOption setpointStatus) {
            this.setpointStatus = setpointStatus;
        }

        public StateOption getSetpointStatus() {
            return setpointStatus;
        }

        public static Optional<SetpointStatus> get(String command) {
            return Arrays.stream(SetpointStatus.values()).filter(m -> m.setpointStatus.getValue().equals(command))
                    .findFirst();
        }
    }

    public enum ScheduleType {
        NONE("None"),
        GEO("Geofence");

        private String scheduleType;

        ScheduleType(String scheduleType) {
            this.scheduleType = scheduleType;
        }

        public String getScheduleType() {
            return scheduleType;
        }

        public static Optional<ScheduleType> get(String scheduleType) {
            return Arrays.stream(ScheduleType.values()).filter(m -> m.scheduleType.equals(scheduleType)).findFirst();
        }
    }

    public enum ScheduleSubType {
        NULL("null"),
        NA("NA");

        private String scheduleSubType;

        ScheduleSubType(String scheduleSubType) {
            this.scheduleSubType = scheduleSubType;
        }

        public String getScheduleSubType() {
            return scheduleSubType;
        }

        public static Optional<ScheduleSubType> get(String scheduleSubType) {
            return Arrays.stream(ScheduleSubType.values()).filter(m -> m.scheduleSubType.equals(scheduleSubType))
                    .findFirst();
        }
    }

    public enum ScheduleStatus {
        OFF("Pause"),
        ON("Resume");

        private String scheduleStatus;

        ScheduleStatus(String scheduleStatus) {
            this.scheduleStatus = scheduleStatus;
        }

        public String getScheduleStatus() {
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

    public HoneywellScheduleData(List<StateOption> allowedSetpointStatus) {
        this.allowedSetpointStatus = allowedSetpointStatus;
    }

    protected synchronized void updateData(JsonObject rawJson) throws IOException {
        logger.trace("Raw ScheduleData: '{}'", rawJson);
        super.updateData(rawJson);
        try {
            availableScheduleTypes.addAll(rawObject.get("scheduleCapabilities").getAsJsonObject()
                    .get("availableScheduleTypes").getAsJsonArray());
            setScheduleType(rawObject.get("scheduleType").getAsJsonObject().get("scheduleType").getAsString());
            if (rawObject.get("scheduleType").getAsJsonObject().has("scheduleSubType")) {
                setScheduleSubType(
                        rawObject.get("scheduleType").getAsJsonObject().get("scheduleSubType").getAsString());
            } else {
                scheduleSubType = ScheduleSubType.NULL;
            }
            currentSchedulePeriod = rawObject.get("currentSchedulePeriod").getAsJsonObject().deepCopy();
            vacationHold = rawObject.get("vacationHold").getAsJsonObject().deepCopy();
            setScheduleStatus(rawObject.get("scheduleStatus").getAsString());
            setPriorityType(rawObject.get("priorityType").getAsString());
        } catch (Exception e) {
            isValid = false;
            throw new IOException("Schedule data update is not a valid item: " + e.getMessage());
        }
        setIsValid();
    }

    public void updateAllowedSetpointStatus() {
        allowedSetpointStatus.clear();
        if (ScheduleStatus.OFF == scheduleStatus) {
            allowedSetpointStatus.add(SetpointStatus.PERMANENT.getSetpointStatus());
        } else if (ScheduleType.GEO == scheduleType) {
            allowedSetpointStatus.add(SetpointStatus.NO.getSetpointStatus());
            allowedSetpointStatus.add(SetpointStatus.TEMPORARY.getSetpointStatus());
        } else {
            allowedSetpointStatus.add(SetpointStatus.NO.getSetpointStatus());
            allowedSetpointStatus.add(SetpointStatus.TEMPORARY.getSetpointStatus());
            allowedSetpointStatus.add(SetpointStatus.PERMANENT.getSetpointStatus());
            allowedSetpointStatus.add(SetpointStatus.UNTIL.getSetpointStatus());
        }
    }

    public List<StateOption> getAvailableScheduleTypes() {
        List<StateOption> options = new ArrayList<>();
        if (isValid) {
            for (int i = 0; i < availableScheduleTypes.size(); i++) {
                final String scheduleType = availableScheduleTypes.get(i).getAsString();
                options.add(new StateOption(scheduleType, scheduleType));
            }
        }
        if (options.isEmpty()) {
            options.add(new StateOption("None", "None"));
        }
        return options;
    }

    protected State getCurrentSchedulePeriod() {
        try {
            return (isValid) ? new StringType(String.format("%s-%s", currentSchedulePeriod.get("day").getAsString(),
                    currentSchedulePeriod.get("period").getAsString())) : UnDefType.UNDEF;
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    protected State getVacationHold() {
        try {
            return (isValid) ? (vacationHold.get("enabled").getAsBoolean() ? OnOffType.ON : OnOffType.OFF)
                    : UnDefType.UNDEF;
        } catch (Exception e) {
            return UnDefType.UNDEF;
        }
    }

    private String setScheduleType(String scheduleType) {
        if (ScheduleType.get(scheduleType).isPresent()) {
            ScheduleType.get(scheduleType).ifPresent((s) -> this.scheduleType = s);
        } else {
            return String.format("Schedule type '%s' failed", scheduleType);
        }
        return "";
    }

    protected State getScheduleType() {
        return (isValid) ? new StringType(scheduleType.getScheduleType()) : UnDefType.UNDEF;
    }

    private String setScheduleSubType(String scheduleSubType) {
        if (ScheduleSubType.get(scheduleSubType).isPresent()) {
            ScheduleSubType.get(scheduleSubType).ifPresent((s) -> this.scheduleSubType = s);
        } else {
            return String.format("Schedule type '%s' failed", scheduleSubType);
        }
        return "";
    }

    protected State getScheduleSubType() {
        return (isValid) ? new StringType(scheduleSubType.getScheduleSubType()) : UnDefType.UNDEF;
    }

    protected String setScheduleStatus(String scheduleStatus) {
        if (ScheduleStatus.get(scheduleStatus).isPresent()) {
            ScheduleStatus.get(scheduleStatus).ifPresent((s) -> this.scheduleStatus = s);
            updateAllowedSetpointStatus();
        } else {
            return String.format("Schedule status '%s' failed", scheduleStatus);
        }
        return "";
    }

    public State isScheduleStatus() {
        return (isValid) ? ((ScheduleStatus.ON == scheduleStatus) ? OnOffType.ON : OnOffType.OFF) : UnDefType.UNDEF;
    }

    protected State getScheduleStatus() {
        return (isValid) ? new StringType(scheduleStatus.getScheduleStatus()) : UnDefType.UNDEF;
    }

    protected String setPriorityType(String priorityType) {
        if (PriorityType.get(priorityType).isPresent()) {
            PriorityType.get(priorityType).ifPresent((t) -> this.priorityType = t);
        } else {
            return String.format("Priority type '%s' failed", priorityType);
        }
        return "";
    }

    protected State getPriorityType() {
        return (isValid) ? new StringType(priorityType.getPriorityType()) : UnDefType.UNDEF;
    }

    public List<StateOption> getAllowedSetpointStatus() {
        return allowedSetpointStatus;
    }
}
