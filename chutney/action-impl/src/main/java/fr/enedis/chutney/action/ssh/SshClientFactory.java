/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.ssh;

import static java.util.Collections.singletonList;

import fr.enedis.chutney.action.spi.injectable.Target;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.UserAuthFactory;
import org.apache.sshd.client.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;

public class SshClientFactory {

    public static String DEFAULT_TIMEOUT = "5 s";

    public static ClientSession buildSSHClientSession(Target target, long timeout) throws IOException {
        Connection connection = Connection.from(target);
        SshClient defaultClient = createDefaultClient();
        try {
            defaultClient.setUserAuthFactories(getAuthFactory(connection));
            ClientSession session = getConnectedSession(defaultClient, connection, timeout);

            session.auth().verify(timeout);
            return session;
        } catch (Exception e) {
            // The client owns i/o threads as soon as it is started: releasing it here keeps a refused
            // connection or a rejected authentication from leaking one on every attempt.
            defaultClient.stop();
            throw e;
        }
    }

    private static SshClient createDefaultClient() {
        SshClient defaultClient = SshClient.setUpDefaultClient();
        defaultClient.start();
        return defaultClient;
    }

    private static List<UserAuthFactory> getAuthFactory(Connection connection) {
        if (connection.usePrivateKey()) {
            return singletonList(UserAuthPublicKeyFactory.INSTANCE);
        }
        return singletonList(UserAuthPasswordFactory.INSTANCE);
    }

    private static ClientSession getConnectedSession(SshClient client, Connection connection, long timeout) throws IOException {
        // Bounded: an unanswered connect (a dropped packet rather than a refusal) would otherwise wait
        // forever and hold on to its thread, whatever timeout the caller asked for.
        ConnectFuture connectFuture = client.connect(connection.username, connection.serverHost, connection.serverPort).verify(timeout);
        return configureSessionAuthMethod(connectFuture.getSession(), connection);
    }

    private static ClientSession configureSessionAuthMethod(ClientSession session, Connection connection) {
        if (connection.usePrivateKey()) {
            FileKeyPairProvider provider = new FileKeyPairProvider(new File(connection.privateKey).toPath());
            provider.setPasswordFinder(FilePasswordProvider.of(connection.passphrase));
            session.setKeyIdentityProvider(provider);
        } else {
            session.addPasswordIdentity(connection.password);
        }
        return session;
    }

}
