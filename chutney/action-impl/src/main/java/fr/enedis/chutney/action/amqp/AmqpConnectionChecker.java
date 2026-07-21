/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.amqp;

import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;

/**
 * Probes an {@code amqp(s)} target by opening a connection and a channel, reusing the same
 * {@link ConnectionFactoryFactory} the amqp actions use (credentials, vhost, TLS). Opening the
 * connection performs the full AMQP handshake including SASL authentication and vhost access, so a
 * wrong username/password or vhost raises {@code AuthenticationFailureException} and the probe
 * reports DOWN. The connection/handshake timeouts are bounded so an unreachable broker fails fast.
 */
public class AmqpConnectionChecker implements TargetConnectionChecker {

    @Override
    public boolean canHandle(Target target) {
        String uri = target.rawUri();
        return uri != null && (startsWithIgnoreCase(uri, "amqp://") || startsWithIgnoreCase(uri, "amqps://"));
    }

    @Override
    public void check(Target target, int timeoutMs) throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setConnectionTimeout(timeoutMs);
        connectionFactory.setHandshakeTimeout(timeoutMs);
        try (Connection connection = new ConnectionFactoryFactory(connectionFactory).newConnection(target);
             Channel channel = connection.createChannel()) {
            // Reaching here means the broker is reachable, authenticated and usable.
        }
    }
}
