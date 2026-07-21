/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.http.check;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.tools.SocketUtils;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HttpConnectionChecker")
class HttpConnectionCheckerTest {

    private final HttpConnectionChecker checker = new HttpConnectionChecker();

    @Nested
    @DisplayName("canHandle")
    class CanHandle {

        @Test
        void should_handle_http_and_https_schemes() {
            assertThat(checker.canHandle(targetWithUrl("http://localhost:8080"))).isTrue();
            assertThat(checker.canHandle(targetWithUrl("https://localhost:8443"))).isTrue();
        }

        @Test
        void should_not_handle_other_schemes() {
            assertThat(checker.canHandle(targetWithUrl("ssh://localhost:22"))).isFalse();
            assertThat(checker.canHandle(targetWithUrl("jdbc:h2:mem:test"))).isFalse();
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        private WireMockServer wireMockServer;

        @BeforeEach
        void start_server() {
            wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
            wireMockServer.start();
        }

        @AfterEach
        void stop_server() {
            wireMockServer.stop();
        }

        @Test
        void should_succeed_when_the_host_answers() {
            // Given
            wireMockServer.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
            Target target = targetWithUrl("http://localhost:" + wireMockServer.port());

            // When
            Throwable thrown = catchThrowable(() -> checker.check(target, 2000));

            // Then
            assertThat(thrown).isNull();
        }

        @Test
        void should_succeed_when_the_host_answers_with_an_error_status() {
            // Given: a reachable host is UP even if it rejects the request (e.g. 401)
            wireMockServer.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(401)));
            Target target = targetWithUrl("http://localhost:" + wireMockServer.port());

            // When
            Throwable thrown = catchThrowable(() -> checker.check(target, 2000));

            // Then
            assertThat(thrown).isNull();
        }

        @Test
        void should_fail_when_the_host_is_unreachable() {
            // Given: a free port with nothing listening
            Target target = targetWithUrl("http://localhost:" + SocketUtils.findAvailableTcpPort());

            // When / Then
            assertThatThrownBy(() -> checker.check(target, 2000)).isInstanceOf(Exception.class);
        }

        @Test
        void should_fail_when_configured_credentials_are_rejected() {
            // Given: the target carries credentials but the server rejects them
            wireMockServer.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(401)));
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("http-target")
                .withUrl("http://localhost:" + wireMockServer.port())
                .withProperty("username", "user")
                .withProperty("password", "wrong")
                .build();

            // When / Then
            assertThatThrownBy(() -> checker.check(target, 2000)).isInstanceOf(Exception.class);
        }
    }

    private static Target targetWithUrl(String url) {
        return TestTarget.TestTargetBuilder.builder()
            .withTargetId("http-target")
            .withUrl(url)
            .build();
    }
}
