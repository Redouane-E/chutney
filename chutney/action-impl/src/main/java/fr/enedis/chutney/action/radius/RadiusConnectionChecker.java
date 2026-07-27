/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.radius;

import static fr.enedis.chutney.action.radius.RadiusHelper.createRadiusClient;

import fr.enedis.chutney.action.common.TargetProtocols;
import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import java.util.Set;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.util.RadiusClient;
import org.tinyradius.util.RadiusException;

/**
 * Probes a RADIUS target over UDP, reusing {@link RadiusHelper#createRadiusClient(Target)} (host,
 * {@code sharedSecret}, {@code authenticatePort}). RADIUS is connectionless, so the check sends a
 * probe Access-Request and waits for any reply: a reachable server answers — Accept <em>or</em>
 * Reject — which proves the host, port and shared secret; total silence within the timeout means the
 * server is unreachable or the shared secret is wrong (an unverifiable reply is dropped).
 * <p>
 * A RADIUS target carries a {@code sharedSecret} property and an {@code authenticatePort}; it is
 * recognised by those, a {@code udp://} url, or an explicit {@code protocol} declaration. The probe
 * does not prove any end-user credential — those are action inputs, not target configuration.
 */
public class RadiusConnectionChecker implements TargetConnectionChecker {

    private static final Set<String> ALIASES = Set.of("radius", "udp");

    @Override
    public boolean canHandle(Target target) {
        return TargetProtocols.matches(target, ALIASES,
            () -> TargetProtocols.uriStartsWith(target, "udp://")
                || TargetProtocols.hasProperty(target, "sharedSecret"));
    }

    @Override
    public void check(Target target, int timeoutMs) throws Exception {
        RadiusClient client = createRadiusClient(target);
        try {
            // Keep the actions' retransmission behaviour (tinyradius default: 3 attempts) so a single
            // dropped UDP datagram — normal on a lossy network — does not read as unreachable. The
            // attempts share the probe budget instead of the library's generous 3s-per-attempt default.
            int retries = 3;
            client.setRetryCount(retries);
            client.setSocketTimeout(Math.max(1, timeoutMs / retries));
            // A probe Access-Request with a throwaway password: the client library rejects an empty
            // one, and the value is irrelevant — the server's reply (Accept/Reject, or a shared-secret
            // mismatch surfacing as an exception) proves reachability; no reply means unreachable.
            client.authenticate(new AccessRequest("chutney-connectivity-probe", "chutney-connectivity-probe"));
        } catch (RadiusException e) {
            // The server answered but the reply could not be verified — the shared secret is wrong.
            // That is a target-credential problem, reported like any other authentication failure.
            throw new IllegalStateException("Authentication failed: invalid RADIUS shared secret (" + e.getMessage() + ")", e);
        } finally {
            client.close();
        }
    }
}
