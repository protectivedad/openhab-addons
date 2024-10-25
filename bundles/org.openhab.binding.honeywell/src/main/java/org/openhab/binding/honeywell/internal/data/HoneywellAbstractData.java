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

import java.io.IOException;

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
// TODO: institue an updated date so stale information can be determined by the user
@NonNullByDefault
abstract class HoneywellAbstractData {
    protected final Logger logger = LoggerFactory.getLogger(HoneywellAbstractData.class);
    protected JSONObject rawObject = new JSONObject();
    protected boolean isValid = false;

    protected void updateData(JSONObject rawJson) {
        rawObject = rawJson;
    }

    protected void updateData(String rawString) throws JSONException, IOException {
        try {
            HoneywellContent content = new HoneywellContent(rawString);
            if (content.validObject) {
                rawObject = content.rawObject;
                isValid = false;
            } else {
                throw new JSONException("Empty JSON");
            }
        } catch (Exception e) {
            logger.error("rawContent not understood: {}", rawString);
            throw new JSONException("Data received from Honeywell not understood, see error log");
        }
    }

    /**
     * check to make sure the the API information is set and the data is valid
     * 
     * @return
     */
    public boolean isValid() {
        return isValid;
    }

    public void setIsValid() {
        isValid = true;
        rawObject = new JSONObject();
    }

    public boolean isError() {
        return rawObject.has("code") && rawObject.has("message");
    }
}
