/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.infra;

import static fr.enedis.chutney.config.ServerConfigurationValues.TARGET_CONNECTION_CHECK_POOL_SIZE_SPRING_VALUE;
import static fr.enedis.chutney.config.ServerConfigurationValues.TARGET_CONNECTION_CHECK_THROTTLE_SPRING_VALUE;
import static fr.enedis.chutney.config.ServerConfigurationValues.TARGET_CONNECTION_CHECK_TIMEOUT_SPRING_VALUE;
import static fr.enedis.chutney.config.ServerConfigurationValues.TARGET_CONNECTION_STATUS_TTL_UNIT_SPRING_VALUE;
import static fr.enedis.chutney.config.ServerConfigurationValues.TARGET_CONNECTION_STATUS_TTL_VALUE_SPRING_VALUE;

import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.environment.api.target.EmbeddedTargetApi;
import fr.enedis.chutney.target.domain.TargetConnectionCheckService;
import fr.enedis.chutney.target.domain.TargetConnectionStatusEnvironmentUpdateHandler;
import fr.enedis.chutney.target.domain.TargetConnectionStatusRepository;
import fr.enedis.chutney.target.domain.TargetConnectionStatusUpdateHandler;
import fr.enedis.chutney.tools.loader.ExtensionLoaders;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TargetConnectionCheckConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(TargetConnectionCheckConfiguration.class);

    private static final String CHECKERS_EXTENSION_PATH = "META-INF/extension/chutney.checkers";

    @Bean
    TargetConnectionCheckService targetConnectionCheckService(
        EmbeddedTargetApi targetApi,
        TargetConnectionStatusRepository statusRepository,
        @Value(TARGET_CONNECTION_CHECK_TIMEOUT_SPRING_VALUE) int timeoutMs,
        @Value(TARGET_CONNECTION_CHECK_THROTTLE_SPRING_VALUE) long throttleMs,
        @Value(TARGET_CONNECTION_STATUS_TTL_VALUE_SPRING_VALUE) int statusTtlValue,
        @Value(TARGET_CONNECTION_STATUS_TTL_UNIT_SPRING_VALUE) String statusTtlUnit,
        @Value(TARGET_CONNECTION_CHECK_POOL_SIZE_SPRING_VALUE) int poolSize
    ) {
        long statusTtlMs = TimeUnit.valueOf(statusTtlUnit).toMillis(statusTtlValue);
        return new TargetConnectionCheckService(targetApi, statusRepository, loadCheckers(), timeoutMs, throttleMs, statusTtlMs, poolSize);
    }

    /**
     * Forgets a target's status when the target itself changes, so a verdict never outlives the
     * configuration it was obtained with.
     */
    @Bean
    TargetConnectionStatusUpdateHandler targetConnectionStatusUpdateHandler(TargetConnectionStatusRepository statusRepository) {
        return new TargetConnectionStatusUpdateHandler(statusRepository);
    }

    @Bean
    TargetConnectionStatusEnvironmentUpdateHandler targetConnectionStatusEnvironmentUpdateHandler(TargetConnectionStatusRepository statusRepository) {
        return new TargetConnectionStatusEnvironmentUpdateHandler(statusRepository);
    }

    /**
     * Loads the per-protocol checkers off the runtime classpath, mirroring how the engine loads
     * {@code chutney.strategies} — the {@code action-impl} module is a runtime dependency, so its
     * implementations cannot be compile-referenced here.
     */
    private List<TargetConnectionChecker> loadCheckers() {
        List<TargetConnectionChecker> checkers = ExtensionLoaders
            .classpathToClass(CHECKERS_EXTENSION_PATH)
            .load()
            .stream()
            .filter(TargetConnectionChecker.class::isAssignableFrom)
            .map(this::instantiateQuietly)
            .filter(Objects::nonNull)
            // Stable order: several checkers may accept the same target, and which one wins must not
            // depend on the order the classpath happened to be scanned in.
            .sorted(Comparator.comparing((TargetConnectionChecker checker) -> checker.getClass().getName()))
            .collect(Collectors.toList());
        LOGGER.debug("Loaded {} target connection checker(s)", checkers.size());
        return checkers;
    }

    /**
     * A checker that cannot be created — a missing optional driver, for instance — costs its protocol
     * the ability to be probed. It must not keep the server from starting.
     */
    private TargetConnectionChecker instantiateQuietly(Class<?> clazz) {
        try {
            return (TargetConnectionChecker) clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            LOGGER.warn("Ignoring target connection checker {}: {}", clazz.getName(), e.toString());
            return null;
        }
    }
}
