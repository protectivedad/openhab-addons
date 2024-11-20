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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.UrlEncoded;
import org.openhab.binding.honeywell.internal.HoneywellOauth20Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HoneywellAuthServlet} manages the authorization with the Honeywell Web API. The servlet implements the
 * Authorization Code flow and saves the resulting refreshToken with the bridge.
 *
 * @author Andreas Stenlund - Initial contribution
 * @author Matthew Bowman - Initial contribution
 * @author Hilbrand Bouwkamp - Rewrite, moved service part to service class. Uses templates, simplified calls.
 * @author Anthony Sepa - Repurposed for Honeywell removed interface bloat
 */
@NonNullByDefault
public class HoneywellAuthServlet extends HttpServlet {

    private static final long serialVersionUID = -4719613645562518231L;

    private static final String CONTENT_TYPE = "text/html;charset=UTF-8";

    // Simple HTML templates for inserting messages.
    private static final String HTML_EMPTY_BRIDGES = "<p class='block'>Manually add a Honeywell Bridge to authorize it here.<p>";
    private static final String HTML_ERROR = "<p class='block error'>Call to Honeywell failed with error: %s</p>";

    private static final Pattern MESSAGE_KEY_PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");

    // Keys present in the index.html
    private static final String KEY_PAGE_REFRESH = "pageRefresh";
    private static final String HTML_META_REFRESH_CONTENT = "<meta http-equiv='refresh' content='10; url=%s'>";
    private static final String KEY_ERROR = "error";
    private static final String KEY_BRIDGES = "bridges";
    private static final String KEY_REDIRECT_URI = "redirectUri";
    // Keys present in the bridge.html
    private static final String BRIDGE_ID = "bridge.id";
    private static final String BRIDGE_NAME = "bridge.name";
    private static final String BRIDGE_HONEYWELL_USER_ID = "bridge.user";
    private static final String BRIDGE_AUTHORIZED_CLASS = "bridge.authorized";
    private static final String BRIDGE_AUTHORIZE = "bridge.authorize";

    private final Logger logger = LoggerFactory.getLogger(HoneywellAuthServlet.class);
    private final HoneywellAuthService honeywellAuthService;
    private final String indexTemplate;
    private final String bridgeTemplate;

    public HoneywellAuthServlet(HoneywellAuthService honeywellAuthService, String indexTemplate,
            String bridgeTemplate) {
        this.honeywellAuthService = honeywellAuthService;
        this.indexTemplate = indexTemplate;
        this.bridgeTemplate = bridgeTemplate;
    }

    @Override
    protected void doGet(@Nullable HttpServletRequest req, @Nullable HttpServletResponse resp)
            throws ServletException, IOException {
        final String servletBaseURL;
        final @Nullable String queryString;
        if (null != req) {
            logger.debug("Honeywell auth callback servlet received GET request {}.", req.getRequestURI());
            final StringBuffer stringBuffer = req.getRequestURL();
            servletBaseURL = (null == stringBuffer) ? "" : stringBuffer.toString();
            queryString = req.getQueryString();
        } else {
            servletBaseURL = "";
            queryString = null;
        }
        final Map<String, String> replaceMap = new HashMap<>();

        handleHoneywellRedirect(replaceMap, servletBaseURL, queryString);
        replaceMap.put(KEY_REDIRECT_URI, servletBaseURL);
        replaceMap.put(KEY_BRIDGES, formatBridges(bridgeTemplate, servletBaseURL));
        if (null != resp) {
            resp.setContentType(CONTENT_TYPE);
            resp.getWriter().append(replaceKeysFromMap(indexTemplate, replaceMap));
            resp.getWriter().close();
        }
    }

