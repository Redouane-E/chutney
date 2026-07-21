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
 * <p>
 * A revision is kept alongside each status and bumped on eviction. It lets a probe that started before
 * a target was edited discover, when it finishes, that its verdict no longer describes that target.
 */
@Repository
public class CaffeineTargetConnectionStatusRepository implements TargetConnectionStatusRepository {

    private final Cache<Key, TargetConnectionStatus> statuses;
    private final Cache<Key, Long> revisions;

    public CaffeineTargetConnectionStatusRepository(
        @Value(TARGET_CONNECTION_STATUS_TTL_VALUE_SPRING_VALUE) Integer ttlValue,
        @Value(TARGET_CONNECTION_STATUS_TTL_UNIT_SPRING_VALUE) String ttlUnit
    ) {
        this.statuses = Caffeine.newBuilder()
            .expireAfterWrite(ttlValue, TimeUnit.valueOf(ttlUnit))
            .build();
        // Same retention: a revision only has to outlive a probe, which is orders of magnitude shorter.
        this.revisions = Caffeine.newBuilder()
            .expireAfterWrite(ttlValue, TimeUnit.valueOf(ttlUnit))
            .build();
    }

    @Override
    public void save(TargetConnectionStatus status) {
        statuses.put(key(status), status);
    }

    @Override
    public boolean saveIfUnchanged(TargetConnectionStatus status, long expectedRevision) {
        Key key = key(status);
        // compute() holds the same per-key lock as invalidate(), so a save cannot slip between an
        // eviction's revision bump and its invalidation.
        TargetConnectionStatus written = statuses.asMap().compute(key,
            (k, current) -> revision(k) == expectedRevision ? status : current);
        return written == status;
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
        Key key = new Key(environmentName, targetName);
        bump(key);
        statuses.invalidate(key);
    }

    @Override
    public void evictEnvironment(String environmentName) {
        statuses.asMap().keySet().stream()
            .filter(key -> key.environmentName().equals(environmentName))
            .toList()
            .forEach(this::bump);
        statuses.asMap().keySet().removeIf(key -> key.environmentName().equals(environmentName));
    }

    @Override
    public long revision(String environmentName, String targetName) {
        return revision(new Key(environmentName, targetName));
    }

    private long revision(Key key) {
        return Optional.ofNullable(revisions.getIfPresent(key)).orElse(0L);
    }

    /** Bumped before the status is invalidated, so an in-flight probe can never win the race. */
    private void bump(Key key) {
        revisions.asMap().merge(key, 1L, Long::sum);
    }

    private Key key(TargetConnectionStatus status) {
        return new Key(status.environmentName(), status.targetName());
    }

    private record Key(String environmentName, String targetName) {
    }
}
