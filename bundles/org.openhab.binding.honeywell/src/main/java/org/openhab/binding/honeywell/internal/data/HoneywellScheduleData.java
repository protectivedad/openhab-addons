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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;

import com.google.gson.JsonObject;

/**
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellScheduleData extends HoneywellAbstractData {
    public static final String PERMANENTHOLD = "PermanentHold";
    public static final String NOHOLD = "NoHold";

    private final List<StateOption> allowedSetpointStatus;

    private JsonObject currentSchedulePeriod = new JsonObject();
    private JsonObject vacationHold = new JsonObject();
    private ScheduleType scheduleType = ScheduleType.NONE;
    private ScheduleStatus scheduleStatus = ScheduleStatus.OFF;
    private PriorityType priorityType = PriorityType.HOUSE;

    protected enum SetpointStatus {
        NO(new StateOption(NOHOLD, "Schedule")),
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
        GEO("Geofence"),
        TIMED("Timed");

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
            setScheduleType(rawObject.get("scheduleType").getAsJsonObject().get("scheduleType").getAsString());
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
