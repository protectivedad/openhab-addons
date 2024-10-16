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
package org.openhab.binding.honeywell.internal.data;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link Content} defines the Honeywell api abstract data
 *
 * @author Anthony Sepa - Initial contribution
 */

@NonNullByDefault
abstract class HoneywellAbstractData {
    protected final Logger logger = LoggerFactory.getLogger(HoneywellAbstractData.class);
    protected JSONObject rawObject = new JSONObject();

    protected HoneywellAbstractData(JSONObject jsonAbstract) {
        rawObject = jsonAbstract;
    }

    protected HoneywellAbstractData(String rawString) throws IllegalArgumentException {
        updateData(rawString);
    }

    protected void updateData(String rawString) throws IllegalArgumentException {
        try {
            Content content = new Content(rawString);
            if (content.validObject) {
                rawObject = content.rawObject;
            } else {
                throw new IllegalArgumentException("Not a valid generic JSON object");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public String getAsString() {
        return rawObject.toString();
    }
}
