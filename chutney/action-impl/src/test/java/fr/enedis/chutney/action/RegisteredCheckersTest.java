/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action;

import static org.assertj.core.api.Assertions.assertThat;

import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.tools.loader.ExtensionLoaders;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the connectivity-probe wiring: every checker listed in {@code chutney.checkers} must load and
 * instantiate. The server loader silently skips a checker that fails to construct, so without this a
 * missing dependency would quietly leave a protocol family unprobeable in production.
 */
@DisplayName("Registered connection checkers")
class RegisteredCheckersTest {

    @Test
    void should_load_and_instantiate_every_registered_checker() {
        List<Class<?>> classes = ExtensionLoaders
            .classpathToClass("META-INF/extension/chutney.checkers")
            .load()
            .stream()
            .toList();

        assertThat(classes)
            .as("checkers registered in chutney.checkers")
            .hasSize(10)
            .allSatisfy(clazz -> assertThat(TargetConnectionChecker.class).isAssignableFrom(clazz));

        assertThat(classes).allSatisfy(clazz -> {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            assertThat(instance).isInstanceOf(TargetConnectionChecker.class);
        });
    }
}
