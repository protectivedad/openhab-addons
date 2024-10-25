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
package org.openhab.binding.honeywell.internal.honeywell;

import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.*;

import java.io.IOException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.honeywell.internal.config.HoneywellResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellConnectionInterface} links to Honeywell API
 *
 * @author Anthony Sepa - Initial contribution
 */

@NonNullByDefault
public interface HoneywellConnectionInterface {
    public final Logger logger = LoggerFactory.getLogger(HoneywellConnectionInterface.class);

    public final static String HONEYWELL_END = "?apikey=%s&locationId=%s";
    public final static String HONEYWELL_LOCATIONS_URL = HONEYWELL_CONTENT_URL + "/locations?apikey=%s";
    public final static String HONEYWELL_DEVICES_STUB = HONEYWELL_CONTENT_URL + "/devices";
    public final static String HONEYWELL_DEVICES_URL = HONEYWELL_DEVICES_STUB + HONEYWELL_END;
    public final static String HONEYWELL_THERMOSTAT_STUB = HONEYWELL_DEVICES_STUB + "/thermostats";
    public final static String HONEYWELL_THERMOSTAT_URL = HONEYWELL_THERMOSTAT_STUB + "/%s" + HONEYWELL_END;
    public final static String HONEYWELL_PRIORITY_URL = HONEYWELL_THERMOSTAT_STUB + "/%s/priority" + HONEYWELL_END;
    public final static String HONEYWELL_GROUP_URL = HONEYWELL_THERMOSTAT_STUB + "/%s/group/%s/rooms" + HONEYWELL_END;

    String honeywellUrl(HoneywellResourceType resourceType, int locationId, String deviceId);

    String honeywellUrl(HoneywellResourceType resourceType, int locationId, String deviceId, int groupId);

    String getCached(String honeywellUrl);

    /**
     * 
     * public entry to POST to the API
     * 
     * @param honeywellUrl - URL
     * @param stateContent - openHAB state as a string
     * @return Json object as a string
     * @throws IOException - Unable to POST but should be okay later
     * @throws IllegalStateException - Configuration error thing needs invesitagting
     */
    String postHttpHoneywell(String honeywellUrl, String stateContent) throws IOException, IllegalStateException;

    String getThermostatDiscoveryInfo() throws IOException, IllegalStateException;

    String getSensorDiscoveryInfo(int locationId, String thermostatId) throws IOException, IllegalStateException;
}
