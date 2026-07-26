/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.jakarta;

import fr.enedis.chutney.action.common.BrokerConnectionUrls;
import fr.enedis.chutney.action.common.TargetProtocols;
import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import java.util.Locale;
import java.util.Set;
import javax.naming.Context;
import javax.naming.InitialContext;

/**
 * Probes a Jakarta messaging (ActiveMQ Artemis) target, the {@code jakarta.jms} counterpart of
 * {@link fr.enedis.chutney.action.jms.JmsConnectionChecker}: it resolves the connection factory
 * through JNDI from the target's {@code java.naming.*} properties and opens an authenticated
 * connection.
 * <p>
 * Recognised by a {@code java.naming.factory.initial} that names an Artemis context factory, or an
 * explicit {@code protocol} of {@code jakarta}/{@code artemis}.
 */
public class JakartaConnectionChecker implements TargetConnectionChecker {

    private static final Set<String> ALIASES = Set.of("jakarta", "artemis");
    static final String INITIAL_CONTEXT_FACTORY_PROPERTY = "java.naming.factory.initial";

    @Override
    public boolean canHandle(Target target) {
        return TargetProtocols.matches(target, ALIASES,
            () -> target.property(INITIAL_CONTEXT_FACTORY_PROPERTY)
                .map(factory -> factory.toLowerCase(Locale.ROOT).contains("artemis"))
                .orElse(false));
    }

    @Override
    public void check(Target target, int timeoutMs) throws Exception {
        java.util.Hashtable<String, String> environment = new java.util.Hashtable<>();
        // Bound the transport's blocking calls so a probe of an unreachable broker fails fast instead
        // of parking this worker thread on Artemis' generous default call timeout.
        environment.put(Context.PROVIDER_URL, BrokerConnectionUrls.boundedArtemis(target.uri().toString(), timeoutMs));
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
        } catch (jakarta.jms.JMSException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static Connection createConnection(ConnectionFactory factory) {
        try {
            return factory.createConnection();
        } catch (jakarta.jms.JMSException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
