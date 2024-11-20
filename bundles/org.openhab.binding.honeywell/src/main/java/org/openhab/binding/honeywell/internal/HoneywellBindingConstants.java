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
package org.openhab.binding.honeywell.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.type.ChannelTypeUID;

/**
 * The {@link HoneywellBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellBindingConstants {

    public static final String BINDING_ID = "honeywell";

    public static final ThingTypeUID HONEYWELL_OAUTH20_BRIDGE = new ThingTypeUID(BINDING_ID, "oauth20");
    public static final ThingTypeUID HONEYWELL_THERMOSTAT_BRIDGE = new ThingTypeUID(BINDING_ID, "thermostat");
    public static final ThingTypeUID HONEYWELL_SENSOR_THING = new ThingTypeUID(BINDING_ID, "sensor");

    public static final String INDOOR_TEMPERATURE = "indoor-temperature";
    public static final ChannelTypeUID INDOOR_TEMPERATURE_TYPE = new ChannelTypeUID("system", INDOOR_TEMPERATURE);
    public static final String HUMIDITY = "humidity";
    public static final ChannelTypeUID HUMIDITY_TYPE = new ChannelTypeUID(BINDING_ID, HUMIDITY);

    public static final String HONEYWELL_BLANK_JSON = "{}";
    public static final String HONEYWELL_TOOMANY_JSON = "{\"TO_MANY_REQUESTS\":\"true\"}";
    public static final String HONEYWELL_ERROR_JSON = "{ \"code\": \"error\", \"message\": \"%s\" }";

    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String URL_CONTENT_TYPE = "application/x-www-form-urlencoded";

    // Authorization related Servlet and resources aliases.
    public static final String HONEYWELL_ALIAS = "/connecthoneywell";
    public static final String HONEYWELL_IMG_ALIAS = "/img";

    public static final String PERMANENTHOLD = "PermanentHold";
}
