/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.mongo;

import com.mongodb.client.MongoDatabase;
import fr.enedis.chutney.action.common.TargetProtocols;
import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.tools.CloseableResource;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Probes a {@code mongodb} target, reusing {@link DefaultMongoDatabaseFactory} to build the client
 * from the target (host/port, credentials, {@code connectionOptions.*}, TLS). The Mongo driver
 * connects lazily, so the checker forces an authenticated round-trip with
 * {@code listCollectionNames()} — a wrong host raises a timeout and bad credentials raise a security
 * error, both reported as DOWN. The server-selection / connect timeouts are bounded to the probe
 * timeout so an unreachable server fails fast (a missing {@code databaseName} property surfaces the
 * factory's clear error).
 */
public class MongoConnectionChecker implements TargetConnectionChecker {

    private static final Set<String> ALIASES = Set.of("mongo", "mongodb");
    private static final String OPTION_PREFIX = "connectionOptions.";

    /**
     * A mongo target is recognised by the {@code databaseName} property the mongo actions require (or
     * a mongo url), not solely by scheme — the docs show {@code mongo://} while the driver builds a
     * {@code mongodb://} string from host and port.
     */
    @Override
    public boolean canHandle(Target target) {
        return TargetProtocols.matches(target, ALIASES,
            () -> TargetProtocols.hasProperty(target, "databaseName")
                || TargetProtocols.uriStartsWith(target, "mongodb://", "mongodb+srv://", "mongo://"));
    }

    @Override
    public void check(Target target, int timeoutMs) throws Exception {
        try (CloseableResource<MongoDatabase> resource = new DefaultMongoDatabaseFactory().create(boundedTarget(target, timeoutMs))) {
            resource.getResource().listCollectionNames().first();
        }
    }

    private static Target boundedTarget(Target target, int timeoutMs) {
        Map<String, String> extraOptions = new LinkedHashMap<>();
        putIfAbsent(target, extraOptions, OPTION_PREFIX + "serverSelectionTimeoutMS", timeoutMs);
        putIfAbsent(target, extraOptions, OPTION_PREFIX + "connectTimeoutMS", timeoutMs);
        return extraOptions.isEmpty() ? target : new TimeoutBoundedTarget(target, extraOptions);
    }

    private static void putIfAbsent(Target target, Map<String, String> options, String key, int value) {
        if (target.property(key).isEmpty()) {
            options.put(key, String.valueOf(value));
        }
    }

    /**
     * Wraps a {@link Target} to add default {@code connectionOptions.*} without mutating the original;
     * the factory reads these back through {@code property} / {@code prefixedProperties}.
     */
    private static final class TimeoutBoundedTarget implements Target {

        private final Target delegate;
        private final Map<String, String> extraProperties;

        private TimeoutBoundedTarget(Target delegate, Map<String, String> extraProperties) {
            this.delegate = delegate;
            this.extraProperties = extraProperties;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public URI uri() {
            return delegate.uri();
        }

        @Override
        public String rawUri() {
            return delegate.rawUri();
        }

        @Override
        public Optional<String> property(String key) {
            return delegate.property(key).or(() -> Optional.ofNullable(extraProperties.get(key)));
        }

        @Override
        public Map<String, String> prefixedProperties(String prefix, boolean cutPrefix) {
            Map<String, String> merged = new LinkedHashMap<>(delegate.prefixedProperties(prefix, cutPrefix));
            extraProperties.forEach((key, value) -> {
                if (key.startsWith(prefix)) {
                    merged.put(cutPrefix ? key.substring(prefix.length()) : key, value);
                }
            });
            return merged;
        }
    }
}
