/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.enedis.chutney.action.TestTarget;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.tools.SocketUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@DisplayName("MongoConnectionChecker")
class MongoConnectionCheckerIntegrationTest {

    private static final GenericContainer<?> mongoContainer =
        new GenericContainer<>(DockerImageName.parse("mongo:latest")).withExposedPorts(27017);

    private final MongoConnectionChecker checker = new MongoConnectionChecker();

    @BeforeAll
    static void start_mongo() {
        mongoContainer.start();
    }

    @AfterAll
    static void stop_mongo() {
        mongoContainer.stop();
    }

    @Nested
    @DisplayName("canHandle")
    class CanHandle {

        @Test
        void should_handle_mongodb_schemes() {
            assertThat(checker.canHandle(url("mongodb://localhost:27017"))).isTrue();
            assertThat(checker.canHandle(url("mongodb+srv://cluster.example.com"))).isTrue();
        }

        @Test
        void should_handle_the_documented_mongo_scheme() {
            // docs use mongo:// even though the driver builds a mongodb:// string
            assertThat(checker.canHandle(url("mongo://my.mongo.base:27017"))).isTrue();
        }

        @Test
        void should_handle_a_target_with_a_databaseName_property() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("mongo").withUrl("tcp://mongo:27017").withProperty("databaseName", "test").build();
            assertThat(checker.canHandle(target)).isTrue();
        }

        @Test
        void should_not_handle_a_plain_or_other_target() {
            assertThat(checker.canHandle(url("http://localhost"))).isFalse();
            assertThat(checker.canHandle(url("tcp://localhost:9092"))).isFalse();
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        void should_succeed_when_the_database_is_reachable() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("mongo")
                .withUrl("mongodb://" + mongoContainer.getHost() + ":" + mongoContainer.getFirstMappedPort())
                .withProperty("databaseName", "test")
                .build();

            Throwable thrown = catchThrowable(() -> checker.check(target, 5000));

            assertThat(thrown).isNull();
        }

        @Test
        void should_fail_when_the_database_is_unreachable() {
            Target target = TestTarget.TestTargetBuilder.builder()
                .withTargetId("mongo")
                .withUrl("mongodb://localhost:" + SocketUtils.findAvailableTcpPort())
                .withProperty("databaseName", "test")
                .build();

            assertThatThrownBy(() -> checker.check(target, 2000)).isInstanceOf(Exception.class);
        }
    }

    private static Target url(String url) {
        return TestTarget.TestTargetBuilder.builder().withTargetId("mongo").withUrl(url).build();
    }
}
