/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.domain;

import static org.assertj.core.api.Assertions.assertThat;

import fr.enedis.chutney.target.domain.TargetConnectionCheckResult.Reason;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TargetConnectionFailureClassifier")
class TargetConnectionFailureClassifierTest {

    @Test
    void should_classify_unknown_host() {
        assertThat(TargetConnectionFailureClassifier.classify(new UnknownHostException("db.example"))).isEqualTo(Reason.UNKNOWN_HOST);
    }

    @Test
    void should_classify_connection_refused() {
        assertThat(TargetConnectionFailureClassifier.classify(new ConnectException("Connection refused"))).isEqualTo(Reason.CONNECTION_REFUSED);
    }

    @Test
    void should_classify_timeout() {
        assertThat(TargetConnectionFailureClassifier.classify(new TimeoutException("timed out"))).isEqualTo(Reason.TIMEOUT);
    }

    @Test
    void should_classify_tls_error() {
        assertThat(TargetConnectionFailureClassifier.classify(new SSLHandshakeException("PKIX path building failed"))).isEqualTo(Reason.TLS_ERROR);
    }

    @Test
    void should_classify_authentication_from_message() {
        assertThat(TargetConnectionFailureClassifier.classify(new IllegalStateException("Authentication failed (HTTP 401)"))).isEqualTo(Reason.AUTH_FAILED);
    }

    @Test
    void should_classify_authentication_from_driver_exception_type() {
        assertThat(TargetConnectionFailureClassifier.classify(new AuthenticationFailureException())).isEqualTo(Reason.AUTH_FAILED);
    }

    @Test
    void should_unwrap_nested_causes() {
        Throwable wrapped = new RuntimeException("wrapper", new ConnectException("Connection refused"));
        assertThat(TargetConnectionFailureClassifier.classify(wrapped)).isEqualTo(Reason.CONNECTION_REFUSED);
    }

    @Test
    void should_prefer_a_specific_cause_over_a_generic_timeout_wrapper() {
        // a driver wraps the real failure (unknown host) inside a generic timeout — the actionable
        // reason is the host problem, not "timed out"
        Throwable wrapped = new TimeoutException("Timed out while selecting a server");
        wrapped.initCause(new UnknownHostException("db.does-not-exist"));
        assertThat(TargetConnectionFailureClassifier.classify(wrapped)).isEqualTo(Reason.UNKNOWN_HOST);
    }

    @Test
    void should_fall_back_to_unreachable_for_an_unrecognized_error() {
        assertThat(TargetConnectionFailureClassifier.classify(new RuntimeException("boom"))).isEqualTo(Reason.UNREACHABLE);
    }

    /** Simulates a driver-specific exception (e.g. RabbitMQ) matched by simple class name. */
    private static final class AuthenticationFailureException extends RuntimeException {
    }
}
