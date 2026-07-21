/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.domain;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.environment.api.target.TargetApi;
import fr.enedis.chutney.environment.api.target.dto.TargetDto;
import fr.enedis.chutney.tools.Entry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TargetConnectionCheckService")
class TargetConnectionCheckServiceTest {

    private final TargetApi targetApi = mock(TargetApi.class);

    @Test
    void should_return_UP_when_a_matching_checker_succeeds() {
        // Given
        givenTarget("http://localhost");
        TargetConnectionCheckService service = serviceWith(checker(true, null));

        // When
        TargetConnectionCheckResult result = service.check("DEFAULT", "target");

        // Then
        assertThat(result.status()).isEqualTo(TargetConnectionCheckResult.Status.UP);
    }

    @Test
    void should_return_DOWN_with_a_classified_reason_and_sanitized_detail_when_the_checker_fails() {
        // Given: a target carrying a secret property and a checker that fails
        when(targetApi.getTarget("DEFAULT", "target"))
            .thenReturn(new TargetDto("target", "http://localhost", "DEFAULT", Set.of(new Entry("password", "topsecret"))));
        TargetConnectionCheckService service = serviceWith(checker(true, new IllegalStateException("Connection refused")));

        // When
        TargetConnectionCheckResult result = service.check("DEFAULT", "target");

        // Then
        assertThat(result.status()).isEqualTo(TargetConnectionCheckResult.Status.DOWN);
        assertThat(result.reason()).isEqualTo(TargetConnectionCheckResult.Reason.CONNECTION_REFUSED);
        assertThat(result.detail()).contains("Connection refused");
        assertThat(result.detail()).doesNotContain("topsecret");
    }

    @Test
    void should_classify_an_authentication_failure() {
        givenTarget("http://localhost");
        TargetConnectionCheckService service = serviceWith(checker(true, new IllegalStateException("Authentication failed (HTTP 401)")));

        TargetConnectionCheckResult result = service.check("DEFAULT", "target");

        assertThat(result.reason()).isEqualTo(TargetConnectionCheckResult.Reason.AUTH_FAILED);
    }

    @Test
    void should_redact_credentials_that_a_driver_embeds_in_the_failure_detail() {
        givenTarget("http://localhost");
        TargetConnectionCheckService service = serviceWith(
            checker(true, new IllegalStateException("cannot open jdbc:pg://localhost/db?user=svc&password=topsecret")));

        TargetConnectionCheckResult result = service.check("DEFAULT", "target");

        assertThat(result.detail()).doesNotContain("topsecret");
        assertThat(result.detail()).contains("password=***");
    }

    @Test
    void should_return_NOT_TESTABLE_when_no_checker_handles_the_protocol() {
        // Given
        givenTarget("kafka://localhost:9092");
        TargetConnectionCheckService service = serviceWith(checker(false, null));

        // When
        TargetConnectionCheckResult result = service.check("DEFAULT", "target");

        // Then
        assertThat(result.status()).isEqualTo(TargetConnectionCheckResult.Status.UNKNOWN);
        assertThat(result.reason()).isEqualTo(TargetConnectionCheckResult.Reason.PROTOCOL_NOT_SUPPORTED);
    }

    @Test
    void should_return_NOT_TESTABLE_when_there_is_no_checker_at_all() {
        // Given
        givenTarget("http://localhost");
        TargetConnectionCheckService service = new TargetConnectionCheckService(targetApi, emptyList(), 1000);

        // When
        TargetConnectionCheckResult result = service.check("DEFAULT", "target");

        // Then
        assertThat(result.status()).isEqualTo(TargetConnectionCheckResult.Status.UNKNOWN);
    }

    @Test
    void should_prefer_the_higher_priority_checker_when_several_match() {
        // Given: a low-priority fallback that would fail, and a default-priority specific checker
        givenTarget("tcp://host:9092");
        TargetConnectionChecker fallback = new TargetConnectionChecker() {
            @Override
            public boolean canHandle(Target target) {
                return true;
            }

            @Override
            public int priority() {
                return -1000;
            }

            @Override
            public void check(Target target, int timeoutMs) {
                throw new IllegalStateException("the fallback should not have run");
            }
        };
        TargetConnectionCheckService service =
            new TargetConnectionCheckService(targetApi, List.of(fallback, checker(true, null)), 1000);

        // When
        TargetConnectionCheckResult result = service.check("DEFAULT", "target");

        // Then: the default-priority checker won, so the fallback never threw
        assertThat(result.status()).isEqualTo(TargetConnectionCheckResult.Status.UP);
    }

    @Test
    void should_probe_a_target_definition_supplied_directly_without_a_lookup() {
        // Given: the "test before save" path — no repository lookup
        TargetConnectionCheckService service = serviceWith(checker(true, null));

        // When
        TargetConnectionCheckResult result = service.check(new TargetDto("target", "http://localhost", "DEFAULT", Set.of()));

        // Then
        assertThat(result.status()).isEqualTo(TargetConnectionCheckResult.Status.UP);
    }

    private void givenTarget(String url) {
        when(targetApi.getTarget("DEFAULT", "target"))
            .thenReturn(new TargetDto("target", url, "DEFAULT", Set.of()));
    }

    private TargetConnectionCheckService serviceWith(TargetConnectionChecker checker) {
        return new TargetConnectionCheckService(targetApi, List.of(checker), 1000);
    }

    private TargetConnectionChecker checker(boolean canHandle, Exception toThrow) {
        return new TargetConnectionChecker() {
            @Override
            public boolean canHandle(Target target) {
                return canHandle;
            }

            @Override
            public void check(Target target, int timeoutMs) throws Exception {
                if (toThrow != null) {
                    throw toThrow;
                }
            }
        };
    }
}
