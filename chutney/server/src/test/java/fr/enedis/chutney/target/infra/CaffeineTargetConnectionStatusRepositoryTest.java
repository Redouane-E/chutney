/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import fr.enedis.chutney.target.domain.TargetConnectionCheckResult;
import fr.enedis.chutney.target.domain.TargetConnectionStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CaffeineTargetConnectionStatusRepository")
class CaffeineTargetConnectionStatusRepositoryTest {

    @Test
    void should_return_the_last_status_saved_for_a_target() {
        CaffeineTargetConnectionStatusRepository repository = repository(1, "HOURS");
        repository.save(status("DEFAULT", "target", TargetConnectionCheckResult.up(10)));

        repository.save(status("DEFAULT", "target",
            TargetConnectionCheckResult.down(TargetConnectionCheckResult.Reason.TIMEOUT, "timed out", 20)));

        assertThat(repository.find("DEFAULT", "target"))
            .hasValueSatisfying(status -> assertThat(status.result().reason())
                .isEqualTo(TargetConnectionCheckResult.Reason.TIMEOUT));
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void should_keep_statuses_of_the_same_target_name_in_different_environments_apart() {
        CaffeineTargetConnectionStatusRepository repository = repository(1, "HOURS");

        repository.save(status("DEFAULT", "target", TargetConnectionCheckResult.up(10)));
        repository.save(status("PROD", "target", TargetConnectionCheckResult.notTestable()));

        assertThat(repository.find("DEFAULT", "target"))
            .hasValueSatisfying(s -> assertThat(s.result().status()).isEqualTo(TargetConnectionCheckResult.Status.UP));
        assertThat(repository.find("PROD", "target"))
            .hasValueSatisfying(s -> assertThat(s.result().status()).isEqualTo(TargetConnectionCheckResult.Status.UNKNOWN));
    }

    @Test
    void should_return_empty_for_an_unknown_target() {
        assertThat(repository(1, "HOURS").find("DEFAULT", "never-checked")).isEmpty();
    }

    @Test
    void should_record_a_status_when_nothing_invalidated_the_target_meanwhile() {
        CaffeineTargetConnectionStatusRepository repository = repository(1, "HOURS");
        long revision = repository.revision("DEFAULT", "target");

        boolean saved = repository.saveIfUnchanged(status("DEFAULT", "target", TargetConnectionCheckResult.up(10)), revision);

        assertThat(saved).isTrue();
        assertThat(repository.find("DEFAULT", "target")).isPresent();
    }

    @Test
    void should_refuse_a_status_whose_target_was_invalidated_while_it_was_being_probed() {
        CaffeineTargetConnectionStatusRepository repository = repository(1, "HOURS");
        long revision = repository.revision("DEFAULT", "target");

        // the target is edited while the probe runs
        repository.evict("DEFAULT", "target");

        boolean saved = repository.saveIfUnchanged(status("DEFAULT", "target", TargetConnectionCheckResult.up(10)), revision);

        assertThat(saved).isFalse();
        assertThat(repository.find("DEFAULT", "target")).isEmpty();
    }

    @Test
    void should_refuse_a_status_whose_environment_was_invalidated_while_it_was_being_probed() {
        CaffeineTargetConnectionStatusRepository repository = repository(1, "HOURS");
        repository.save(status("DEFAULT", "target", TargetConnectionCheckResult.up(10)));
        long revision = repository.revision("DEFAULT", "target");

        repository.evictEnvironment("DEFAULT");

        boolean saved = repository.saveIfUnchanged(status("DEFAULT", "target", TargetConnectionCheckResult.up(20)), revision);

        assertThat(saved).isFalse();
        assertThat(repository.find("DEFAULT", "target")).isEmpty();
    }

    @Test
    void should_refuse_a_first_ever_probe_saved_after_its_environment_was_invalidated() {
        // A target probed for the very first time has no stored status and no per-target revision to
        // bump, so only an environment-level revision can stop its in-flight probe from resurrecting a
        // verdict for an environment that was deleted or renamed while the probe was running.
        CaffeineTargetConnectionStatusRepository repository = repository(1, "HOURS");
        long revision = repository.revision("DEFAULT", "never-probed");

        repository.evictEnvironment("DEFAULT");

        boolean saved = repository.saveIfUnchanged(status("DEFAULT", "never-probed", TargetConnectionCheckResult.up(10)), revision);

        assertThat(saved).isFalse();
        assertThat(repository.find("DEFAULT", "never-probed")).isEmpty();
    }

    @Test
    void should_forget_a_status_once_its_time_to_live_has_passed() {
        // Given: a very short retention, so an old verdict is never presented as current
        CaffeineTargetConnectionStatusRepository repository = repository(1, "SECONDS");
        repository.save(status("DEFAULT", "target", TargetConnectionCheckResult.up(10)));
        assertThat(repository.find("DEFAULT", "target")).isPresent();

        // When / Then
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(repository.find("DEFAULT", "target")).isEmpty());
    }

    private CaffeineTargetConnectionStatusRepository repository(int ttlValue, String ttlUnit) {
        return new CaffeineTargetConnectionStatusRepository(ttlValue, ttlUnit);
    }

    private TargetConnectionStatus status(String environmentName, String targetName, TargetConnectionCheckResult result) {
        return new TargetConnectionStatus(environmentName, targetName, result, Instant.now());
    }
}
