/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.ssh.check;

import fr.enedis.chutney.action.common.SilentLogger;
import fr.enedis.chutney.action.common.TargetProtocols;
import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.action.ssh.Connection;
import fr.enedis.chutney.action.ssh.sshj.SshJClient;
import java.util.List;
import java.util.Set;

/**
 * Probes an {@code ssh} target by connecting and authenticating exactly as the ssh command action
 * does — through the {@link SshJClient} sshj path, so the target's whole jump-host chain
 * ({@code proxy}, {@code proxy_1..N} with their own credentials) is traversed rather than bypassed.
 * A target reachable only through a bastion is therefore probed the same way it is really used, and a
 * successful connect+authenticate (over the last hop) is what proves it reachable. The connect is
 * bounded by the probe timeout so an unreachable host fails fast, and every hop is disconnected after.
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
        Connection connection = Connection.from(target);
        List<Connection> proxies = Connection.tunnelFrom(target);
        new SshJClient(connection, proxies, false, new SilentLogger()).connectAndAuthenticate(timeoutMs);
    }
}
