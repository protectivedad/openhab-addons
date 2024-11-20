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
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The {@link HoneywellContent} used to validate strings as either JSON objects or arrays
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellContent {
    private final Logger logger = LoggerFactory.getLogger(HoneywellContent.class);
    public final JsonArray rawArray;
    public final boolean validArray;
    public final JsonObject rawObject;
    public final boolean validObject;

    public HoneywellContent(String rawContent) {
        @Nullable
        JsonObject tempObject;
        try {
            tempObject = new Gson().fromJson(rawContent, JsonObject.class);
            logger.debug("Content is a JSON Object.");
        } catch (Exception e) {
            tempObject = new JsonObject();
        }
        rawObject = (null == tempObject ? new JsonObject() : tempObject);
        validObject = (!rawObject.isEmpty());

        @Nullable
        JsonArray tempArray;
        try {
            tempArray = new Gson().fromJson(rawContent, JsonArray.class);
            logger.debug("Content is a JSON Array.");
        } catch (Exception e) {
            tempArray = new JsonArray();
        }
        rawArray = (null == tempArray ? new JsonArray() : tempArray);
        validArray = (!rawArray.isEmpty());
    }
}
