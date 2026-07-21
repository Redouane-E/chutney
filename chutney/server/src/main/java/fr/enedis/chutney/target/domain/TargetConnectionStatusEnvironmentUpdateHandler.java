/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.domain;

import fr.enedis.chutney.server.core.domain.environment.UpdateEnvironmentHandler;

/**
 * Drops the connectivity statuses of an environment whose targets are no longer addressed the way
 * they were checked.
 * <p>
 * Statuses are keyed by (environment, target). Deleting an environment removes its targets, and
 * renaming one moves every target under a new key — in both cases the recorded verdicts would
 * otherwise linger, unreachable and stale, until their time-to-live elapsed.
 */
public class TargetConnectionStatusEnvironmentUpdateHandler implements UpdateEnvironmentHandler {

    private final TargetConnectionStatusRepository statusRepository;

    public TargetConnectionStatusEnvironmentUpdateHandler(TargetConnectionStatusRepository statusRepository) {
        this.statusRepository = statusRepository;
    }

    @Override
    public void renameEnvironment(String oldName, String newName) {
        // The targets are carried over unchanged, but under a new environment name: rather than
        // re-key verdicts obtained before the rename, drop them and let users check again.
        statusRepository.evictEnvironment(oldName);
        statusRepository.evictEnvironment(newName);
    }

    @Override
    public void deleteEnvironment(String environmentName) {
        statusRepository.evictEnvironment(environmentName);
    }
}
