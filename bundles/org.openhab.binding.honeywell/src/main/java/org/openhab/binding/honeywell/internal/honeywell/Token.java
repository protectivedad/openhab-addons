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
package org.openhab.binding.honeywell.internal.honeywell;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.honeywell.internal.data.Content;

/**
 * The {@link Token} defines the Honeywell api token response
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class Token extends Content {
    public final boolean validToken;

    public Token(String rawContent) throws IllegalArgumentException {
        super(rawContent);
        String accessToken = "";
        try {
            accessToken = rawObject.get("access_token").toString();
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        validToken = (!accessToken.isEmpty());
    }

    public String getAccessToken() {
        return validToken ? rawObject.getString("access_token") : "";
    }

    public String getRefreshToken() {
        return validToken ? rawObject.getString("refresh_token") : "";
    }

    public int getExpiresIn() {
        return validToken ? rawObject.getInt("expires_in") : 0;
    }

    // Use expires in to create an update time with 300s to spare
    public long getUpdateIn() {
        return validToken ? (rawObject.getInt("expires_in") - 300) * 1000 : 0;
    }

    public String getTokenType() {
        return validToken ? rawObject.getString("token_type") : "";
    }
}
