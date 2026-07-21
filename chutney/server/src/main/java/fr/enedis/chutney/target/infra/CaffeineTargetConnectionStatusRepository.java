/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.infra;

import static fr.enedis.chutney.config.ServerConfigurationValues.TARGET_CONNECTION_STATUS_TTL_UNIT_SPRING_VALUE;
import static fr.enedis.chutney.config.ServerConfigurationValues.TARGET_CONNECTION_STATUS_TTL_VALUE_SPRING_VALUE;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.enedis.chutney.target.domain.TargetConnectionStatus;
import fr.enedis.chutney.target.domain.TargetConnectionStatusRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * In-memory, expiring store of the last connectivity result per target.
 * <p>
 * Statuses are deliberately not persisted: they describe a point in time, so losing them on restart is
 * correct behaviour rather than a limitation. Entries expire after the configured time-to-live, which
 * bounds how stale a displayed status can be.
 */
@Repository
public class CaffeineTargetConnectionStatusRepository implements TargetConnectionStatusRepository {

    private final Cache<Key, TargetConnectionStatus> statuses;

    public CaffeineTargetConnectionStatusRepository(
        @Value(TARGET_CONNECTION_STATUS_TTL_VALUE_SPRING_VALUE) Integer ttlValue,
        @Value(TARGET_CONNECTION_STATUS_TTL_UNIT_SPRING_VALUE) String ttlUnit
    ) {
        this.statuses = Caffeine.newBuilder()
            .expireAfterWrite(ttlValue, TimeUnit.valueOf(ttlUnit))
            .build();
    }

    @Override
    public void save(TargetConnectionStatus status) {
        statuses.put(new Key(status.environmentName(), status.targetName()), status);
    }

    @Override
    public Optional<TargetConnectionStatus> find(String environmentName, String targetName) {
        return Optional.ofNullable(statuses.getIfPresent(new Key(environmentName, targetName)));
    }

    @Override
    public List<TargetConnectionStatus> findAll() {
        return new ArrayList<>(statuses.asMap().values());
    }

    @Override
    public void evict(String environmentName, String targetName) {
        statuses.invalidate(new Key(environmentName, targetName));
    }

    @Override
    public void evictEnvironment(String environmentName) {
        statuses.asMap().keySet().removeIf(key -> key.environmentName().equals(environmentName));
    }

    private record Key(String environmentName, String targetName) {
    }
}
