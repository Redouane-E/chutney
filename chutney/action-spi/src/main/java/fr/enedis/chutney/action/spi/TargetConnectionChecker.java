/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.spi;

import fr.enedis.chutney.action.spi.injectable.Target;

/**
 * SPI for a per-protocol target connectivity probe.
 * <p>
 * Implementations open a real (short-timeout) connection to a {@link Target} to verify it is
 * reachable and that its credentials are valid. They are discovered at runtime from
 * {@code META-INF/extension/chutney.checkers}, mirroring how {@link Action}s are discovered from
 * {@code META-INF/extension/chutney.actions}, so each implementation must expose a single
 * no-argument constructor.
 */
public interface TargetConnectionChecker {

    /**
     * @param target the target to probe
     * @return {@code true} if this checker knows how to reach the given target, typically based on
     * {@code target.uri().getScheme()} / {@code target.rawUri()}.
     */
    boolean canHandle(Target target);

    /**
     * Dispatch priority when several checkers can handle the same target — the highest wins.
     * Protocol-specific checkers keep the default; a generic fallback (e.g. raw TCP reachability)
     * returns a lower value so it is only chosen when no protocol-specific checker matches.
     */
    default int priority() {
        return 0;
    }

    /**
     * Attempts to reach the target. Returns normally when the target is reachable, throws otherwise.
     *
     * @param target    the target to probe
     * @param timeoutMs maximum time to wait when establishing the connection, in milliseconds
     * @throws Exception when the target cannot be reached or authentication fails
     */
    void check(Target target, int timeoutMs) throws Exception;
}
