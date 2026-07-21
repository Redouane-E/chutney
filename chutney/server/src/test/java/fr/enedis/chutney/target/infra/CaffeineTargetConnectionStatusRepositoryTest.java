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
