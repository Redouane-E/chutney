/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.domain;

/**
 * Outcome of a target connectivity probe.
 *
 * @param status     UP when reachable, DOWN when not, UNKNOWN when the protocol cannot be probed
 * @param reason     the machine-readable category the UI turns into a human headline + fix hint
 * @param detail     the raw (sanitized) technical detail, shown behind "Show details" (null on success)
 * @param durationMs how long the probe took, in milliseconds (0 for UNKNOWN)
 */
public record TargetConnectionCheckResult(Status status, Reason reason, String detail, long durationMs) {

    public enum Status {
        UP, DOWN, UNKNOWN
    }

    /**
     * Failure/success categories. Kept in sync with the frontend {@code admin.targets.connection.reason.*}
     * i18n keys.
     */
    public enum Reason {
        OK,
        UNKNOWN_HOST,
        CONNECTION_REFUSED,
        TIMEOUT,
        AUTH_FAILED,
        TLS_ERROR,
        UNREACHABLE,
        PROTOCOL_NOT_SUPPORTED
    }

    public static TargetConnectionCheckResult up(long durationMs) {
        return new TargetConnectionCheckResult(Status.UP, Reason.OK, null, durationMs);
    }

    public static TargetConnectionCheckResult down(Reason reason, String detail, long durationMs) {
        return new TargetConnectionCheckResult(Status.DOWN, reason, detail, durationMs);
    }

    public static TargetConnectionCheckResult notTestable() {
        return new TargetConnectionCheckResult(Status.UNKNOWN, Reason.PROTOCOL_NOT_SUPPORTED, null, 0);
    }
}
