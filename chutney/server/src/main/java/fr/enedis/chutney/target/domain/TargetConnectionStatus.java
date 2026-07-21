/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.domain;

import java.time.Instant;

/**
 * The last known connectivity result for a target, shared by the whole instance.
 * <p>
 * A probe is run by the server, so its outcome describes the server-to-target path and is the same
 * for every user: it is stored once here rather than in each browser. The timestamp is part of the
 * value so the UI can always disclose how old the result is — a status is never presented as live.
 *
 * @param environmentName the environment the target belongs to
 * @param targetName      the probed target
 * @param result          the outcome of that probe
 * @param checkedAt       when the probe ran
 */
public record TargetConnectionStatus(
    String environmentName,
    String targetName,
    TargetConnectionCheckResult result,
    Instant checkedAt
) {
}
