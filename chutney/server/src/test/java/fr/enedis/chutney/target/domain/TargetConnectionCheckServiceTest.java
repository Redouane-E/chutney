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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TargetConnectionCheckService")
class TargetConnectionCheckServiceTest {

    private static final long THROTTLE_MS = 30_000;

    private final TargetApi targetApi = mock(TargetApi.class);
    private final InMemoryStatusRepository statusRepository = new InMemoryStatusRepository();

    @Test
    void should_return_UP_when_a_matching_checker_succeeds() {
        // Given
        givenTarget("http://localhost");
        TargetConnectionCheckService service = serviceWith(checker(true, null));

        // When
        TargetConnectionCheckResult result = service.check("DEFAULT", "target", true).result();

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
        TargetConnectionCheckResult result = service.check("DEFAULT", "target", true).result();

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

        TargetConnectionCheckResult result = service.check("DEFAULT", "target", true).result();

        assertThat(result.reason()).isEqualTo(TargetConnectionCheckResult.Reason.AUTH_FAILED);
    }

    @Test
    void should_redact_credentials_that_a_driver_embeds_in_the_failure_detail() {
        givenTarget("http://localhost");
        TargetConnectionCheckService service = serviceWith(
            checker(true, new IllegalStateException("cannot open jdbc:pg://localhost/db?user=svc&password=topsecret")));

        TargetConnectionCheckResult result = service.check("DEFAULT", "target", true).result();

        assertThat(result.detail()).doesNotContain("topsecret");
        assertThat(result.detail()).contains("password=***");
    }

    @Test
    void should_redact_secrets_whatever_shape_a_driver_reports_them_in() {
        givenTarget("http://localhost");
        String leaky = "sasl.jaas.config = PlainLoginModule required username=\"admin\" password=\"s3cr3t\"; "
            + "uri=mongodb://user:p@ss/w0rd@host:27017 "
            + "token=ghp_AbCdEf0123456789 "
            + "passphrase: topsecret "
            + "Authorization: Basic YWRtaW46aHVudGVyMg==";
        TargetConnectionCheckService service = serviceWith(checker(true, new IllegalStateException(leaky)));

        String detail = service.check("DEFAULT", "target", true).result().detail();

        assertThat(detail)
            .doesNotContain("s3cr3t")
            // assert on the tail too: matching only the whole password would pass while most of it
            // survived, which is exactly how an earlier leak stayed hidden
            .doesNotContain("p@ss/w0rd")
            .doesNotContain("ss/w0rd")
            .doesNotContain("w0rd")
            .doesNotContain("ghp_AbCdEf0123456789")
            .doesNotContain("topsecret")
            .doesNotContain("YWRtaW46aHVudGVyMg==");
    }

    @Test
    void should_redact_a_password_containing_unencoded_separators() {
        // '@' and '/' inside a password are a common mistake; stopping at the first one would leave
        // most of the password readable by everyone
        givenTarget("http://localhost");
        TargetConnectionCheckService service = serviceWith(checker(true,
            new IllegalStateException("connect failed: mongodb://user:p@ss/w0rd@host:27017")));

        String detail = service.check("DEFAULT", "target", true).result().detail();

        assertThat(detail).contains("mongodb://user:***@host:27017");
        assertThat(detail).doesNotContain("ss/w0rd");
    }

    @Test
    void should_keep_prose_that_merely_mentions_an_authentication_scheme() {
        givenTarget("http://localhost");
        TargetConnectionCheckService service = serviceWith(checker(true,
            new IllegalStateException("Bearer token has expired")));

        String detail = service.check("DEFAULT", "target", true).result().detail();

        // the word explaining the failure must survive; only credential material is hidden
        assertThat(detail).contains("Bearer token has expired");
    }

    @Test
    void should_describe_the_same_failure_it_named() {
        // Given: a driver wrapping an authentication failure over a lower-level connect error
        givenTarget("jdbc:postgresql://localhost/db");
        Exception wrapped = new IllegalStateException(
            "Failed to initialize pool: FATAL: password authentication failed for user \"app\"",
            new java.net.ConnectException("Connection refused"));
        TargetConnectionCheckService service = serviceWith(checker(true, wrapped));

        TargetConnectionCheckResult result = service.check("DEFAULT", "target", true).result();

        // Then: headline and detail agree — reporting "authentication failed" with a "refused" detail
        // would leave the user unable to act
        assertThat(result.reason()).isEqualTo(TargetConnectionCheckResult.Reason.AUTH_FAILED);
        assertThat(result.detail()).contains("authentication failed");
        assertThat(result.detail()).doesNotContain("Connection refused");
    }

