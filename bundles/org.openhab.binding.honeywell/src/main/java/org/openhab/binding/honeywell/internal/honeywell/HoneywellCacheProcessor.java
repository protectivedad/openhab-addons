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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.honeywell.internal.data.HoneywellGroupData;

/**
 * The {@link HoneywellCacheProcessor} defines the interface for processing caches from Honeywell. The implementations
 * can feed a cache, comsume from cacche or both. OAuth feeds, thermastat feeds and consumes, sesnor just consumes.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public interface HoneywellCacheProcessor {

    /**
     * process the cache
     *
     */
    default void processCache(String returnString) {
        return;
    }

    default void processCache(HoneywellGroupData groupData) {
        return;
    }
}
