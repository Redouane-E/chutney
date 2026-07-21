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
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Probes whether a target is reachable with its configured URL and credentials.
 * <p>
 * The environment {@link TargetDto} is turned into the action-spi {@link Target} the connection
 * factories consume, then dispatched to the first {@link TargetConnectionChecker} that can handle
 * its protocol. Each probe runs on a bounded thread so an unreachable/hanging target reports DOWN
 * instead of blocking the request. When no checker matches the protocol, the result is UNKNOWN.
 * <p>
 * A probe runs from the server, so its outcome is the same for every user: results for saved targets
 * are recorded in a shared {@link TargetConnectionStatusRepository} instead of being kept per browser.
 * Un-forced checks reuse a recent result, which keeps several users pressing "test all" at once from
 * stampeding the probed systems.
 */
public class TargetConnectionCheckService {

    /**
     * Extra time granted to the bounded task on top of the per-checker timeout, so a checker's own
     * (more precise) timeout/exception surfaces before this hard cap cancels a truly hanging probe.
     */
    private static final long HARD_CAP_MARGIN_MS = 5000;

    /** How many checks may wait per worker before further ones are refused rather than queued. */
    private static final int QUEUE_DEPTH_PER_WORKER = 4;

    private final TargetApi targetApi;
    private final TargetConnectionStatusRepository statusRepository;
    private final List<TargetConnectionChecker> checkers;
    private final int timeoutMs;
    private final long throttleMs;
    private final long statusTtlMs;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, CompletableFuture<TargetConnectionStatus>> inFlightChecks = new ConcurrentHashMap<>();

    public TargetConnectionCheckService(TargetApi targetApi,
                                        TargetConnectionStatusRepository statusRepository,
                                        List<TargetConnectionChecker> checkers,
                                        int timeoutMs,
                                        long throttleMs,
                                        long statusTtlMs,
                                        int poolSize) {
        this.targetApi = targetApi;
        this.statusRepository = statusRepository;
        this.checkers = List.copyOf(checkers);
        this.timeoutMs = timeoutMs;
        this.throttleMs = throttleMs;
        this.statusTtlMs = statusTtlMs;
        // Bounded workers and a bounded queue: a burst of checks must neither open unlimited
        // connections nor pile up requests that hold web threads hostage. Once the queue is full the
        // extra work is refused immediately — a caller told to retry is far better than an application
        // whose threads are all waiting on probes.
        ThreadPoolExecutor probeExecutor = new ThreadPoolExecutor(
            poolSize, poolSize,
            0L, MILLISECONDS,
            new ArrayBlockingQueue<>(Math.max(poolSize, 1) * QUEUE_DEPTH_PER_WORKER),
            runnable -> {
                Thread thread = new Thread(runnable, "target-connection-check");
                thread.setDaemon(true);
                return thread;
            });
        probeExecutor.allowCoreThreadTimeOut(false);
        this.executor = probeExecutor;
    }

    /**
     * Probes a saved target, looked up by environment + name, and records the outcome for everyone.
     *
     * @param force when false, a result younger than the throttle window is reused instead of probing
     *              again — this is what "test all" uses. An explicit single test forces a fresh probe.
     */
    public TargetConnectionStatus check(String environmentName, String targetName, boolean force) {
        if (!force) {
            Optional<TargetConnectionStatus> fresh = statusRepository.find(environmentName, targetName)
                .filter(this::isFresh);
            if (fresh.isPresent()) {
                return fresh.get();
            }
        }
        return checkOnce(environmentName, targetName);
    }

