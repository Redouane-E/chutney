/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.api.dto;

import fr.enedis.chutney.target.domain.TargetConnectionStatus;
import java.time.Duration;
import java.time.Instant;

/**
 * The last known connectivity result of a saved target.
 * <p>
 * The freshness is reported as an <em>age</em> rather than a timestamp, together with the retention
 * the server applies: a client can then display how old a result is, and stop displaying it exactly
 * when the server would, using only its own clock. Sending an absolute instant instead would make
 * both depend on the browser's clock agreeing with the server's, which it often does not.
 *
 * @param ageMs how long ago the probe ran, in milliseconds
 * @param ttlMs how long a result is kept before the server forgets it
 */
public record TargetConnectionStatusDto(
    String environmentName,
    String targetName,
    String status,
    String reason,
    String detail,
    long durationMs,
    long ageMs,
    long ttlMs
) {

    public static TargetConnectionStatusDto from(TargetConnectionStatus status, long ttlMs) {
        return new TargetConnectionStatusDto(
            status.environmentName(),
            status.targetName(),
            status.result().status().name(),
            status.result().reason().name(),
            status.result().detail(),
            status.result().durationMs(),
            ageMs(status.checkedAt()),
            ttlMs);
    }

    private static long ageMs(Instant checkedAt) {
        return Math.max(0, Duration.between(checkedAt, Instant.now()).toMillis());
    }
}
