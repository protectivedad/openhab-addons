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
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellAbstractData} defines the Honeywell api abstract data
 *
 * @author Anthony Sepa - Initial contribution
 */

@NonNullByDefault
abstract class HoneywellAbstractData {
    protected final Logger logger = LoggerFactory.getLogger(HoneywellAbstractData.class);
    protected JSONObject rawObject = new JSONObject();

    protected void updateData(String rawString) throws JSONException {
        try {
            HoneywellContent content = new HoneywellContent(rawString);
            if (content.validObject) {
                rawObject = content.rawObject;
            } else {
                throw new JSONException("Not a valid generic JSON object");
            }
        } catch (Exception e) {
            throw new JSONException(e.getMessage());
        }
    }
}
