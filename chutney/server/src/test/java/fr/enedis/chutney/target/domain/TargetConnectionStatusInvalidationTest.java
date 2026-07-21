/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.domain;

import static org.assertj.core.api.Assertions.assertThat;

import fr.enedis.chutney.target.infra.CaffeineTargetConnectionStatusRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A recorded verdict describes the url and credentials it was obtained with, so it must not survive a
 * change to the target — otherwise everyone keeps seeing it until its time-to-live elapses.
 */
@DisplayName("Connectivity status invalidation")
class TargetConnectionStatusInvalidationTest {

    private final CaffeineTargetConnectionStatusRepository repository =
        new CaffeineTargetConnectionStatusRepository(1, "HOURS");
    private final TargetConnectionStatusUpdateHandler targetHandler =
        new TargetConnectionStatusUpdateHandler(repository);
    private final TargetConnectionStatusEnvironmentUpdateHandler environmentHandler =
        new TargetConnectionStatusEnvironmentUpdateHandler(repository);

    @Test
    void should_forget_the_status_of_an_edited_target() {
        givenStatus("DEFAULT", "billing-api");

        targetHandler.updateTarget("DEFAULT", "billing-api", "billing-api");

        assertThat(repository.find("DEFAULT", "billing-api")).isEmpty();
    }

    @Test
    void should_forget_both_names_when_a_target_is_renamed() {
        givenStatus("DEFAULT", "old-name");
        givenStatus("DEFAULT", "new-name");

        targetHandler.updateTarget("DEFAULT", "old-name", "new-name");

        assertThat(repository.find("DEFAULT", "old-name")).isEmpty();
        assertThat(repository.find("DEFAULT", "new-name")).isEmpty();
    }

    @Test
    void should_forget_the_status_of_a_deleted_target_so_a_target_recreated_with_the_same_name_starts_unchecked() {
        givenStatus("DEFAULT", "billing-api");

        targetHandler.deleteTarget("DEFAULT", "billing-api");

        assertThat(repository.find("DEFAULT", "billing-api")).isEmpty();
    }

    @Test
    void should_only_forget_the_target_that_changed() {
        givenStatus("DEFAULT", "billing-api");
        givenStatus("DEFAULT", "orders-db");
        givenStatus("PROD", "billing-api");

        targetHandler.deleteTarget("DEFAULT", "billing-api");

        assertThat(repository.find("DEFAULT", "orders-db")).isPresent();
        assertThat(repository.find("PROD", "billing-api")).isPresent();
    }

    @Test
    void should_forget_every_status_of_a_deleted_environment() {
        givenStatus("DEFAULT", "billing-api");
        givenStatus("DEFAULT", "orders-db");
        givenStatus("PROD", "billing-api");

        environmentHandler.deleteEnvironment("DEFAULT");

        assertThat(repository.findAll())
            .extracting(TargetConnectionStatus::environmentName)
            .containsExactly("PROD");
    }

    @Test
    void should_forget_statuses_on_both_sides_of_an_environment_rename() {
        givenStatus("OLD", "billing-api");
        givenStatus("NEW", "billing-api");
        givenStatus("OTHER", "billing-api");

        environmentHandler.renameEnvironment("OLD", "NEW");

        assertThat(repository.findAll())
            .extracting(TargetConnectionStatus::environmentName)
            .containsExactly("OTHER");
    }

    private void givenStatus(String environmentName, String targetName) {
        repository.save(new TargetConnectionStatus(
            environmentName, targetName, TargetConnectionCheckResult.up(10), Instant.now()));
    }
}