    /**
     * Handles a possible call from Honeywell to the redirect_uri. If that is the case Honeywell will pass the
     * authorization codes via the url and these are processed. In case of an error this is shown to the user. If the
     * user was authorized this is passed on to the handler. Based on all these different outcomes the HTML is generated
     * to inform the user.
     *
     * @param replaceMap a map with key String values that will be mapped in the HTML templates.
     * @param servletBaseURL the servlet base, which should be used as the Honeywell redirect_uri value
     * @param queryString the query part of the GET request this servlet is processing
     */
    private void handleHoneywellRedirect(Map<String, String> replaceMap, String servletBaseURL,
            @Nullable String queryString) {
        replaceMap.put(KEY_ERROR, "");
        replaceMap.put(KEY_PAGE_REFRESH, "");

        if (queryString != null) {
            final MultiMap<@Nullable String> params = new MultiMap<>();
            UrlEncoded.decodeTo(queryString, params, StandardCharsets.UTF_8.name());
            final String reqCode = params.getString("code");
            final String reqState = params.getString("state");
            final String reqError = params.getString("error");

            replaceMap.put(KEY_PAGE_REFRESH,
                    params.isEmpty() ? "" : String.format(HTML_META_REFRESH_CONTENT, servletBaseURL));
            if (!StringUtil.isBlank(reqError)) {
                logger.debug("Honeywell redirected with an error: {}", reqError);
                replaceMap.put(KEY_ERROR, String.format(HTML_ERROR, reqError));
            } else if (!StringUtil.isBlank(reqState)) {
                try {
                    honeywellAuthService.authorize(servletBaseURL, reqState, reqCode);
                } catch (Exception e) {
                    logger.debug("Exception during authorizaton: ", e);
                    replaceMap.put(KEY_ERROR, String.format(HTML_ERROR, e.getMessage()));
                }
            }
        }
    }

    /**
     * Formats the HTML of all available Honewell Bridges and returns it as a String
     *
     * @param bridgeTemplate The bridge template to format the bridge values in
     * @param servletBaseURL the redirect_uri to be used in the authorization url created on the authorization button.
     * @return A String with the bridges formatted with the bridge template
     */
    private String formatBridges(String bridgeTemplate, String servletBaseURL) {
        final List<HoneywellOauth20Handler> bridges = honeywellAuthService.getHoneywellAccountHandlers();

        return bridges.isEmpty() ? HTML_EMPTY_BRIDGES
                : bridges.stream().map(p -> formatBridge(bridgeTemplate, p, servletBaseURL))
                        .collect(Collectors.joining());
    }

    /**
     * Formats the HTML of a Honeywell Bridge and returns it as a String
     *
     * @param bridgeTemplate The bridge template to format the bridge values in
     * @param handler The handler for the bridge to format
     * @param servletBaseURL the redirect_uri to be used in the authorization url created on the authorization button.
     * @return A String with the bridge formatted with the bridge template
     */
    private String formatBridge(String bridgeTemplate, HoneywellOauth20Handler handler, String servletBaseURL) {
        final Map<String, String> map = new HashMap<>();

        map.put(BRIDGE_ID, handler.getUID().getAsString());
        map.put(BRIDGE_NAME, handler.getLabel());

        if (handler.isAuthorized()) {
            map.put(BRIDGE_AUTHORIZED_CLASS, " authorized");
            map.put(BRIDGE_HONEYWELL_USER_ID, " (Authorized)");
        } else {
            map.put(BRIDGE_AUTHORIZED_CLASS, " Unauthorized");
            map.put(BRIDGE_HONEYWELL_USER_ID, " (Unauthorized)");
        }

        map.put(BRIDGE_AUTHORIZE, handler.formatAuthorizationUrl(servletBaseURL));
        return replaceKeysFromMap(bridgeTemplate, map);
    }

    /**
     * Replaces all keys from the map found in the template with values from the map. If the key is not found the key
     * will be kept in the template.
     *
     * @param template template to replace keys with values
     * @param map map with key value pairs to replace in the template
     * @return a template with keys replaced
     */
    private String replaceKeysFromMap(String template, Map<String, String> map) {
        final Matcher m = MESSAGE_KEY_PATTERN.matcher(template);
        final StringBuffer sb = new StringBuffer();

        while (m.find()) {
            try {
                final String key = m.group(1);
                m.appendReplacement(sb, Matcher.quoteReplacement(map.getOrDefault(key, "${" + key + '}')));
            } catch (RuntimeException e) {
                logger.debug("Error occurred during template filling, cause ", e);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
