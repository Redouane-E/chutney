/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.sql.check;

import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import java.sql.Connection;
import java.util.Properties;

/**
 * Probes a {@code jdbc:} target by opening a single pooled connection and validating it.
 * <p>
 * It reads the jdbcUrl / credentials / {@code dataSource.*} options exactly like
 * {@link fr.enedis.chutney.action.sql.core.DefaultSqlClientFactory}, but bounds the pool to a short
 * timeout and fails fast so an unreachable database does not block for Hikari's 30s default.
 */
public class SqlConnectionChecker implements TargetConnectionChecker {

    private static final int MIN_HIKARI_TIMEOUT_MS = 250;

    @Override
    public boolean canHandle(Target target) {
        String uri = target.rawUri();
        return uri != null && startsWithIgnoreCase(uri, "jdbc:");
    }

    @Override
    public void check(Target target, int timeoutMs) throws Exception {
        long boundedTimeout = Math.max(timeoutMs, MIN_HIKARI_TIMEOUT_MS);

        Properties props = new Properties();
        props.put("jdbcUrl", target.property("jdbcUrl").orElse(target.uri().toString()));
        target.user().ifPresent(user -> props.put("username", user));
        target.userPassword().ifPresent(password -> props.put("password", password));
        props.putAll(target.prefixedProperties("dataSource."));
        props.put("maximumPoolSize", "1");
        props.put("connectionTimeout", String.valueOf(boundedTimeout));
        props.put("validationTimeout", String.valueOf(boundedTimeout));
        props.put("initializationFailTimeout", "1");

        try (HikariDataSource dataSource = new HikariDataSource(new HikariConfig(props));
             Connection connection = dataSource.getConnection()) {
            if (!connection.isValid((int) Math.max(1, boundedTimeout / 1000))) {
                throw new IllegalStateException("JDBC connection opened but reported as not valid");
            }
        }
    }
}
