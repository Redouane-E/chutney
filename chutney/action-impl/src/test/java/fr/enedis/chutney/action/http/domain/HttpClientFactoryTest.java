/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.http.domain;

import static org.assertj.core.api.Assertions.assertThat;

import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.injectable.Target;
import java.util.Optional;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HttpClientFactory.proxyRoutePlanner")
class HttpClientFactoryTest {

    @Test
    void should_build_a_proxy_route_planner_from_the_target_proxy_property() {
        // the connectivity checker relies on this so a proxy-only target is probed through its proxy
        Target target = TestTarget.TestTargetBuilder.builder()
            .withTargetId("http").withUrl("http://service.internal")
            .withProperty("proxy", "http://proxy.local:3128").build();

        Optional<HttpRoutePlanner> planner = HttpClientFactory.proxyRoutePlanner(target);

        assertThat(planner).containsInstanceOf(DefaultProxyRoutePlanner.class);
    }

    @Test
    void should_ignore_a_malformed_proxy_url() {
        Target target = TestTarget.TestTargetBuilder.builder()
            .withTargetId("http").withUrl("http://service.internal")
            .withProperty("proxy", "not-a-valid-url").build();

        assertThat(HttpClientFactory.proxyRoutePlanner(target)).isEmpty();
    }
}
