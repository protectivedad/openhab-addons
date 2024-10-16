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

/**
 * The {@link HoneywellAuthException} is an exception after authorization errors
 *
 * @author Jan N. Klug - Initial contribution
 * @author Anthony Sepa - Imported into Honeywell binding
 */
@NonNullByDefault
public class HoneywellAuthException extends Exception {
    private static final long serialVersionUID = 1L;

    public HoneywellAuthException() {
        super();
    }

    public HoneywellAuthException(String message) {
        super(message);
    }
}
