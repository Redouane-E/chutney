/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.common;

import fr.enedis.chutney.action.spi.injectable.Target;
import java.util.Map;
import java.util.Optional;

/**
 * Copies a target's TLS material into the JNDI environment of a JMS / Jakarta connection factory,
 * exactly as the JMS and Jakarta actions do in their {@code configureSsl} step. A connectivity probe
 * must build the connection the same way the action does, so an SSL broker requiring a client
 * certificate or a private CA is reached — and not wrongly reported unreachable — during the probe.
 * <p>
 * The keys mirror the factories verbatim ({@code connection.ConnectionFactory.*}); only properties the
 * target actually declares are added, so a plain-text broker is left untouched.
 */
public final class JmsSslProperties {

    private JmsSslProperties() {
    }

    public static void putInto(Map<String, String> environment, Target target) {
        putIfPresent(environment, "connection.ConnectionFactory.keyStore", target.keyStore());
        putIfPresent(environment, "connection.ConnectionFactory.keyStorePassword", target.keyStorePassword());
        putIfPresent(environment, "connection.ConnectionFactory.keyStoreKeyPassword", target.keyPassword());
        putIfPresent(environment, "connection.ConnectionFactory.trustStore", target.trustStore());
        putIfPresent(environment, "connection.ConnectionFactory.trustStorePassword", target.trustStorePassword());
    }

    private static void putIfPresent(Map<String, String> environment, String key, Optional<String> value) {
        value.ifPresent(v -> environment.put(key, v));
    }
}
