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

//import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;

/**
 * Interface to decouple Hoeywell Bridge Handler implementation from other code.
 *
 * @author Hilbrand Bouwkamp - Initial contribution
 * @author Anthony Sepa - Repurposed for honeywell binding
 */
@NonNullByDefault
public interface HoneywellAccountHandler extends ThingHandler {

    /**
     * @return The {@link ThingUID} associated with this Honeywell Account Handler
     */
    ThingUID getUID();

    /**
     * @return The label of the Honeywell Bridge associated with this Honeywell Account Handler
     */
    String getLabel();

    /**
     * @return Returns true if the Honeywell Bridge is authorized.
     */
    boolean isAuthorized();

    /**
     * @return List of Honeywell devices associated with this Honeywell Account Handler
     */
    // List<Device> listDevices();

    /**
     * @return Returns true if the device is online
     */
    boolean isOnline();

    /**
     * Calls Honeywell Api to obtain refresh and access tokens and persist data with Thing.
     *
     * @param redirectUrl The redirect url Honeywell calls back to
     * @param reqCode The unique code passed by Honeywell to obtain the refresh and access tokens
     * @return returns the name of the Honeywell user that is authorized
     */
    void authorize(String redirectUrl, String reqCode);

    /**
     * Returns true if the given Thing UID relates to this {@link HoneywellAccountHandler} instance.
     *
     * @param thingUID The Thing UID to check
     * @return true if it relates to the given Thing UID
     */
    boolean equalsThingUID(String thingUID);

    /**
     * Formats the Url to use to call Honeywell to authorize the application.
     *
     * @param redirectUri The uri Honeywell will redirect back to
     * @return the formatted url that should be used to call Honeywell Web Api with
     */
    String formatAuthorizationUrl(String redirectUri);
}
