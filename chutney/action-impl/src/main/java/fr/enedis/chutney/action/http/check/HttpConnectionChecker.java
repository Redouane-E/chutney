/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.http.check;

import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

import fr.enedis.chutney.action.common.SilentLogger;
import fr.enedis.chutney.action.http.domain.HttpClient;
import fr.enedis.chutney.action.http.domain.HttpClientFactory;
import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Probes an {@code http(s)} target by issuing a {@code HEAD} request to its base URL, reusing the
 * same {@link HttpClientFactory} the http actions use (basic-auth, TLS one/two-way, proxy).
 * <p>
 * A transport failure (connection refused, timeout, TLS handshake failure) raises an exception, so
 * it reports DOWN. Any HTTP response otherwise means the host is reachable (UP) — the factory
 * installs a no-op error handler so non-2xx does not throw. The one exception: when the target
 * carries credentials and they are rejected ({@code 401}/{@code 403}), the probe reports DOWN, so a
 * wrong username/password is caught the same way it is for SQL and SSH. A {@code 401}/{@code 403} on
 * a target with no configured credentials stays UP (the endpoint is reachable; holding a token is
 * not the probe's job).
 */
public class HttpConnectionChecker implements TargetConnectionChecker {

    @Override
    public boolean canHandle(Target target) {
        String uri = target.rawUri();
        return uri != null && (startsWithIgnoreCase(uri, "http://") || startsWithIgnoreCase(uri, "https://"));
    }

    @Override
    public void check(Target target, int timeoutMs) {
        HttpClient httpClient = new HttpClientFactory().create(new SilentLogger(), target, String.class, timeoutMs);
        ResponseEntity<String> response = httpClient.call(HttpMethod.HEAD, "", new HttpEntity<>(null, new HttpHeaders()));
        int status = response.getStatusCode().value();
        if ((status == 401 || status == 403) && target.user().isPresent()) {
            throw new IllegalStateException("Authentication failed (HTTP " + status + ")");
        }
    }
}
