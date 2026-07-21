/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.common;

import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Generic reachability probe for {@code tcp://host:port} targets — Chutney's protocol-agnostic
 * scheme, where the actual protocol is decided by the action rather than the URL. Since the protocol
 * is unknown, this only opens a raw TCP connection: it confirms the host resolves and the port is
 * open (catching a wrong host/port, a down service or a firewall), but does NOT validate any
 * protocol handshake or credentials.
 */
public class TcpConnectionChecker implements TargetConnectionChecker {

    @Override
    public boolean canHandle(Target target) {
        String uri = target.rawUri();
        return uri != null && (startsWithIgnoreCase(uri, "tcp://") || startsWithIgnoreCase(uri, "tcps://"));
    }

    @Override
    public int priority() {
        // Generic fallback: a protocol-specific checker (e.g. Kafka on a tcp:// target that also
        // declares bootstrap.servers) must win over raw TCP reachability.
        return -1000;
    }

    @Override
    public void check(Target target, int timeoutMs) throws Exception {
        String host = target.host();
        int port = target.port();
        if (host == null || host.isBlank() || port < 0) {
            throw new IllegalArgumentException("A tcp target must define a host and a port (tcp://host:port)");
        }
        InetAddress address = InetAddress.getByName(host);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), timeoutMs);
        }
    }
}
