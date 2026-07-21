/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.domain;

import fr.enedis.chutney.target.domain.TargetConnectionCheckResult.Reason;
import java.util.Locale;

/**
 * Maps the exception raised by a {@code TargetConnectionChecker} to a human-actionable
 * {@link Reason}. Matching is done on exception type <em>names</em> and message keywords rather than
 * on the concrete classes: the protocol drivers (rabbitmq, mongo, kafka, jdbc…) live in the
 * runtime-scoped {@code action-impl} module and cannot be compile-referenced here.
 */
final class TargetConnectionFailureClassifier {

    private TargetConnectionFailureClassifier() {
    }

    static Reason classify(Throwable throwable) {
        for (Throwable cause = throwable; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            String type = cause.getClass().getSimpleName();
            String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(Locale.ROOT);

            if (type.equals("UnknownHostException") || message.contains("failed to resolve")) {
                return Reason.UNKNOWN_HOST;
            }
            if (isAuthentication(type, message)) {
                return Reason.AUTH_FAILED;
            }
            if (isTls(type, message)) {
                return Reason.TLS_ERROR;
            }
            if (type.equals("ConnectException") || message.contains("connection refused")) {
                return Reason.CONNECTION_REFUSED;
            }
            if (type.contains("Timeout") || message.contains("timed out") || message.contains("timeout")) {
                return Reason.TIMEOUT;
            }
        }
        return Reason.UNREACHABLE;
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
            || message.contains("handshake")
            || message.contains("certificate");
    }
}
