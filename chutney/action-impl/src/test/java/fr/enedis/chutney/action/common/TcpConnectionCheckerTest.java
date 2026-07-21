/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.tools.SocketUtils;
import java.net.ServerSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TcpConnectionChecker")
class TcpConnectionCheckerTest {

    private final TcpConnectionChecker checker = new TcpConnectionChecker();

    @Nested
    @DisplayName("canHandle")
    class CanHandle {

        @Test
        void should_handle_tcp_and_tcps_schemes() {
            assertThat(checker.canHandle(url("tcp://localhost:9092"))).isTrue();
            assertThat(checker.canHandle(url("tcps://localhost:9093"))).isTrue();
        }

        @Test
        void should_not_handle_schemes_owned_by_a_dedicated_checker() {
            assertThat(checker.canHandle(url("http://localhost"))).isFalse();
            assertThat(checker.canHandle(url("jdbc:h2:mem:test"))).isFalse();
            assertThat(checker.canHandle(url("mongodb://localhost"))).isFalse();
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        void should_succeed_when_the_port_is_open() throws Exception {
            try (ServerSocket server = new ServerSocket(0)) {
                Target target = url("tcp://localhost:" + server.getLocalPort());

                Throwable thrown = catchThrowable(() -> checker.check(target, 2000));

                assertThat(thrown).isNull();
            }
        }

        @Test
        void should_fail_when_nothing_is_listening() {
            Target target = url("tcp://localhost:" + SocketUtils.findAvailableTcpPort());

            assertThatThrownBy(() -> checker.check(target, 2000)).isInstanceOf(Exception.class);
        }

        @Test
        void should_fail_when_no_port_is_defined() {
            Target target = url("tcp://localhost");

            assertThatThrownBy(() -> checker.check(target, 2000)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static Target url(String url) {
        return TestTarget.TestTargetBuilder.builder().withTargetId("tcp").withUrl(url).build();
    }
}
