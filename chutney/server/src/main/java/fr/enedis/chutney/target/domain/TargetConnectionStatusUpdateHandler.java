/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.domain;

import fr.enedis.chutney.server.core.domain.environment.UpdateTargetHandler;

/**
 * Drops the connectivity status of a target as soon as its definition changes.
 * <p>
 * A verdict describes the url and credentials it was obtained with. Editing a target's url or
 * password, renaming it, or deleting and recreating it under the same name would otherwise leave the
 * previous verdict on display — shown to everyone, for as long as its time-to-live lasts. Forgetting
 * it is the honest answer: the target simply reads as not checked until someone checks it again.
 */
public class TargetConnectionStatusUpdateHandler implements UpdateTargetHandler {

    private final TargetConnectionStatusRepository statusRepository;

    public TargetConnectionStatusUpdateHandler(TargetConnectionStatusRepository statusRepository) {
        this.statusRepository = statusRepository;
    }

    @Override
    public void updateTarget(String environmentName, String previousName, String newName) {
        statusRepository.evict(environmentName, previousName);
        statusRepository.evict(environmentName, newName);
    }

    @Override
    public void deleteTarget(String environmentName, String targetName) {
        statusRepository.evict(environmentName, targetName);
    }
}
