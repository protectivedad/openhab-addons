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
package org.openhab.binding.honeywell.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link HoneywellBridgeConfig} class contains fields mapping thing configuration parameters.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellBridgeConfig {
    public int refresh = 120;
    public int timeout = 3000;

    public String consumerKey = "";
    public String consumerSecret = "";

    // If an authorizationCode is given then it will be used to get a refreshToken
    // regardless of whether refreshToken exists. If it is successful then the authorizationCode
    // will be cleared.
    public String authorizationCode = "";
    public String refreshToken = "";

    public int bufferSize = 2048;
    public int delay = 200;
}
