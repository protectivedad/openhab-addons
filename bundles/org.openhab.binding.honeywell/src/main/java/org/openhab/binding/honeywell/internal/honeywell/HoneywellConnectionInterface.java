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
package org.openhab.binding.honeywell.internal.honeywell;

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
    public final static String HONEYWELL_API = "https://api.honeywell.com/";
    public final static String HONEYWELL_CONTENT_URL = HONEYWELL_API + "/v2";
    public final static String HONEYWELL_LOCATIONS_URL = HONEYWELL_CONTENT_URL + "/locations?apikey=%s";
    public final static String HONEYWELL_DEVICES_STUB = HONEYWELL_CONTENT_URL + "/devices";
    public final static String HONEYWELL_DEVICES_URL = HONEYWELL_DEVICES_STUB + HONEYWELL_END;
    public final static String HONEYWELL_SCHEDULE_URL = HONEYWELL_DEVICES_STUB + "/schedule/%s" + HONEYWELL_END
            + "&type=regular";
    public final static String HONEYWELL_THERMOSTAT_STUB = HONEYWELL_DEVICES_STUB + "/thermostats";
    public final static String HONEYWELL_THERMOSTAT_URL = HONEYWELL_THERMOSTAT_STUB + "/%s" + HONEYWELL_END;
    public final static String HONEYWELL_PRIORITY_URL = HONEYWELL_THERMOSTAT_STUB + "/%s/priority" + HONEYWELL_END;
    public final static String HONEYWELL_FAN_URL = HONEYWELL_THERMOSTAT_STUB + "/%s/fan" + HONEYWELL_END;

    public void resetAccessToken(String authorizationCode, String refreshToken);

    public String honeywellUrl(HoneywellResourceType resourceType, int locationId, String deviceId);

    public String refreshCache();

    public Boolean hasAccessToken();

    public String getCached(String honeywellUrl);

    public String getOnDemand(String honeywellUrl);

    public String postHttpHoneywell(String honeywellUrl, String stateContent);

    public String getRefreshToken();

    public void addProcessCache(HoneywellCacheProcessor cacheProcessor, String stateUrl);

    public void delProcessCache(HoneywellCacheProcessor cacheProcessor);

    public String getThermostatDiscoveryInfo();

    public String getSensorDiscoveryInfo(int locationId, String thermostatId);
}
