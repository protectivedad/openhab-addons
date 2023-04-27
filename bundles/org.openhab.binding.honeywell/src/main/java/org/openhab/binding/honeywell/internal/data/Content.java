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
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link Content} used to validate strings as either JSON objects or arrays
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class Content {
    private final Logger logger = LoggerFactory.getLogger(Content.class);
    private final String rawContent;
    public final JSONArray rawArray;
    public final boolean validArray;
    public final JSONObject rawObject;
    public final boolean validObject;

    public Content(String rawContent) {
        this.rawContent = rawContent;
        JSONObject tempObject;
        try {
            tempObject = new JSONObject(rawContent);
            logger.debug("Content is a JSON Object.");
            logger.trace("Content: {}", tempObject.toString());
        } catch (Exception e) {
            tempObject = new JSONObject();
        }
        rawObject = tempObject;
        validObject = (!rawObject.isEmpty());

        JSONArray tempArray;
        try {
            tempArray = new JSONArray(rawContent);
            logger.debug("Content is a JSON Array.");
            logger.trace("Content: {}", tempArray.toString());
        } catch (Exception e) {
            tempArray = new JSONArray();
        }
        rawArray = tempArray;
        validArray = (!rawArray.isEmpty());
    }

    public String getRawContent() {
        return rawContent;
    }
}
