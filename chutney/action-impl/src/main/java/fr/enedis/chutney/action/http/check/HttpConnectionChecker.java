/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.http.check;

import static fr.enedis.chutney.action.common.SecurityUtils.buildSslContext;
import static java.nio.charset.StandardCharsets.UTF_8;

import fr.enedis.chutney.action.common.TargetProtocols;
import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;

/**
 * Probes an {@code http(s)} target with a {@code HEAD} request to its base url, honouring the same
 * TLS material as the http actions ({@code keyStore}/{@code trustStore}) and sending basic-auth when
 * the target carries credentials.
 * <p>
 * A transport failure (connection refused, timeout, TLS handshake failure) reports DOWN. Any HTTP
 * response otherwise means the host is reachable, with one exception: when the target carries
 * credentials and the server answers {@code 401}, the probe reports DOWN so a wrong username/password
 * is caught the same way it is for SQL and SSH. A {@code 401} on a target with no configured
 * credentials stays UP — the endpoint answered, and holding a token is not the probe's job.
 * <p>
 * {@code 403} deliberately counts as reachable: plenty of healthy services forbid their base url
 * while serving their real endpoints, so treating it as a failure would mark them down for a
 * configuration that works.
 * <p>
 * The client is created and closed per probe: unlike a scenario execution, a probe is a one-shot call,
 * and leaving its connection pool behind would leak a socket every time.
 */
public class HttpConnectionChecker implements TargetConnectionChecker {

    private static final Set<String> ALIASES = Set.of("http", "https");

    @Override
    public boolean canHandle(Target target) {
        return TargetProtocols.matches(target, ALIASES, () -> TargetProtocols.uriStartsWith(target, "http://", "https://"));
    }

    @Override
    public void check(Target target, int timeoutMs) throws Exception {
        try (CloseableHttpClient client = httpClient(target, timeoutMs)) {
            HttpHead request = new HttpHead(target.uri());
            basicAuthorization(target).ifPresent(header -> request.addHeader(HttpHeaders.AUTHORIZATION, header));

            client.execute(request, response -> {
                if (response.getCode() == 401 && target.user().isPresent()) {
                    throw new IllegalStateException("Authentication failed (HTTP 401)");
                }
                return null;
            });
        }
    }

    private CloseableHttpClient httpClient(Target target, int timeoutMs) throws Exception {
        SSLContext sslContext = buildSslContext(target).build();
        HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
            .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(timeoutMs, TimeUnit.MILLISECONDS).build())
            .build();
        return HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(timeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(timeoutMs))
                .build())
            .build();
    }

    private Optional<String> basicAuthorization(Target target) {
        return target.user().map(user -> {
            String credentials = user + ":" + target.userPassword().orElse("");
            return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(UTF_8));
        });
    }
}
