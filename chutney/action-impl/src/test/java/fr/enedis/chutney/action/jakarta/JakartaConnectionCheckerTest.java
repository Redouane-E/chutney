/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.injectable.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JakartaConnectionChecker")
class JakartaConnectionCheckerTest {

    private final JakartaConnectionChecker checker = new JakartaConnectionChecker();

    @Nested
    @DisplayName("canHandle")
    class CanHandle {

        @Test
        void should_handle_a_target_with_an_artemis_context_factory() {
            Target target = jakartaTarget("org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_handle_a_target_explicitly_tagged_as_jakarta() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("jakarta").withUrl("tcp://broker:61616").withProperty("protocol", "jakarta").build();
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_not_claim_an_activemq_classic_target() {
            Target target = jakartaTarget("org.apache.activemq.jndi.ActiveMQInitialContextFactory");
            assertThat(checker.canHandle(target)).isFalse();
        }
    }

    private Target jakartaTarget(String initialContextFactory) {
        return TestTarget.TestTargetBuilder.builder()
            .withTargetId("jakarta")
            .withUrl("tcp://localhost:61616")
            .withProperty("java.naming.factory.initial", initialContextFactory)
            .build();
    }
}
