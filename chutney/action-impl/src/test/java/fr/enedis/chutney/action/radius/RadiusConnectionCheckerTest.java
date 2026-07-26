/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.radius;

import static fr.enedis.chutney.tools.SocketUtils.findAvailableTcpPort;
import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.injectable.Target;
import java.net.InetSocketAddress;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tinyradius.util.RadiusServer;

@DisplayName("RadiusConnectionChecker")
class RadiusConnectionCheckerTest {

    private static RadiusServer server;
    private static int authPort;

    private final RadiusConnectionChecker checker = new RadiusConnectionChecker();

    @BeforeAll
    static void start_server() {
        server = new RadiusServer() {
            @Override
            public String getSharedSecret(InetSocketAddress client) {
                return "secret";
            }

            @Override
            public String getUserPassword(String userName) {
                return "password";
            }
        };
        Integer[] range = randomRange();
        authPort = findAvailableTcpPort(range[0], range[1]);
        range = randomRange();
        server.setAuthPort(authPort);
        server.setAcctPort(findAvailableTcpPort(range[0], range[1]));
        server.start(true, false);
    }

    @AfterAll
    static void stop_server() {
        server.stop();
    }

    @Nested
    @DisplayName("canHandle")
    class CanHandle {

        @Test
        void should_handle_a_target_with_a_shared_secret() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("radius").withUrl("tcp://host:1812").withProperty("sharedSecret", "s").build();
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_handle_a_udp_url() {
            Target target = TestTarget.TestTargetBuilder.builder().withTargetId("radius").withUrl("udp://host:1812").build();
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_not_handle_a_plain_target() {
            Target target = TestTarget.TestTargetBuilder.builder().withTargetId("x").withUrl("tcp://host:1812").build();
            assertThat(checker.canHandle(target)).isFalse();
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        void should_succeed_when_the_server_answers_with_the_right_shared_secret() {
            Throwable thrown = catchThrowable(() -> checker.check(radiusTarget("secret", authPort), 3000));

            assertThat(thrown).isNull();
        }

        @Test
        void should_fail_when_the_server_is_unreachable() {
            Target target = radiusTarget("secret", findAvailableTcpPort());

            assertThatThrownBy(() -> checker.check(target, 1500)).isInstanceOf(Exception.class);
        }
    }

    private static Target radiusTarget(String sharedSecret, int port) {
        return TestTarget.TestTargetBuilder.builder()
            .withTargetId("radius")
            .withUrl("udp://localhost:1812")
            .withProperty("sharedSecret", sharedSecret)
            .withProperty("authenticatePort", valueOf(port))
            .withProperty("accountingPort", valueOf(port))
            .build();
    }

    private static Integer[] randomRange() {
        int start = 1024 + new Random().nextInt(60000 - 1024 - 100);
        return new Integer[]{start, start + 100};
    }
}
