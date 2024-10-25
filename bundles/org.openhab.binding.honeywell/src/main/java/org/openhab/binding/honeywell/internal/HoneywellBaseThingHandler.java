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
package org.openhab.binding.honeywell.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionInterface;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellBaseThingHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellBaseThingHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(HoneywellBaseThingHandler.class);

    protected @Nullable HoneywellConnectionInterface honeywellApi;
    protected String deviceUrl = "";
    protected int locationId = 9999999;
    protected String deviceId = "";
    protected String uniqueId = "";

    public HoneywellBaseThingHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        // all done in parent
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("Bridge status changed for: '{}'", uniqueId);
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            honeywellApi = null;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            honeywellApi = null;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        } else if (bridgeStatusInfo.getStatusDetail() == ThingStatusDetail.CONFIGURATION_PENDING) {
            honeywellApi = null;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
        } else {
            final @Nullable HoneywellOauth20Handler bridgeHandler = getBridgeHandler();
            if (null == bridgeHandler) {
                honeywellApi = null;
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Bridge handler not found!");
            } else {
                honeywellApi = (HoneywellConnectionInterface) bridgeHandler;
                updateStatus(ThingStatus.ONLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                        "Waiting for information from Honeywell");
            }
        }
    }

    /**
     * Return the bridge status.
     */
    protected ThingStatusInfo getBridgeStatus() {
        final Bridge bridge = getBridge();
        return (null == bridge) ? new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, null)
                : bridge.getStatusInfo();
    }

    /**
     * Return the bride handler.
     */
    protected @Nullable HoneywellOauth20Handler getBridgeHandler() {
        final Bridge bridge = getBridge();
        return (null == bridge) ? null : (HoneywellOauth20Handler) bridge.getHandler();
    }

    public boolean isOnline() {
        return thing.getStatus() == ThingStatus.ONLINE;
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO: Look at some common items Temp/Humid
    }
}
