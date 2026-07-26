/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.domain;

import fr.enedis.chutney.target.domain.TargetConnectionCheckResult.Reason;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps the exception raised by a {@code TargetConnectionChecker} to a human-actionable
 * {@link Reason}. Matching is done on exception type <em>names</em> and message keywords rather than
 * on the concrete classes: the protocol drivers (rabbitmq, mongo, kafka, jdbc…) live in the
 * runtime-scoped {@code action-impl} module and cannot be compile-referenced here.
 */
final class TargetConnectionFailureClassifier {

    /** Chains are short; this only guards against a cause cycle, which would otherwise spin forever. */
    private static final int MAX_CAUSE_DEPTH = 20;

    private TargetConnectionFailureClassifier() {
    }

    /**
     * Ranks reasons from most to least specific. A driver often wraps the real failure — a refused
     * connection or an unknown host — inside a generic timeout, so a more specific reason found deeper
     * in the chain is preferred over a timeout or a bare "unreachable" nearer the surface.
     */
    private static final List<Reason> SPECIFICITY = List.of(
        Reason.AUTH_FAILED, Reason.TLS_ERROR, Reason.UNKNOWN_HOST, Reason.CONNECTION_REFUSED, Reason.TIMEOUT);

    static Reason classify(Throwable throwable) {
        return mostSpecific(throwable)
            .map(Map.Entry::getValue)
            .orElse(Reason.UNREACHABLE);
    }

    /**
     * @return the cause {@link #classify} derived its reason from, so the detail shown to the user
     * describes the same failure as the headline. Falls back to the deepest cause.
     */
    static Throwable classifiedCause(Throwable throwable) {
        return mostSpecific(throwable)
            .map(Map.Entry::getKey)
            .orElseGet(() -> {
                List<Throwable> chain = causeChain(throwable);
                return chain.isEmpty() ? throwable : chain.get(chain.size() - 1);
            });
    }

    /** The (cause, reason) in the chain whose reason is the most specific; empty if none match. */
    private static Optional<Map.Entry<Throwable, Reason>> mostSpecific(Throwable throwable) {
        Map.Entry<Throwable, Reason> best = null;
        for (Throwable cause : causeChain(throwable)) {
            Reason reason = reasonOf(cause);
            if (reason != null && (best == null || SPECIFICITY.indexOf(reason) < SPECIFICITY.indexOf(best.getValue()))) {
                best = Map.entry(cause, reason);
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The causes from the outermost to the deepest, stopping on a repeat so a cyclic chain — which
     * some driver wrappers can build — cannot loop forever.
     */
    private static List<Throwable> causeChain(Throwable throwable) {
        List<Throwable> chain = new ArrayList<>();
        Throwable cause = throwable;
        while (cause != null && chain.size() < MAX_CAUSE_DEPTH && !containsIdentity(chain, cause)) {
            chain.add(cause);
            cause = cause.getCause();
        }
        return chain;
    }

    private static boolean containsIdentity(List<Throwable> chain, Throwable candidate) {
        return chain.stream().anyMatch(seen -> seen == candidate);
    }

    private static Reason reasonOf(Throwable cause) {
        String type = cause.getClass().getSimpleName();
        String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(Locale.ROOT);

        if (type.equals("UnknownHostException") || message.contains("failed to resolve")) {
            return Reason.UNKNOWN_HOST;
        }
        if (isAuthentication(type, message)) {
            return Reason.AUTH_FAILED;
        }
        if (type.equals("ConnectException") || message.contains("connection refused")) {
            return Reason.CONNECTION_REFUSED;
        }
        if (isTls(type, message)) {
            return Reason.TLS_ERROR;
        }
        if (type.contains("Timeout") || message.contains("timed out") || message.contains("timeout")) {
            return Reason.TIMEOUT;
        }
        return null;
    }

    private static boolean isAuthentication(String type, String message) {
        return type.contains("Authentication")
            || type.equals("MongoSecurityException")
            || message.contains("authentication failed")
            || message.contains("access denied")
            || message.contains("unauthorized")
            || message.contains("auth fail")
            || message.contains("bad credentials")
            || message.contains("requires authentication");
    }

    private static boolean isTls(String type, String message) {
        return type.contains("SSLHandshake")
            || type.contains("SSLException")
            || type.contains("CertificateException")
            || message.contains("pkix")
            || message.contains("unable to find valid certification path");
    }
}
