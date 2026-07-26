/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.common;

import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

import fr.enedis.chutney.action.spi.injectable.Target;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Decides which protocol a target speaks, for connectivity checks.
 * <p>
 * A target's url scheme is not a reliable indicator: Chutney documents the protocol-agnostic
 * {@code tcp://} form for sql, ssh, kafka and jms alike, and teams configure targets as they see fit.
 * Detection therefore rests on two firmer signals — an explicit {@code protocol} property when the
 * team states it, otherwise the properties an action of that family actually requires (a jdbc target
 * carries {@code jdbcUrl}, a mongo one {@code databaseName}, a radius one {@code sharedSecret}…).
 * <p>
 * A declared protocol is authoritative: it selects exactly one family and silences every heuristic,
 * so a target can never be probed as something its owner did not intend.
 */
public final class TargetProtocols {

    public static final String PROTOCOL_PROPERTY = "protocol";

    /**
     * Every protocol name any checker answers to. A declared {@code protocol} is only treated as
     * authoritative when it is one of these — otherwise (a value like {@code postgresql}, or a typo)
     * it is ignored and detection falls back to the heuristic, so declaring a protocol never makes a
     * target <em>less</em> testable than saying nothing. Must stay in sync with each checker's aliases.
     */
    private static final Set<String> KNOWN_PROTOCOLS = Set.of(
        "http", "https",
        "jdbc", "sql", "database", "db",
        "postgresql", "postgres", "mysql", "mariadb", "oracle", "sqlserver", "mssql", "db2", "h2", "sqlite",
        "ssh", "sftp", "scp",
        "amqp", "amqps", "rabbit", "rabbitmq",
        "kafka", "kafkas",
        "mongo", "mongodb",
        "jms", "activemq",
        "jakarta", "artemis",
        "radius", "udp",
        "tcp", "tcps");

    private TargetProtocols() {
    }

    /**
     * @param aliases   names the calling checker answers to, lower case
     * @param otherwise how to recognise the target when no usable protocol is declared
     */
    public static boolean matches(Target target, Set<String> aliases, BooleanSupplier otherwise) {
        Optional<String> declared = declaredProtocol(target).filter(KNOWN_PROTOCOLS::contains);
        if (declared.isPresent()) {
            return aliases.contains(declared.get());
        }
        return otherwise.getAsBoolean();
    }

    public static Optional<String> declaredProtocol(Target target) {
        return target.property(PROTOCOL_PROPERTY)
            .map(String::trim)
            .filter(protocol -> !protocol.isEmpty())
            .map(protocol -> protocol.toLowerCase(Locale.ROOT));
    }

    /** True when the target carries a non-blank value for that property. */
    public static boolean hasProperty(Target target, String key) {
        return target.property(key).filter(value -> !value.isBlank()).isPresent();
    }

    public static boolean uriStartsWith(Target target, String... prefixes) {
        String uri = target.rawUri();
        if (uri == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (startsWithIgnoreCase(uri, prefix)) {
                return true;
            }
        }
        return false;
    }
}
