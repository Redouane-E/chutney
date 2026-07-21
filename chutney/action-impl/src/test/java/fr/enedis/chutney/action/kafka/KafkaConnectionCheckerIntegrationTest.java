/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.tools.SocketUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

@DisplayName("KafkaConnectionChecker")
class KafkaConnectionCheckerIntegrationTest {

    private static final EmbeddedKafkaBroker embeddedKafkaBroker = new EmbeddedKafkaKraftBroker(1, 1);

    private static String brokerPath;

    private final KafkaConnectionChecker checker = new KafkaConnectionChecker();

    @BeforeAll
    static void start_broker() {
        embeddedKafkaBroker.afterPropertiesSet();
        brokerPath = embeddedKafkaBroker.getBrokersAsString();
    }

    @AfterAll
    static void stop_broker() {
        embeddedKafkaBroker.destroy();
    }

    @Nested
    @DisplayName("canHandle")
    class CanHandle {

        @Test
        void should_handle_a_target_declaring_bootstrap_servers() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("kafka")
                .withUrl("tcp://localhost:9092")
                .withProperty("bootstrap.servers", "localhost:9092")
                .build();
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_handle_a_kafka_scheme() {
            assertThat(checker.canHandle(url("kafka://localhost:9092"))).isTrue();
        }

        @Test
        void should_handle_a_tcp_target_tagged_with_protocol_kafka() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("kafka")
                .withUrl("tcp://localhost:9092")
                .withProperty("protocol", "kafka")
                .build();
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_not_handle_a_plain_tcp_target_without_bootstrap_servers() {
            assertThat(checker.canHandle(url("tcp://localhost:9092"))).isFalse();
            assertThat(checker.canHandle(url("http://localhost"))).isFalse();
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        void should_succeed_when_the_broker_is_reachable() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("kafka")
                .withUrl("tcp://" + brokerPath)
                .withProperty("bootstrap.servers", brokerPath)
                .build();

            Throwable thrown = catchThrowable(() -> checker.check(target, 5000));

            assertThat(thrown).isNull();
        }

        @Test
        void should_fail_when_the_broker_is_unreachable() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("kafka")
                .withUrl("tcp://localhost:" + SocketUtils.findAvailableTcpPort())
                .withProperty("bootstrap.servers", "localhost:" + SocketUtils.findAvailableTcpPort())
                .build();

            assertThatThrownBy(() -> checker.check(target, 2000)).isInstanceOf(Exception.class);
        }
    }

    private static Target url(String url) {
        return TestTarget.TestTargetBuilder.builder().withTargetId("kafka").withUrl(url).build();
    }
}
