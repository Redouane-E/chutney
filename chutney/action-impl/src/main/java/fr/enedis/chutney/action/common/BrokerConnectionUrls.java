/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.common;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Adds fail-fast timeout options to a JMS broker url for a connectivity probe, so a probe of an
 * unreachable broker returns instead of parking a worker thread for ever.
 * <p>
 * A connectivity check must never rely on the broker's own defaults: an ActiveMQ classic
 * {@code failover:} url reconnects indefinitely, and both stacks default their transport
 * connect / call timeouts to tens of seconds. The driver's blocking connect ignores thread
 * interruption, so the probe's outer deadline can report the verdict on time yet cannot free the
 * worker thread — the bound therefore has to be pushed down into the transport url the driver honours.
 * <p>
 * Any option the target already declares is left untouched: a team that set its own value meant it.
 */
public final class BrokerConnectionUrls {

    private BrokerConnectionUrls() {
    }

    /**
     * Bounds an ActiveMQ <em>classic</em> broker url. Plain transports ({@code tcp://}, {@code ssl://},
     * {@code nio://}) get a {@code connectionTimeout} and {@code soTimeout}; a {@code failover:(...)}
     * composite is capped to a single connect attempt so it can never retry for ever. A {@code vm://}
     * target runs in-process and is left as it is.
     */
    public static String boundedActiveMqClassic(String url, int timeoutMs) {
        if (url == null) {
            return null;
        }
        String lower = url.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("vm:")) {
            return url;
        }
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        if (lower.startsWith("failover:")) {
            params.put("startupMaxReconnectAttempts", "1");
            params.put("maxReconnectAttempts", "0");
            params.put("timeout", String.valueOf(timeoutMs));
        } else {
            params.put("connectionTimeout", String.valueOf(timeoutMs));
            params.put("soTimeout", String.valueOf(timeoutMs));
        }
        return appendMissingParams(url, params);
    }

    /**
     * Bounds an ActiveMQ <em>Artemis</em> (jakarta) broker url. {@code connect-timeout-millis} is a
     * netty connector key (default {@code -1}, i.e. netty's ~30s), passed straight through the client
     * uri to the transport, so it caps the TCP connect to an unreachable host; {@code callTimeout}
     * caps the blocking calls the rest of connection establishment makes. Artemis already limits
     * itself to a single initial connect attempt, so it cannot loop the way classic failover can. A
     * {@code vm://} target is left as it is.
     */
    public static String boundedArtemis(String url, int timeoutMs) {
        if (url == null) {
            return null;
        }
        if (url.trim().toLowerCase(Locale.ROOT).startsWith("vm:")) {
            return url;
        }
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("connect-timeout-millis", String.valueOf(timeoutMs));
        params.put("callTimeout", String.valueOf(timeoutMs));
        return appendMissingParams(url, params);
    }

    /**
     * Appends each option not already present to the url's own query string. For a
     * {@code failover:(...)} composite the query sits after the closing parenthesis, so options land
     * there rather than inside an inner transport.
     */
    private static String appendMissingParams(String url, Map<String, String> params) {
        int compositeEnd = url.lastIndexOf(')');
        int queryStart = url.indexOf('?', compositeEnd + 1);
        String existingQuery = queryStart < 0 ? "" : url.substring(queryStart + 1);

        StringBuilder result = new StringBuilder(url);
        boolean hasQuery = queryStart >= 0;
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (hasParam(existingQuery, param.getKey())) {
                continue;
            }
            result.append(hasQuery ? '&' : '?').append(param.getKey()).append('=').append(param.getValue());
            hasQuery = true;
        }
        return result.toString();
    }

    /** True when the query already carries that key, matched on a {@code &}-boundary so no suffix collides. */
    private static boolean hasParam(String query, String key) {
        return ("&" + query).toLowerCase(Locale.ROOT).contains("&" + key.toLowerCase(Locale.ROOT) + "=");
    }
}
