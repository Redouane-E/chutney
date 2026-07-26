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
        void should_handle_the_documented_tcp_target_with_a_jdbcUrl_property() {
            // Chutney documents sql targets as tcp://host:port with the real url in a jdbcUrl property
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("sql")
                .withUrl("tcp://myoracle.db.server:1531/")
                .withProperty("jdbcUrl", "jdbc:oracle:thin:@myoracle.db.server:1531/svc")
                .build();
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_handle_a_target_explicitly_tagged_as_jdbc() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("sql").withUrl("tcp://db:5432").withProperty("protocol", "jdbc").build();
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_not_handle_a_plain_tcp_or_other_target() {
            assertThat(checker.canHandle(target("http://localhost", null))).isFalse();
            assertThat(checker.canHandle(target("tcp://localhost:9092", null))).isFalse();
        }

        @Test
        void should_yield_to_an_explicit_other_protocol() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("sql").withUrl("jdbc:h2:mem:x").withProperty("protocol", "kafka").build();
            assertThat(checker.canHandle(target)).isFalse();
        }

        @Test
        void should_handle_a_natural_database_protocol_name() {
            // a team naming the protocol postgresql/mysql/oracle must not make the target LESS testable
            for (String name : new String[]{"postgresql", "postgres", "mysql", "oracle", "sqlserver"}) {
                Target target = TestTarget.TestTargetBuilder.builder()
                    .withTargetId("sql").withUrl("tcp://db:5432")
                    .withProperty("jdbcUrl", "jdbc:x").withProperty("protocol", name).build();
                assertThat(checker.canHandle(target)).as("protocol=%s", name).isTrue();
            }
        }

        @Test
        void should_fall_back_to_the_heuristic_when_the_declared_protocol_is_unknown() {
            // an unrecognised protocol value (typo, exotic name) must not silence detection
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("sql").withUrl("tcp://db:5432")
                .withProperty("jdbcUrl", "jdbc:x").withProperty("protocol", "somethingelse").build();
            assertThat(checker.canHandle(target)).isTrue();
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
