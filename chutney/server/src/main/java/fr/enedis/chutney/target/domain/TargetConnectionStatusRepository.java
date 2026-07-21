/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.domain;

import java.util.List;
import java.util.Optional;

/**
 * Holds the last connectivity result per target, shared across users of the instance.
 * <p>
 * Entries are expected to expire on their own: a probe outcome is point-in-time, and showing an old
 * result as if it were current would be misleading. Implementations therefore bound how long a status
 * is kept, and callers must be prepared for {@link #find} to return empty.
 */
public interface TargetConnectionStatusRepository {

    void save(TargetConnectionStatus status);

    Optional<TargetConnectionStatus> find(String environmentName, String targetName);

    /**
     * @return every status still within its retention window, in no particular order.
     */
    List<TargetConnectionStatus> findAll();

    /**
     * Forgets the status of one target. Called when the target changes or disappears: a verdict
     * describes the configuration it was obtained with, so it must not outlive it.
     */
    void evict(String environmentName, String targetName);

    /**
     * Forgets every status of an environment, for changes that affect all of its targets at once
     * (the environment is deleted, renamed, or replaced wholesale by an import).
     */
    void evictEnvironment(String environmentName);
}
