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

    static Reason classify(Throwable throwable) {
        for (Throwable cause : causeChain(throwable)) {
            Reason reason = reasonOf(cause);
            if (reason != null) {
                return reason;
            }
        }
        return Reason.UNREACHABLE;
    }

    /**
     * @return the cause {@link #classify} derived its reason from, so the detail shown to the user
     * describes the same failure as the headline. Falls back to the deepest cause.
     */
    static Throwable classifiedCause(Throwable throwable) {
        List<Throwable> chain = causeChain(throwable);
        for (Throwable cause : chain) {
            if (reasonOf(cause) != null) {
                return cause;
            }
        }
        return chain.isEmpty() ? throwable : chain.get(chain.size() - 1);
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
