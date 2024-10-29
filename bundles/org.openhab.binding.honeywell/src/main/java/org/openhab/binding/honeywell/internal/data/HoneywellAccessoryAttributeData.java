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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONException;

/**
 * The {@link HoneywellAccessoryAttributeData} defines the Honeywell api accessory attributes information
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellAccessoryAttributeData extends HoneywellAbstractData {
    public HoneywellAccessoryAttributeData(String rawJson) throws JSONException {
        try {
            super.updateData(rawJson);
        } catch (Exception e) {
            isValid = false;
            throw new JSONException("JSON object is not a valid sensor item");
        }
        isValid = true;
    }
}