    @Test
    void should_not_treat_a_result_from_the_future_as_fresh() {
        // Given: the clock stepped backwards, leaving a status stamped in the future
        givenTarget("http://localhost");
        AtomicInteger probes = new AtomicInteger();
        TargetConnectionCheckService service = serviceWith(countingChecker(probes));
        statusRepository.save(new TargetConnectionStatus("DEFAULT", "target",
            TargetConnectionCheckResult.up(1), Instant.now().plusSeconds(3600)));

        // When
        service.check("DEFAULT", "target", false);

        // Then: it probed rather than trusting a timestamp that cannot be right
        assertThat(probes.get()).isEqualTo(1);
    }

    @Test
    void should_return_NOT_TESTABLE_when_no_checker_handles_the_protocol() {
        // Given
        givenTarget("ftp://localhost");
        TargetConnectionCheckService service = serviceWith(checker(false, null));

        // When
        TargetConnectionCheckResult result = service.check("DEFAULT", "target", true).result();

        // Then
        assertThat(result.status()).isEqualTo(TargetConnectionCheckResult.Status.UNKNOWN);
        assertThat(result.reason()).isEqualTo(TargetConnectionCheckResult.Reason.PROTOCOL_NOT_SUPPORTED);
    }

    @Test
    void should_return_NOT_TESTABLE_when_there_is_no_checker_at_all() {
        // Given
        givenTarget("http://localhost");
        TargetConnectionCheckService service = service(emptyList());

        // When
        TargetConnectionCheckResult result = service.check("DEFAULT", "target", true).result();

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
        TargetConnectionCheckService service = service(List.of(fallback, checker(true, null)));

        // When
        TargetConnectionCheckResult result = service.check("DEFAULT", "target", true).result();

        // Then: the default-priority checker won, so the fallback never threw
        assertThat(result.status()).isEqualTo(TargetConnectionCheckResult.Status.UP);
    }

    @Test
    void should_not_record_a_verdict_for_a_target_that_changed_while_it_was_probed() {
        // Given: the target is edited (and its status invalidated) while the probe is running
        givenTarget("http://localhost");
        TargetConnectionCheckService service = serviceWith(new TargetConnectionChecker() {
            @Override
            public boolean canHandle(Target target) {
                return true;
            }

            @Override
            public void check(Target target, int timeoutMs) {
                statusRepository.evict("DEFAULT", "target");
            }
        });

        // When
        TargetConnectionStatus returned = service.check("DEFAULT", "target", true);

        // Then: the caller still gets its own answer...
        assertThat(returned.result().status()).isEqualTo(TargetConnectionCheckResult.Status.UP);
        // ...but the verdict describes the target as it was before the edit, so it must not come back
        assertThat(statusRepository.find("DEFAULT", "target")).isEmpty();
    }

