/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BrokerConnectionUrls")
class BrokerConnectionUrlsTest {

    @Nested
    @DisplayName("ActiveMQ classic")
    class Classic {

        @Test
        void should_bound_a_plain_transport_with_connection_and_socket_timeouts() {
            String bounded = BrokerConnectionUrls.boundedActiveMqClassic("tcp://broker:61616", 2000);
            assertThat(bounded).isEqualTo("tcp://broker:61616?connectionTimeout=2000&soTimeout=2000");
        }

        @Test
        void should_add_to_an_existing_query_without_a_second_question_mark() {
            String bounded = BrokerConnectionUrls.boundedActiveMqClassic("ssl://broker:61616?wireFormat.maxInactivityDuration=0", 2000);
            assertThat(bounded)
                .startsWith("ssl://broker:61616?wireFormat.maxInactivityDuration=0")
                .contains("&connectionTimeout=2000")
                .contains("&soTimeout=2000")
                .doesNotContain("??");
        }

        @Test
        void should_not_override_a_timeout_the_target_already_declares() {
            String bounded = BrokerConnectionUrls.boundedActiveMqClassic("tcp://broker:61616?soTimeout=500", 2000);
            // the team's own soTimeout is kept; only the missing connectionTimeout is added
            assertThat(bounded).isEqualTo("tcp://broker:61616?soTimeout=500&connectionTimeout=2000");
        }

        @Test
        void should_cap_a_failover_url_to_a_single_attempt_so_it_cannot_retry_for_ever() {
            String bounded = BrokerConnectionUrls.boundedActiveMqClassic("failover:(tcp://a:61616,tcp://b:61616)", 2000);
            assertThat(bounded).isEqualTo(
                "failover:(tcp://a:61616,tcp://b:61616)?startupMaxReconnectAttempts=1&maxReconnectAttempts=0&timeout=2000");
        }

        @Test
        void should_place_failover_options_after_the_composite_even_with_an_inner_query() {
            String bounded = BrokerConnectionUrls.boundedActiveMqClassic("failover:(tcp://a:61616?soTimeout=500)", 2000);
            // the inner transport query is preserved; failover options go after the closing parenthesis
            assertThat(bounded).isEqualTo(
                "failover:(tcp://a:61616?soTimeout=500)?startupMaxReconnectAttempts=1&maxReconnectAttempts=0&timeout=2000");
        }

        @Test
        void should_leave_an_in_process_vm_url_untouched() {
            assertThat(BrokerConnectionUrls.boundedActiveMqClassic("vm://broker", 2000)).isEqualTo("vm://broker");
        }
    }

    @Nested
    @DisplayName("Artemis")
    class Artemis {

        @Test
        void should_bound_the_tcp_connect_and_the_blocking_calls() {
            // connect-timeout-millis is a netty connector key carried through the client uri; it caps
            // the TCP connect to an unreachable host (default is -1, i.e. netty's ~30s)
            assertThat(BrokerConnectionUrls.boundedArtemis("tcp://broker:61616", 2000))
                .isEqualTo("tcp://broker:61616?connect-timeout-millis=2000&callTimeout=2000");
        }

        @Test
        void should_keep_a_timeout_the_target_already_declares() {
            assertThat(BrokerConnectionUrls.boundedArtemis("tcp://broker:61616?callTimeout=500", 2000))
                .isEqualTo("tcp://broker:61616?callTimeout=500&connect-timeout-millis=2000");
        }

        @Test
        void should_leave_an_in_process_vm_url_untouched() {
            assertThat(BrokerConnectionUrls.boundedArtemis("vm://0", 2000)).isEqualTo("vm://0");
        }
    }
}
