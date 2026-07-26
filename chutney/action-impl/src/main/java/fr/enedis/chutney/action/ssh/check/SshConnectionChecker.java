/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.ssh.check;

import fr.enedis.chutney.action.common.TargetProtocols;
import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.action.ssh.SshClientFactory;
import java.util.Set;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.FactoryManager;

/**
 * Probes an {@code ssh} target by opening and authenticating a client session (password or private
 * key), reusing {@link SshClientFactory#buildSSHClientSession(Target, long)}. Reaching the end of a
 * successful call means the TCP connection and authentication both succeeded; the session and its
 * underlying client are released afterwards.
 */
public class SshConnectionChecker implements TargetConnectionChecker {

    private static final Set<String> ALIASES = Set.of("ssh", "sftp", "scp");

    /**
     * Recognised by an {@code ssh://} url or a {@code privateKey} property (only ssh targets carry
     * one). A password-only ssh target written as {@code tcp://host:port} is indistinguishable from a
     * plain tcp target, so it falls back to a reachability check unless tagged with
     * {@code protocol: ssh}.
     */
    @Override
    public boolean canHandle(Target target) {
        return TargetProtocols.matches(target, ALIASES,
            () -> TargetProtocols.uriStartsWith(target, "ssh://") || TargetProtocols.hasProperty(target, "privateKey"));
    }

    @Override
    public void check(Target target, int timeoutMs) throws Exception {
        ClientSession session = SshClientFactory.buildSSHClientSession(target, timeoutMs);
        FactoryManager factoryManager = session.getFactoryManager();
        try {
            // Connected and authenticated: the target is reachable.
        } finally {
            session.close(true);
            if (factoryManager instanceof SshClient client) {
                client.stop();
            }
        }
    }
}
