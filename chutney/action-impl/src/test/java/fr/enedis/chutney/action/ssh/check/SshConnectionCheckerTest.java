/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.ssh.check;

import static fr.enedis.chutney.action.ssh.fakes.FakeServerSsh.buildLocalSshServer;
import static fr.enedis.chutney.action.ssh.fakes.FakeTargetInfo.buildTargetWithPassword;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.action.ssh.fakes.FakeServerSsh;
import fr.enedis.chutney.action.ssh.fakes.HardcodedTarget;
import java.util.Map;
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SshConnectionChecker")
class SshConnectionCheckerTest {

    private static SshServer fakeSshServer;

    private final SshConnectionChecker checker = new SshConnectionChecker();

    @BeforeAll
    static void start_ssh_server() throws Exception {
        fakeSshServer = buildLocalSshServer();
        fakeSshServer.start();
    }

    @AfterAll
    static void stop_ssh_server() throws Exception {
        fakeSshServer.stop();
    }

    @Nested
    @DisplayName("canHandle")
    class CanHandle {

        @Test
        void should_handle_an_ssh_scheme() {
            assertThat(checker.canHandle(new HardcodedTarget(fakeSshServer, Map.of()))).isTrue();
        }

        @Test
        void should_handle_a_target_carrying_a_private_key() {
            // a private-key property is unique to ssh, so it identifies even a tcp:// ssh target
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("ssh").withUrl("tcp://host:22").withProperty("privateKey", "/keys/id_rsa").build();
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_handle_a_target_explicitly_tagged_as_ssh() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("ssh").withUrl("tcp://host:22").withProperty("protocol", "ssh").build();
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_not_handle_a_password_only_tcp_target() {
            // indistinguishable from a plain tcp target — falls back to a reachability check
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("x").withUrl("tcp://host:22").withProperty("password", "p").build();
            assertThat(checker.canHandle(target)).isFalse();
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        void should_succeed_with_valid_credentials() {
            // Given
            Target target = buildTargetWithPassword(fakeSshServer);

            // When
            Throwable thrown = catchThrowable(() -> checker.check(target, 5000));

            // Then
            assertThat(thrown).isNull();
        }

        @Test
        void should_fail_with_wrong_password() {
            // Given
            Target target = new HardcodedTarget(fakeSshServer, Map.of("user", "mockssh", "password", "wrong-password"));

            // When / Then
            assertThatThrownBy(() -> checker.check(target, 5000)).isInstanceOf(Exception.class);
        }

        @Test
        void should_reach_the_target_through_a_jump_host() throws Exception {
            // A target reachable only via a bastion must be probed through that bastion, exactly as the
            // ssh action connects — not by a direct connection that would report a false DOWN.
            SshServer proxy = FakeServerSsh.buildLocalProxy("proxySshUser", "proxySshPassword");
            try {
                proxy.start();
                String proxyUrl = "ssh://" + proxy.getHost() + ":" + proxy.getPort();
                Target target = buildTargetWithPassword(fakeSshServer, proxyUrl, "proxySshUser", "proxySshPassword");

                Throwable thrown = catchThrowable(() -> checker.check(target, 5000));

                assertThat(thrown).isNull();
            } finally {
                proxy.stop();
            }
        }

        @Test
        void should_fail_when_the_jump_host_credentials_are_wrong() throws Exception {
            // the probe authenticates each hop the way the action does — a bad bastion password fails it
            SshServer proxy = FakeServerSsh.buildLocalProxy("proxySshUser", "proxySshPassword");
            try {
                proxy.start();
                String proxyUrl = "ssh://" + proxy.getHost() + ":" + proxy.getPort();
                Target target = buildTargetWithPassword(fakeSshServer, proxyUrl, "proxySshUser", "wrong-proxy-password");

                assertThatThrownBy(() -> checker.check(target, 5000)).isInstanceOf(Exception.class);
            } finally {
                proxy.stop();
            }
        }
    }
}
