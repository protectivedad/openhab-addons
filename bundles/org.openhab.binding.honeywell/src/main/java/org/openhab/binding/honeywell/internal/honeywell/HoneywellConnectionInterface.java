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

    String HONEYWELL_END = "?apikey=%s&locationId=%s";
    String HONEYWELL_API = "https://api.honeywell.com/";
    String HONEYWELL_CONTENT_URL = HONEYWELL_API + "v2";
    String HONEYWELL_TOKEN_URL = HONEYWELL_API + "oauth2/token";
    String HONEYWELL_AUTH_URL = HONEYWELL_API + "oauth2/authorize";
    String HONEYWELL_LOCATIONS_URL = HONEYWELL_CONTENT_URL + "/locations?apikey=%s";
    String HONEYWELL_DEVICES_STUB = HONEYWELL_CONTENT_URL + "/devices";
    String HONEYWELL_DEVICES_URL = HONEYWELL_DEVICES_STUB + HONEYWELL_END;
    String HONEYWELL_THERMOSTAT_STUB = HONEYWELL_DEVICES_STUB + "/thermostats";
    String HONEYWELL_THERMOSTAT_URL = HONEYWELL_THERMOSTAT_STUB + "/%s" + HONEYWELL_END;
    String HONEYWELL_PRIORITY_URL = HONEYWELL_THERMOSTAT_STUB + "/%s/priority" + HONEYWELL_END;
    String HONEYWELL_GROUP_URL = HONEYWELL_THERMOSTAT_STUB + "/%s/group/%s/rooms" + HONEYWELL_END;

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
