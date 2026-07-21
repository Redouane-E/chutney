/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.domain;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import fr.enedis.chutney.action.spi.TargetConnectionChecker;
import fr.enedis.chutney.action.spi.injectable.Target;
import fr.enedis.chutney.engine.domain.environment.TargetImpl;
import fr.enedis.chutney.environment.api.target.TargetApi;
import fr.enedis.chutney.environment.api.target.dto.TargetDto;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/**
 * Probes whether a target is reachable with its configured URL and credentials.
 * <p>
 * The environment {@link TargetDto} is turned into the action-spi {@link Target} the connection
 * factories consume, then dispatched to the first {@link TargetConnectionChecker} that can handle
 * its protocol. Each probe runs on a bounded thread so an unreachable/hanging target reports DOWN
 * instead of blocking the request. When no checker matches the protocol, the result is UNKNOWN.
 */
public class TargetConnectionCheckService {

    /**
     * Extra time granted to the bounded task on top of the per-checker timeout, so a checker's own
     * (more precise) timeout/exception surfaces before this hard cap cancels a truly hanging probe.
     */
    private static final long HARD_CAP_MARGIN_MS = 5000;

    private final TargetApi targetApi;
    private final List<TargetConnectionChecker> checkers;
    private final int timeoutMs;
    private final ExecutorService executor;

    public TargetConnectionCheckService(TargetApi targetApi, List<TargetConnectionChecker> checkers, int timeoutMs) {
        this.targetApi = targetApi;
        this.checkers = List.copyOf(checkers);
        this.timeoutMs = timeoutMs;
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "target-connection-check");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Probes a saved target, looked up by environment + name (used by the target list).
     */
    public TargetConnectionCheckResult check(String environmentName, String targetName) {
        return check(targetApi.getTarget(environmentName, targetName));
    }

    /**
     * Probes the given target definition directly — used to test the values currently being edited,
     * before they are saved.
     */
    public TargetConnectionCheckResult check(TargetDto targetDto) {
        Target target = TargetImpl.builder()
            .withName(targetDto.name)
            .withUrl(targetDto.url)
            .withProperties(targetDto.propertiesToMap())
            .build();

        Optional<TargetConnectionChecker> checker = findChecker(target);
        if (checker.isEmpty()) {
            return TargetConnectionCheckResult.notTestable();
        }
        return runBounded(checker.get(), target);
    }

    private Optional<TargetConnectionChecker> findChecker(Target target) {
        return checkers.stream()
            .filter(checker -> canHandleQuietly(checker, target))
            .max(Comparator.comparingInt(TargetConnectionChecker::priority));
    }

    private boolean canHandleQuietly(TargetConnectionChecker checker, Target target) {
        try {
            return checker.canHandle(target);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private TargetConnectionCheckResult runBounded(TargetConnectionChecker checker, Target target) {
        long startNanos = System.nanoTime();
        Future<?> future = executor.submit(() -> {
            checker.check(target, timeoutMs);
            return null;
        });
        try {
            future.get(timeoutMs + HARD_CAP_MARGIN_MS, MILLISECONDS);
            return TargetConnectionCheckResult.up(elapsedMs(startNanos));
        } catch (TimeoutException e) {
            future.cancel(true);
            return TargetConnectionCheckResult.down(
                TargetConnectionCheckResult.Reason.TIMEOUT,
                "Connection timed out after " + timeoutMs + " ms",
                elapsedMs(startNanos));
        } catch (ExecutionException e) {
            return TargetConnectionCheckResult.down(
                TargetConnectionFailureClassifier.classify(e.getCause()),
                describe(e.getCause()),
                elapsedMs(startNanos));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TargetConnectionCheckResult.down(
                TargetConnectionCheckResult.Reason.UNREACHABLE,
                "Connection check was interrupted",
                elapsedMs(startNanos));
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * Builds a short, non-sensitive failure detail from the root cause. Only the exception type and
     * message are exposed, and any credential a driver may have embedded in its message (URL
     * user-info, {@code password=}/{@code token=} query params) is redacted.
     */
    private static String describe(Throwable cause) {
        Throwable root = rootCause(cause);
        String message = root.getMessage();
        String type = root.getClass().getSimpleName();
        return (message == null || message.isBlank()) ? type : type + ": " + redactSecrets(message);
    }

    private static String redactSecrets(String message) {
        return message
            // URL user-info: scheme://user:password@host -> scheme://user:***@host
            .replaceAll("(?i)(://[^:/@\\s]+):[^@/\\s]+@", "$1:***@")
            // key/value secrets: password=..., pwd=..., token=..., secret=..., apikey=...
            .replaceAll("(?i)(password|passwd|pwd|secret|token|api[_-]?key)=[^\\s;,&\"']+", "$1=***");
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
