/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.jms;

import fr.enedis.chutney.action.common.BrokerConnectionUrls;
import fr.enedis.chutney.action.common.TargetProtocols;
import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import java.util.Set;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;

/**
 * Probes a JMS (ActiveMQ classic) target by resolving its connection factory through JNDI and opening
 * an authenticated connection, mirroring how {@link JmsConnectionFactory} sets a probe up: the target
 * url becomes {@code Context.PROVIDER_URL}, its {@code java.naming.*} / {@code jndi.*} properties feed
 * the {@link InitialContext}, and {@code connectionFactoryName} (default {@code ConnectionFactory}) is
 * looked up. Opening the connection validates reachability and credentials.
 * <p>
 * A JMS target has no fixed url scheme — brokers use {@code tcp://}, {@code ssl://}, {@code vm://},
 * {@code failover:(...)} — so it is recognised by the {@code java.naming.factory.initial} property
 * every JMS target must carry, or an explicit {@code protocol} declaration.
 */
public class JmsConnectionChecker implements TargetConnectionChecker {

    private static final Set<String> ALIASES = Set.of("jms", "activemq");
    static final String INITIAL_CONTEXT_FACTORY_PROPERTY = "java.naming.factory.initial";

    @Override
    public boolean canHandle(Target target) {
        return TargetProtocols.matches(target, ALIASES,
            () -> TargetProtocols.hasProperty(target, INITIAL_CONTEXT_FACTORY_PROPERTY)
                && isClassic(target));
    }

    /** Distinguishes ActiveMQ classic from Artemis, which the jakarta checker handles. */
    private boolean isClassic(Target target) {
        return target.property(INITIAL_CONTEXT_FACTORY_PROPERTY)
            .map(factory -> !factory.toLowerCase(java.util.Locale.ROOT).contains("artemis"))
            .orElse(true);
    }

    @Override
    public void check(Target target, int timeoutMs) throws Exception {
        java.util.Hashtable<String, String> environment = new java.util.Hashtable<>();
        // Bound the transport so a probe of an unreachable broker fails fast instead of parking this
        // worker thread — a classic failover: url would otherwise reconnect for ever.
        environment.put(Context.PROVIDER_URL, BrokerConnectionUrls.boundedActiveMqClassic(target.uri().toString(), timeoutMs));
        environment.putAll(target.prefixedProperties("java.naming."));
        environment.putAll(target.prefixedProperties("jndi.", true));

        Context context = new InitialContext(environment);
        try {
            String factoryName = target.property("connectionFactoryName").orElse("ConnectionFactory");
            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup(factoryName);
            Connection connection = target.user()
                .map(user -> createConnection(connectionFactory, user, target.userPassword().orElse("")))
                .orElseGet(() -> createConnection(connectionFactory));
            try {
                connection.start();
            } finally {
                connection.close();
            }
        } finally {
            context.close();
        }
    }

    private static Connection createConnection(ConnectionFactory factory, String user, String password) {
        try {
            return factory.createConnection(user, password);
        } catch (javax.jms.JMSException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static Connection createConnection(ConnectionFactory factory) {
        try {
            return factory.createConnection();
        } catch (javax.jms.JMSException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