    /**
     * Runs at most one probe per target at a time; simultaneous callers wait for the running one and
     * share its outcome.
     * <p>
     * This matters beyond saving work: several checkers authenticate, so duplicated probes of a target
     * whose stored password is wrong become a burst of failed logins — enough, on a hardened server, to
     * lock the account the probe was meant to validate.
     */
    private TargetConnectionStatus checkOnce(String environmentName, String targetName) {
        String key = environmentName + "/" + targetName;
        CompletableFuture<TargetConnectionStatus> promise = new CompletableFuture<>();
        CompletableFuture<TargetConnectionStatus> running = inFlightChecks.putIfAbsent(key, promise);
        if (running != null) {
            return join(running, environmentName, targetName);
        }
        try {
            // Read before the target itself, so an edit happening anywhere from here on is detected.
            long revision = statusRepository.revision(environmentName, targetName);
            TargetConnectionCheckResult result = check(targetApi.getTarget(environmentName, targetName));
            TargetConnectionStatus status = new TargetConnectionStatus(environmentName, targetName, result, Instant.now());
            // A probe takes seconds; if the target was edited or deleted meanwhile, this verdict
            // describes the previous definition and must not resurrect what invalidation removed. The
            // caller still gets its own answer — only the shared record is skipped.
            statusRepository.saveIfUnchanged(status, revision);
            promise.complete(status);
            return status;
        } catch (RuntimeException e) {
            promise.completeExceptionally(e);
            throw e;
        } finally {
            inFlightChecks.remove(key, promise);
        }
    }

