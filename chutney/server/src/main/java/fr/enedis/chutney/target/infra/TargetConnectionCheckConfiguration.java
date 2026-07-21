/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.infra;

import static fr.enedis.chutney.config.ServerConfigurationValues.TARGET_CONNECTION_CHECK_TIMEOUT_SPRING_VALUE;

import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.environment.api.target.EmbeddedTargetApi;
import fr.enedis.chutney.target.domain.TargetConnectionCheckService;
import fr.enedis.chutney.tools.ThrowingFunction;
import fr.enedis.chutney.tools.loader.ExtensionLoaders;
import java.util.List;
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
        @Value(TARGET_CONNECTION_CHECK_TIMEOUT_SPRING_VALUE) int timeoutMs
    ) {
        return new TargetConnectionCheckService(targetApi, loadCheckers(), timeoutMs);
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
            .map(ThrowingFunction.toUnchecked(clazz -> (TargetConnectionChecker) clazz.getDeclaredConstructor().newInstance()))
            .collect(Collectors.toList());
        LOGGER.debug("Loaded {} target connection checker(s)", checkers.size());
        return checkers;
    }
}
