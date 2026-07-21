/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.enedis.chutney.action.TestFinallyActionRegistry;
import fr.enedis.chutney.action.TestLogger;
import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.ActionExecutionResult;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.tools.SocketUtils;
import org.apache.qpid.server.SystemLauncher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AmqpConnectionChecker")
class AmqpConnectionCheckerTest {

    private static SystemLauncher qpidServer;
    private static int amqpPort;

    private final AmqpConnectionChecker checker = new AmqpConnectionChecker();

    @BeforeAll
    static void start_broker() {
        amqpPort = SocketUtils.findAvailableTcpPort();
        System.setProperty("qpid.amqp_port", String.valueOf(amqpPort));
        ActionExecutionResult result =
            new QpidServerStartAction(new TestLogger(), new TestFinallyActionRegistry(), null).execute();
        assertThat(result.status).isEqualTo(ActionExecutionResult.Status.Success);
        qpidServer = (SystemLauncher) result.outputs.get("qpidLauncher");
    }

    @AfterAll
    static void stop_broker() {
        if (qpidServer != null) {
            qpidServer.shutdown();
        }
        System.clearProperty("qpid.amqp_port");
    }

    @Nested
    @DisplayName("canHandle")
    class CanHandle {

        @Test
        void should_handle_amqp_and_amqps_schemes() {
            assertThat(checker.canHandle(url("amqp://localhost:5672"))).isTrue();
            assertThat(checker.canHandle(url("amqps://localhost:5671"))).isTrue();
        }

        @Test
        void should_not_handle_other_schemes() {
            assertThat(checker.canHandle(url("http://localhost"))).isFalse();
            assertThat(checker.canHandle(url("mongodb://localhost"))).isFalse();
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        void should_succeed_with_valid_credentials() {
            Target target = amqpTarget("guest", "guest");

            Throwable thrown = catchThrowable(() -> checker.check(target, 5000));

            assertThat(thrown).isNull();
        }

        @Test
        void should_fail_with_wrong_password() {
            Target target = amqpTarget("guest", "wrong-password");

            assertThatThrownBy(() -> checker.check(target, 5000)).isInstanceOf(Exception.class);
        }

        @Test
        void should_fail_when_broker_is_unreachable() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("amqp")
                .withUrl("amqp://localhost:" + SocketUtils.findAvailableTcpPort())
                .build();

            assertThatThrownBy(() -> checker.check(target, 2000)).isInstanceOf(Exception.class);
        }
    }

    private static Target amqpTarget(String user, String password) {
        return TestTarget.TestTargetBuilder.builder()
            .withTargetId("amqp")
            .withUrl("amqp://localhost:" + amqpPort)
            .withProperty("user", user)
            .withProperty("password", password)
            .build();
    }

    private static Target url(String url) {
        return TestTarget.TestTargetBuilder.builder().withTargetId("amqp").withUrl(url).build();
    }
}
