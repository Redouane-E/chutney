/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.server.core.domain.environment;

/**
 * Notified when a target changes, so features holding data derived from a target's definition can
 * drop it. Mirrors {@link UpdateEnvironmentHandler} at target level.
 * <p>
 * Implementations live outside the environment module and must be quick and forgiving: they run
 * inline with the mutation, after it has been persisted.
 */
public interface UpdateTargetHandler {

    /**
     * A target was added, edited or renamed. {@code previousName} equals {@code newName} unless the
     * target was renamed; both are reported so data held under either key can be dropped.
     */
    void updateTarget(String environmentName, String previousName, String newName);

    void deleteTarget(String environmentName, String targetName);
}
