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

import static org.openhab.binding.honeywell.internal.HoneywellBindingConstants.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.honeywell.internal.HoneywellOauth20Handler;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellAuthService} class to manage the servlets and bind authorization servlet to bridges.
 *
 * @author Andreas Stenlund - Initial contribution
 * @author Hilbrand Bouwkamp - Made this the service class instead of only interface. Added templates
 * @author Anthony Sepa - Repurposed for Honeywell removed interface bloat
 */
@Component(service = HoneywellAuthService.class, configurationPid = "binding.honeywell.authService")
@NonNullByDefault
public class HoneywellAuthService {

    private static final String TEMPLATE_PATH = "templates/";
    private static final String TEMPLATE_BRIDGE = TEMPLATE_PATH + "bridge.html";
    private static final String TEMPLATE_INDEX = TEMPLATE_PATH + "index.html";
    private static final String ERROR_UKNOWN_BRIDGE = "Returned 'state' but doesn't match any Bridges. Has the bridge been removed?";

    private final Logger logger = LoggerFactory.getLogger(HoneywellAuthService.class);

    private final Map<String, HoneywellOauth20Handler> handlers = new HashMap<>();

    private @NonNullByDefault({}) BundleContext bundleContext;
    private @NonNullByDefault({}) HttpService httpService;

    @Activate
    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        try {
            bundleContext = componentContext.getBundleContext();
            httpService.registerServlet(HONEYWELL_ALIAS, createServlet(), new Hashtable<>(),
                    httpService.createDefaultHttpContext());
            httpService.registerResources(HONEYWELL_ALIAS + HONEYWELL_IMG_ALIAS, "web", null);
        } catch (NamespaceException | ServletException | IOException e) {
            logger.warn("Error during honeywell servlet startup", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext componentContext) {
        httpService.unregister(HONEYWELL_ALIAS);
        httpService.unregister(HONEYWELL_ALIAS + HONEYWELL_IMG_ALIAS);
    }

    /**
     * Creates a new {@link HoneywellAuthServlet}.
     *
     * @return the newly created servlet
     * @throws IOException thrown when an HTML template could not be read
     */
    private HttpServlet createServlet() throws IOException {
        return new HoneywellAuthServlet(this, readTemplate(TEMPLATE_INDEX), readTemplate(TEMPLATE_BRIDGE));
    }

    /**
     * Reads a template from file and returns the content as String.
     *
     * @param templateName name of the template file to read
     * @return The content of the template file
     * @throws IOException thrown when an HTML template could not be read
     */
    private String readTemplate(String templateName) throws IOException {
        final URL index = bundleContext.getBundle().getEntry(templateName);

        if (index == null) {
            throw new FileNotFoundException(
                    String.format("Cannot find '%s' - failed to initialize Honeywell servlet", templateName));
        } else {
            try (InputStream inputStream = index.openStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * Call with Honeywell redirect uri returned State and Code values to get the refresh and access tokens and persist
     * these values
     *
     * @param servletBaseURL the servlet base, which will be the Honeywell redirect url
     * @param thingUID The Honeywell returned state value
     * @param code The Honeywell returned code value
     * @throws Exception
     */
    @SuppressWarnings("null")
    public void authorize(String servletBaseURL, String thingUID, String code) throws IllegalArgumentException {
        /**
         * Get the {@link HoneywellOauth20Handler} that matches the given thing UID.
         *
         * @param thingUID UID of the thing to match the handler with
         * @return the {@link HoneywellOauth20Handler} matching the thing UID or null
         */
        final Optional<HoneywellOauth20Handler> maybeListener = getHoneywellAccountHandlers().stream()
                .filter(l -> l.getThing().getUID().getAsString().equals(thingUID)).findFirst();

        if (maybeListener.isPresent()) {
            maybeListener.get().authorize(servletBaseURL, code);
        } else {
            logger.debug(
                    "Honeywell redirected with state '{}' but no matching bridge was found. Possible bridge has been removed.",
                    thingUID);
            throw new IllegalArgumentException(ERROR_UKNOWN_BRIDGE);
        }
    }

    /**
     * @param listener Adds the given handler
     */
    public void addHoneywellAccountHandler(HoneywellOauth20Handler listener) {
        handlers.put(listener.getThing().getUID().getAsString(), listener);
    }

    /**
     * @param handler Removes the given handler
     */
    public void removeHoneywellAccountHandler(String listner) {

        handlers.remove(listner);
    }

    /**
     * @return Returns all {@link HoneywellAccountHandler}s.
     */
    public Collection<HoneywellOauth20Handler> getHoneywellAccountHandlers() {
        return handlers.values();
    }

    @Reference
    protected void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    protected void unsetHttpService(HttpService httpService) {
        this.httpService = null;
    }
}
