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
package org.openhab.binding.honeywell.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link HoneywellChannelConfig} class contains fields mapping channel configuration parameters.
 *
 * @author Jan N. Klug - Initial contribution
 * @author Anthony Sepa - Adapted for use in Honeywell binding
 */
@NonNullByDefault
public class HoneywellChannelConfig {
    public String stateTransformation = "";
    public String commandTransformation = "";
    public String stateContent = "";
}
