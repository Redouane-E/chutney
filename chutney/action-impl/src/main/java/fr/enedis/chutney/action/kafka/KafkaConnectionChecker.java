/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.kafka;

import static fr.enedis.chutney.action.kafka.KafkaClientFactoryHelper.filterMapFrom;
import static fr.enedis.chutney.action.kafka.KafkaClientFactoryHelper.resolveBootStrapServerConfig;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;
import static org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG;

import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;

/**
 * Probes a Kafka target with an {@link AdminClient} {@code describeCluster()} call, reusing the same
 * bootstrap-server resolution and security/SSL property passthrough the kafka actions use
 * ({@link KafkaClientFactoryHelper}, mirroring {@link ChutneyKafkaProducerFactory}). The request and
 * api timeouts are bounded so an unreachable broker fails fast.
 * <p>
 * Kafka targets have no dedicated URI scheme (they are typically {@code tcp://host:port}), so a
 * target is treated as Kafka when it uses a {@code kafka(s)://} scheme, declares a
 * {@code bootstrap.servers} property, or is explicitly tagged with a {@code protocol=kafka}
 * property. A plain {@code tcp://} target with none of these falls back to a raw TCP reachability
 * check rather than being mis-probed as Kafka.
 */
public class KafkaConnectionChecker implements TargetConnectionChecker {

    private static final String BOOTSTRAP_SERVERS_PROPERTY = "bootstrap.servers";
    private static final String PROTOCOL_PROPERTY = "protocol";
    private static final String KAFKA_PROTOCOL = "kafka";

    @Override
    public boolean canHandle(Target target) {
        if (KAFKA_PROTOCOL.equalsIgnoreCase(target.property(PROTOCOL_PROPERTY).orElse(""))) {
            return true;
        }
        String uri = target.rawUri();
        boolean kafkaScheme = uri != null && (startsWithIgnoreCase(uri, "kafka://") || startsWithIgnoreCase(uri, "kafkas://"));
        return kafkaScheme || target.property(BOOTSTRAP_SERVERS_PROPERTY).isPresent();
    }

    @Override
    public void check(Target target, int timeoutMs) throws Exception {
        Set<String> adminConfigKeys = AdminClientConfig.configDef().configKeys().keySet();
        Map<String, Object> config = new HashMap<>();
        config.put(BOOTSTRAP_SERVERS_CONFIG, resolveBootStrapServerConfig(target));
        config.putAll(filterMapFrom(adminConfigKeys, target.prefixedProperties("")));
        target.trustStore().ifPresent(trustStore -> {
            config.put(SSL_TRUSTSTORE_LOCATION_CONFIG, trustStore);
            target.trustStorePassword().ifPresent(trustStorePassword ->
                config.put(SSL_TRUSTSTORE_PASSWORD_CONFIG, trustStorePassword));
        });
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeoutMs);

        try (AdminClient adminClient = AdminClient.create(config)) {
            adminClient.describeCluster().nodes().get(timeoutMs, MILLISECONDS);
        }
    }
}
