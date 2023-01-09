/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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

import java.util.Collection;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.honeywell.internal.config.HoneywellBridgeConfig;
import org.openhab.binding.honeywell.internal.discovery.HoneywellDiscoveryService;
import org.openhab.binding.honeywell.internal.honeywell.HoneywellConnectionClass;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Anthony Sepa - Initial contribution
 */
@NonNullByDefault
public class HoneywellBridgeHandler extends BaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(HoneywellBridgeHandler.class);
    public HoneywellBridgeConfig bridgeConfig = getConfigAs(HoneywellBridgeConfig.class);

    private final HttpClient secureClient;
    public final HoneywellConnectionClass honeywellApi;
    private @Nullable ScheduledFuture<?> cachedFuture;

    public HoneywellBridgeHandler(Bridge thing, HttpClientProvider honeywellClientProvider)
            throws IllegalStateException {
        super(thing);
        secureClient = honeywellClientProvider.getSecureClient();
        honeywellApi = new HoneywellConnectionClass(bridgeConfig, secureClient);
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(HoneywellDiscoveryService.class);
    }

    @Override
    public void initialize() {
        Configuration tempConfig = editConfiguration();
        tempConfig.put("refreshToken", honeywellApi.getRefreshToken());
        updateConfiguration(tempConfig);

        final Random random = new Random();
        final int startDelay = random.nextInt(bridgeConfig.refresh);
        cachedFuture = scheduler.scheduleWithFixedDelay(honeywellApi::refreshCache, startDelay, bridgeConfig.refresh,
                TimeUnit.SECONDS);
        updateStatus(ThingStatus.ONLINE);
    }

    private void stop() {
        // stop update tasks
        final ScheduledFuture<?> job = cachedFuture;
        if (job != null) {
            job.cancel(true);
            cachedFuture = null;
        }
    }

    public String getConsumerKey() {
        return bridgeConfig.consumerKey;
    }

    /**
     * Does nothing in the base implementation.
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("handleCommand() HoneywellBridgeHandler: {}", channelUID);
    }

    @Override
    public void dispose() {
        logger.debug("Disposal phase");
        stop();
        super.dispose();
    }
}
