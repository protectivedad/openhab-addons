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
    public static final ThingTypeUID THERMOSTAT_HONEYWELL_THING = new ThingTypeUID(BINDING_ID, "thermostat");
    public static final ThingTypeUID SCHEDULE_HONEYWELL_THING = new ThingTypeUID(BINDING_ID, "schedule");
    public static final ThingTypeUID SENSOR_HONEYWELL_THING = new ThingTypeUID(BINDING_ID, "sensor");
    public static final String PUBLISH_TRIGGER_CHANNEL = "publishTrigger";
    public static final String HONEYWELL_API = "https://api.honeywell.com/";
    public static final String HONEYWELL_CONTENT_URL = HONEYWELL_API + "/v2";
}
