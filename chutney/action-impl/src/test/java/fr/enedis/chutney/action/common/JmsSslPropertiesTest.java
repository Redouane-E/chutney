/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.common;

import static org.assertj.core.api.Assertions.assertThat;

import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.injectable.Target;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JmsSslProperties")
class JmsSslPropertiesTest {

    @Test
    void should_map_the_targets_tls_material_into_the_jndi_environment() {
        Target target = TestTarget.TestTargetBuilder.builder()
            .withTargetId("jms").withUrl("ssl://broker:61617")
            .withProperty("keyStore", "/certs/client.p12")
            .withProperty("keyStorePassword", "kspass")
            .withProperty("keyPassword", "keypass")
            .withProperty("trustStore", "/certs/ca.p12")
            .withProperty("trustStorePassword", "tspass")
            .build();
        Map<String, String> environment = new HashMap<>();

        JmsSslProperties.putInto(environment, target);

        assertThat(environment)
            .containsEntry("connection.ConnectionFactory.keyStore", "/certs/client.p12")
            .containsEntry("connection.ConnectionFactory.keyStorePassword", "kspass")
            .containsEntry("connection.ConnectionFactory.keyStoreKeyPassword", "keypass")
            .containsEntry("connection.ConnectionFactory.trustStore", "/certs/ca.p12")
            .containsEntry("connection.ConnectionFactory.trustStorePassword", "tspass");
    }

    @Test
    void should_add_nothing_for_a_plain_broker_without_tls_material() {
        Target target = TestTarget.TestTargetBuilder.builder()
            .withTargetId("jms").withUrl("tcp://broker:61616").build();
        Map<String, String> environment = new HashMap<>();

        JmsSslProperties.putInto(environment, target);

        assertThat(environment).isEmpty();
    }
}
