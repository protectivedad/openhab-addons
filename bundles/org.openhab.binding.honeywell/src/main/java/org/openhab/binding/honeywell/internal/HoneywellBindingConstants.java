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

/**
 * The {@link HoneywellBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellBindingConstants {

    public static final String BINDING_ID = "honeywell";

    public static final ThingTypeUID BRIDGE_TYPE_OAUTH20 = new ThingTypeUID(BINDING_ID, "oauth20");
    public static final ThingTypeUID BRIDGE_TYPE_THERMOSTAT = new ThingTypeUID(BINDING_ID, "thermostat");
    public static final ThingTypeUID SENSOR_HONEYWELL_THING = new ThingTypeUID(BINDING_ID, "sensor");

    public static final String HONEYWELL_BLANK_JSON = "{}";
    public static final String HONEYWELL_TOOMANY_JSON = "{\"TO_MANY_REQUESTS\":\"true\"}";
    public static final String HONEYWELL_ERROR_JSON = "{ \"code\": \"error\", \"message\": \"'{}'\" }";

    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String URL_CONTENT_TYPE = "application/x-www-form-urlencoded";

    // Authorization related Servlet and resources aliases.
    public static final String HONEYWELL_ALIAS = "/connecthoneywell";
    public static final String HONEYWELL_IMG_ALIAS = "/img";
}