    @Test
    void should_release_its_workers_when_closed() {
        // A context rebuilt in place would otherwise leave a pool of threads behind every time
        givenTarget("http://localhost");
        TargetConnectionCheckService service = serviceWith(checker(true, null));
        service.check("DEFAULT", "target", true);

        service.close();

        // the probe now has nowhere to run, and says so instead of blaming the target
        TargetConnectionCheckResult result = service.check("DEFAULT", "target", true).result();
        assertThat(result.status()).isEqualTo(TargetConnectionCheckResult.Status.DOWN);
        assertThat(result.reason()).isEqualTo(TargetConnectionCheckResult.Reason.UNREACHABLE);
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

    @Test
    void should_share_the_result_of_a_saved_target_so_every_user_sees_it() {
        // Given
        givenTarget("http://localhost");
        TargetConnectionCheckService service = serviceWith(checker(true, null));

        // When
        service.check("DEFAULT", "target", true);

        // Then: it is readable by anyone, not kept for the caller only
        assertThat(service.lastStatuses())
            .singleElement()
            .satisfies(status -> {
                assertThat(status.environmentName()).isEqualTo("DEFAULT");
                assertThat(status.targetName()).isEqualTo("target");
                assertThat(status.result().status()).isEqualTo(TargetConnectionCheckResult.Status.UP);
                assertThat(status.checkedAt()).isNotNull();
            });
    }

    @Test
    void should_not_share_the_result_of_unsaved_values() {
        // Given: the "test before save" path probes values that do not identify a saved target
        TargetConnectionCheckService service = serviceWith(checker(true, null));

        // When
        service.check(new TargetDto("target", "http://localhost", "DEFAULT", Set.of()));

        // Then
        assertThat(service.lastStatuses()).isEmpty();
    }

    @Test
    void should_reuse_a_recent_result_when_the_check_is_not_forced() {
        // Given: a first probe has just run
        givenTarget("http://localhost");
        AtomicInteger probes = new AtomicInteger();
        TargetConnectionCheckService service = serviceWith(countingChecker(probes));
        service.check("DEFAULT", "target", true);

        // When: an unforced check follows immediately (this is what "test all" does)
        TargetConnectionStatus status = service.check("DEFAULT", "target", false);

        // Then: the probed system was not contacted again
        assertThat(probes.get()).isEqualTo(1);
        assertThat(status.result().status()).isEqualTo(TargetConnectionCheckResult.Status.UP);
    }

    @Test
    void should_probe_once_when_several_callers_check_the_same_target_at_the_same_time() throws Exception {
        // Given: a slow probe, and several users pressing test at the same moment
        givenTarget("http://localhost");
        AtomicInteger probes = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        TargetConnectionCheckService service = serviceWith(new TargetConnectionChecker() {
            @Override
            public boolean canHandle(Target target) {
                return true;
            }

            @Override
            public void check(Target target, int timeoutMs) throws Exception {
                probes.incrementAndGet();
                release.await(2, java.util.concurrent.TimeUnit.SECONDS);
            }
        });

        // When
        ExecutorService callers = Executors.newFixedThreadPool(4);
        try {
            List<Future<TargetConnectionStatus>> results = callers.invokeAll(List.of(
                () -> service.check("DEFAULT", "target", true),
                () -> service.check("DEFAULT", "target", true),
                () -> service.check("DEFAULT", "target", true),
                () -> service.check("DEFAULT", "target", true)));
            release.countDown();
            for (Future<TargetConnectionStatus> result : results) {
                result.get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            release.countDown();
            callers.shutdownNow();
        }

        // Then: the probed system saw a single authentication attempt, not four — repeated failed
        // logins are what locks a service account
        assertThat(probes.get()).isEqualTo(1);
    }

    @Test
    void should_always_probe_again_when_the_check_is_forced() {
        // Given
        givenTarget("http://localhost");
        AtomicInteger probes = new AtomicInteger();
        TargetConnectionCheckService service = serviceWith(countingChecker(probes));
        service.check("DEFAULT", "target", true);

        // When: the user explicitly tests this target — they need the truth, not a cached verdict
        service.check("DEFAULT", "target", true);

        // Then
        assertThat(probes.get()).isEqualTo(2);
    }

    @Test
    void should_probe_when_the_known_result_is_older_than_the_throttle_window() {
        // Given: a status older than the throttle window
        givenTarget("http://localhost");
        AtomicInteger probes = new AtomicInteger();
        TargetConnectionCheckService service = serviceWith(countingChecker(probes));
        statusRepository.save(new TargetConnectionStatus("DEFAULT", "target",
            TargetConnectionCheckResult.up(1), Instant.now().minusMillis(THROTTLE_MS * 2)));

        // When
        service.check("DEFAULT", "target", false);

        // Then
        assertThat(probes.get()).isEqualTo(1);
    }

    private void givenTarget(String url) {
        when(targetApi.getTarget("DEFAULT", "target"))
            .thenReturn(new TargetDto("target", url, "DEFAULT", Set.of()));
    }

    private TargetConnectionCheckService serviceWith(TargetConnectionChecker checker) {
        return service(List.of(checker));
    }

    private TargetConnectionCheckService service(List<TargetConnectionChecker> checkers) {
        return new TargetConnectionCheckService(targetApi, statusRepository, checkers, 1000, THROTTLE_MS, 900_000, 2);
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

    private TargetConnectionChecker countingChecker(AtomicInteger probes) {
        return new TargetConnectionChecker() {
            @Override
            public boolean canHandle(Target target) {
                return true;
            }

            @Override
            public void check(Target target, int timeoutMs) {
                probes.incrementAndGet();
            }
        };
    }

    private static final class InMemoryStatusRepository implements TargetConnectionStatusRepository {

        private final java.util.Map<String, TargetConnectionStatus> statuses = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, Long> revisions = new java.util.LinkedHashMap<>();

        @Override
        public void save(TargetConnectionStatus status) {
            statuses.put(status.environmentName() + "::" + status.targetName(), status);
        }

        @Override
        public Optional<TargetConnectionStatus> find(String environmentName, String targetName) {
            return Optional.ofNullable(statuses.get(environmentName + "::" + targetName));
        }

        @Override
        public List<TargetConnectionStatus> findAll() {
            return List.copyOf(statuses.values());
        }

        @Override
        public void evict(String environmentName, String targetName) {
            String key = environmentName + "::" + targetName;
            revisions.merge(key, 1L, Long::sum);
            statuses.remove(key);
        }

        @Override
        public void evictEnvironment(String environmentName) {
            statuses.keySet().stream()
                .filter(key -> key.startsWith(environmentName + "::"))
                .toList()
                .forEach(key -> revisions.merge(key, 1L, Long::sum));
            statuses.keySet().removeIf(key -> key.startsWith(environmentName + "::"));
        }

        @Override
        public long revision(String environmentName, String targetName) {
            return revisions.getOrDefault(environmentName + "::" + targetName, 0L);
        }

        @Override
        public boolean saveIfUnchanged(TargetConnectionStatus status, long expectedRevision) {
            if (revision(status.environmentName(), status.targetName()) != expectedRevision) {
                return false;
            }
            save(status);
            return true;
        }
    }
}
