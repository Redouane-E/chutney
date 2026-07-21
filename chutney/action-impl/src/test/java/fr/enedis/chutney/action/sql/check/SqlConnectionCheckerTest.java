/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.sql.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.tools.SocketUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SqlConnectionChecker")
class SqlConnectionCheckerTest {

    private final SqlConnectionChecker checker = new SqlConnectionChecker();

    @Nested
    @DisplayName("canHandle")
    class CanHandle {

        @Test
        void should_handle_jdbc_urls() {
            assertThat(checker.canHandle(target("jdbc:h2:mem:test", null))).isTrue();
            assertThat(checker.canHandle(target("JDBC:postgresql://localhost/db", null))).isTrue();
        }

        @Test
        void should_not_handle_other_schemes() {
            assertThat(checker.canHandle(target("http://localhost", null))).isFalse();
            assertThat(checker.canHandle(target("ssh://localhost", null))).isFalse();
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        void should_succeed_when_the_database_is_reachable() {
            // Given
            Target target = target("jdbc:h2:mem:probe_up_" + System.nanoTime(), "sa");

            // When
            Throwable thrown = catchThrowable(() -> checker.check(target, 2000));

            // Then
            assertThat(thrown).isNull();
        }

        @Test
        void should_fail_when_the_database_is_unreachable() {
            // Given: an H2 TCP url pointing at a free port with nothing listening
            Target target = target("jdbc:h2:tcp://localhost:" + SocketUtils.findAvailableTcpPort() + "/mem:unreachable", "sa");

            // When / Then
            assertThatThrownBy(() -> checker.check(target, 1000)).isInstanceOf(Exception.class);
        }
    }

    private static Target target(String url, String user) {
        TestTarget.TestTargetBuilder builder = TestTarget.TestTargetBuilder.builder()
            .withTargetId("sql-target")
            .withUrl(url);
        if (user != null) {
            builder.withProperty("user", user);
        }
        return builder.build();
    }
}