    private TargetConnectionStatus join(CompletableFuture<TargetConnectionStatus> running,
                                        String environmentName, String targetName) {
        try {
            return running.get(timeoutMs + HARD_CAP_MARGIN_MS, MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return busy(environmentName, targetName);
        } catch (ExecutionException | TimeoutException e) {
            // The probe we joined failed or outlived our patience; report it as unfinished rather than
            // as a verdict about the target, and leave the running probe to record the real outcome.
            return busy(environmentName, targetName);
        }
    }

    private TargetConnectionStatus busy(String environmentName, String targetName) {
        return new TargetConnectionStatus(environmentName, targetName,
            TargetConnectionCheckResult.down(TargetConnectionCheckResult.Reason.UNREACHABLE,
                "A connection check for this target is already running, please retry", 0),
            Instant.now());
    }

    /**
     * @return the last known status of every target still within the retention window.
     */
    public List<TargetConnectionStatus> lastStatuses() {
        return statusRepository.findAll();
    }

    /**
     * @return how long a recorded status is kept, so clients can stop showing one exactly when the
     * server forgets it.
     */
    public long statusTtlMs() {
        return statusTtlMs;
    }

    /**
     * Releases the probe workers. Spring calls this when the context closes (it infers {@code close}
     * as the destroy method of a bean). Threads are daemons, so a running server does not depend on
     * it — but a context rebuilt in place, as tests and reloads do, would otherwise leave a pool
     * behind every time.
     */
    public void close() {
        executor.shutdownNow();
    }

    /**
     * A result counts as fresh only if it is genuinely recent. A negative age means the clock stepped
     * backwards (ntp correction, restored snapshot); treating that as fresh would freeze stale results
     * in place for as long as the skew lasts, so it is rejected instead.
     */
    private boolean isFresh(TargetConnectionStatus status) {
        long ageMs = Duration.between(status.checkedAt(), Instant.now()).toMillis();
        return ageMs >= 0 && ageMs < throttleMs;
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
        // The probe may wait for a free worker when many targets are checked at once. That wait must
        // not count against the target: charging queue time to the probe would report healthy targets
        // as timed out — and such a verdict would then be shared with everyone. So the clock starts
        // when the probe actually begins, and only a probe that really started can time out.
        CountDownLatch started = new CountDownLatch(1);
        AtomicLong startNanos = new AtomicLong();
        Future<?> future;
        try {
            future = executor.submit(() -> {
                startNanos.set(System.nanoTime());
                started.countDown();
                checker.check(target, timeoutMs);
                return null;
            });
        } catch (RejectedExecutionException e) {
            // Saturated: answer at once instead of holding this request — and say nothing about the
            // target, which was never contacted.
            return TargetConnectionCheckResult.down(
                TargetConnectionCheckResult.Reason.UNREACHABLE,
                "Too many connection checks running, please retry",
                0);
        }

        long probeBudgetMs = timeoutMs + HARD_CAP_MARGIN_MS;
        try {
            if (!started.await(queueBudgetMs(), MILLISECONDS)) {
                future.cancel(true);
                return TargetConnectionCheckResult.down(
                    TargetConnectionCheckResult.Reason.UNREACHABLE,
                    "Server busy: the connection check could not be started, please retry",
                    0);
            }
            future.get(probeBudgetMs, MILLISECONDS);
            return TargetConnectionCheckResult.up(elapsedMs(startNanos.get()));
        } catch (TimeoutException e) {
            future.cancel(true);
            return TargetConnectionCheckResult.down(
                TargetConnectionCheckResult.Reason.TIMEOUT,
                "Connection timed out after " + timeoutMs + " ms",
                elapsedMs(startNanos.get()));
        } catch (ExecutionException e) {
            return TargetConnectionCheckResult.down(
                TargetConnectionFailureClassifier.classify(e.getCause()),
                describe(e.getCause()),
                elapsedMs(startNanos.get()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TargetConnectionCheckResult.down(
                TargetConnectionCheckResult.Reason.UNREACHABLE,
                "Connection check was interrupted",
                elapsedMs(startNanos.get()));
        }
    }

    /**
     * How long a probe may wait for a free worker before the caller gives up. Generous, since waiting
     * says nothing about the target, but bounded so a request never hangs.
     */
    private long queueBudgetMs() {
        return (long) (timeoutMs + HARD_CAP_MARGIN_MS) * 3;
    }

    private static long elapsedMs(long startNanos) {
        return startNanos == 0 ? 0 : (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * Builds a short, non-sensitive failure detail. It describes the cause the reason was classified
     * from — reporting a different one would contradict the headline the user reads — and redacts any
     * credential a driver may have embedded in its message.
     */
    private static String describe(Throwable cause) {
        Throwable described = TargetConnectionFailureClassifier.classifiedCause(cause);
        String message = described.getMessage();
        String type = described.getClass().getSimpleName();
        return (message == null || message.isBlank()) ? type : type + ": " + redactSecrets(message);
    }

    /**
     * Drivers put credentials in their error messages in many shapes, so redaction errs on the side of
     * hiding: any {@code key=value} or {@code key: value} whose key looks secret, quoted or not, and
     * any URL user-info — including a token-only one, which carries no colon.
     */
    private static String redactSecrets(String message) {
        String secretKey = "password|passwd|pwd|passphrase|secret|token|credential[s]?|auth|api[_-]?key|access[_-]?key|private[_-]?key";
        return message
            // url user-info with a password: scheme://user:password@host. The password is matched
            // greedily up to the last '@' of the run: an unencoded '@' or '/' inside a password is a
            // common mistake, and stopping at the first '@' would leave most of it in clear.
            .replaceAll("(?i)(://[^:/@\\s]+):[^\\s]*@", "$1:***@")
            // url user-info carrying only a token: scheme://token@host
            .replaceAll("(?i)(://)[^:/@\\s]+@", "$1***@")
            // quoted values: password="s3cr3t" / password:'s3cr3t' (the usual kafka/jaas rendering)
            .replaceAll("(?i)(" + secretKey + ")(\\s*[=:]\\s*)([\"'])[^\"']*\\3", "$1$2$3***$3")
            // bare values, = or : separated
            .replaceAll("(?i)(" + secretKey + ")(\\s*[=:]\\s*)[^\\s;,&\"']+", "$1$2***")
            // http basic-auth headers — only what looks like encoded credential material, so prose
            // such as "Bearer token has expired" keeps the word that explains the failure.
            .replaceAll("(?i)\\b(basic|bearer)\\s+(?![A-Za-z]+\\b)([A-Za-z0-9+/=._-]{16,})", "$1 ***");
    }

}
