/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.jms;

import static org.assertj.core.api.Assertions.assertThat;

import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.injectable.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JmsConnectionChecker")
class JmsConnectionCheckerTest {

    private final JmsConnectionChecker checker = new JmsConnectionChecker();

    @Nested
    @DisplayName("canHandle")
    class CanHandle {

        @Test
        void should_handle_a_target_with_an_activemq_classic_context_factory() {
            // JMS targets use tcp:// or ssl://; the ActiveMQ-classic JNDI factory is what identifies them
            Target target = jmsTarget("org.apache.activemq.jndi.ActiveMQInitialContextFactory");
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_handle_a_target_explicitly_tagged_as_jms() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("jms").withUrl("ssl://broker:61616").withProperty("protocol", "jms").build();
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_not_claim_an_artemis_target() {
            // Artemis is the jakarta checker's job
            Target target = jmsTarget("org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
            assertThat(checker.canHandle(target)).isFalse();
        }

        @Test
        void should_not_handle_a_plain_tcp_target_without_a_jndi_factory() {
            Target target = TestTarget.TestTargetBuilder.builder().withTargetId("x").withUrl("tcp://host:61616").build();
            assertThat(checker.canHandle(target)).isFalse();
        }
    }

    private Target jmsTarget(String initialContextFactory) {
        return TestTarget.TestTargetBuilder.builder()
            .withTargetId("jms")
            .withUrl("tcp://localhost:61616")
            .withProperty("java.naming.factory.initial", initialContextFactory)
            .build();
    }
}
