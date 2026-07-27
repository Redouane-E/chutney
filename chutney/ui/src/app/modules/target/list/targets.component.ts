/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { Component, OnDestroy, OnInit } from '@angular/core';

import { Environment, Target, Authorization, TargetConnectionCheckEntry, TargetConnectionCheckResult } from '@model';
import { EnvironmentService, LoginService } from '@core/services';
import { distinct, filterOnTextContent, match } from '@shared/tools';
import { EMPTY, Subject, Subscription, from, takeUntil } from 'rxjs';
import { catchError, mergeMap, tap } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { ConnectionKind, reasonHintKey, reasonKind, reasonTitleKey } from '../connection-check.util';

type StatusKind = ConnectionKind | 'idle' | 'testing';

/** How many targets a bulk check probes at once, to keep one click from flooding the server. */
const BULK_CHECK_CONCURRENCY = 4;

interface ConnectionCheck {
    testing: boolean;
    result: TargetConnectionCheckResult | null;
    /** Age reported by the server, and the browser time it was received: together they track the
     *  real age without ever comparing the two clocks. */
    ageMsAtLoad?: number;
    receivedAt?: number;
    ttlMs?: number;
}

@Component({
    selector: 'chutney-targets',
    templateUrl: './targets.component.html',
    styleUrls: ['./targets.component.scss'],
    standalone: false
})
export class TargetsComponent implements OnInit, OnDestroy {

    errorMessage: string = null;
    environments: Environment[] = [];
    targetsNames: string[] = [];
    targets: Target[] = [];

    environmentFilter: Environment;
    targetFilter = '';

    isAuthorizedToWriteTargets: boolean = false;

    private readonly connectionChecks = new Map<string, ConnectionCheck>();
    private readonly selectedEnvTabs = new Map<string, string>();

    private environmentServiceSubscription: Subscription = null;
    private readonly connectionSubscriptions = new Subscription();
    private readonly unsubscribeSub$ = new Subject<void>();

    constructor(
        private environmentService: EnvironmentService,
        private loginService: LoginService,
        private translateService: TranslateService
    ) {
            this.isAuthorizedToWriteTargets = this.loginService.hasAuthorization(Authorization.TARGET_WRITE);
    }

    ngOnInit() {
        this.loadConnectionStatuses();
        this.loadTargets();
    }

    ngOnDestroy(): void {
        this.unsubscribeSub$.next();
        this.unsubscribeSub$.complete();
        this.environmentServiceSubscription?.unsubscribe();
        this.connectionSubscriptions.unsubscribe();
    }

    /** Environment tab currently displayed for a target row — the one the status dot refers to. */
    activeEnvTab(targetName: string): string {
        return this.selectedEnvTabs.get(targetName) ?? this.activeEnvironmentTab(targetName);
    }

    onEnvTabChange(targetName: string, event: { nextId: string }) {
        this.selectedEnvTabs.set(targetName, event.nextId);
    }

    /**
     * @param force true for an explicit single test — it must always re-probe, since the user has
     *              usually just changed something and needs the truth rather than a recent verdict.
     */
    testConnection(targetName: string, environmentName: string, force = true) {
        const key = this.checkKey(targetName, environmentName);
        this.connectionChecks.set(key, { ...this.connectionChecks.get(key), testing: true });
        this.connectionSubscriptions.add(this.checkTarget(targetName, environmentName, force).subscribe());
    }

    /**
     * Issues one check and folds its outcome into the map. A failed request says nothing about the
     * target, so the last known status is kept and the error is reported as an error — showing the
     * target as "down" would blame it for a problem with Chutney.
     */
    private checkTarget(targetName: string, environmentName: string, force: boolean) {
        const key = this.checkKey(targetName, environmentName);
        const previous = { ...this.connectionChecks.get(key), testing: false };
        return this.environmentService.checkTargetConnection(environmentName, targetName, force).pipe(
            tap({
                next: (entry: TargetConnectionCheckEntry) => this.connectionChecks.set(key, this.toCheck(entry)),
                error: error => {
                    this.connectionChecks.set(key, previous);
                    this.errorMessage = this.requestErrorMessage(error);
                }
            }),
            catchError(() => EMPTY)
        );
    }

    private requestErrorMessage(error: any): string {
        const detail = error?.error;
        if (typeof detail === 'string' && detail) {
            return detail;
        }
        return error?.message ?? this.translateService.instant('admin.targets.connection.checkFailed');
    }

    /**
     * A probe runs from the server, so its outcome is the same for everyone: the list starts from what
     * the instance already knows instead of blank, and shows how old each result is.
     */
    private loadConnectionStatuses() {
        this.environmentService.listTargetConnectionStatuses()
            .pipe(takeUntil(this.unsubscribeSub$))
            .subscribe({
                next: entries => entries.forEach(entry => {
                    const key = this.checkKey(entry.targetName, entry.environmentName);
                    // A check started while this was in flight is more current than what the server
                    // knew when it answered, so it wins.
                    if (!this.connectionChecks.get(key)?.testing) {
                        this.connectionChecks.set(key, this.toCheck(entry));
                    }
                }),
                error: () => {
                    // statuses are a convenience: failing to read them must not break the target list
                }
            });
    }

    private toCheck(entry: TargetConnectionCheckEntry): ConnectionCheck {
        return {
            testing: false,
            result: new TargetConnectionCheckResult(entry.status, entry.reason, entry.detail, entry.durationMs),
            ageMsAtLoad: entry.ageMs ?? 0,
            receivedAt: Date.now(),
            ttlMs: entry.ttlMs
        };
    }

    private ageLabel(check: ConnectionCheck): string {
        const seconds = Math.max(0, Math.round(this.currentAgeMs(check) / 1000));
        if (seconds < 60) {
            return this.translateService.instant('admin.targets.connection.testedNow');
        }
        const minutes = Math.floor(seconds / 60);
        const age = minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h`;
        return this.translateService.instant('admin.targets.connection.checkedAgo', { age });
    }

    /** Age of the result now: what the server reported, plus the time elapsed in this browser since. */
    private currentAgeMs(check: ConnectionCheck): number {
        return (check.ageMsAtLoad ?? 0) + (Date.now() - (check.receivedAt ?? Date.now()));
    }

    /**
     * Probes every visible target on its active environment tab, so the status column fills in one
     * click. Unforced on purpose: recent results are reused, so several users doing this at once do
     * not stampede the probed systems.
     */
    testAll() {
        const pending = this.targetsNames
            .map(targetName => ({ targetName, environmentName: this.activeEnvTab(targetName) }))
            .filter(({ targetName, environmentName }) => environmentName && !this.isTesting(targetName, environmentName));

        pending.forEach(({ targetName, environmentName }) =>
            this.connectionChecks.set(this.checkKey(targetName, environmentName),
                { ...this.connectionChecks.get(this.checkKey(targetName, environmentName)), testing: true }));

        // A few at a time: firing one request per target would tie up as many server threads as the
        // list is long, for every user pressing this at once.
        this.connectionSubscriptions.add(
            from(pending).pipe(
                mergeMap(({ targetName, environmentName }) => this.checkTarget(targetName, environmentName, false), BULK_CHECK_CONCURRENCY),
                takeUntil(this.unsubscribeSub$)
            ).subscribe()
        );
    }

    /** Test all only exists when the environment currently shown actually has targets to probe. */
    canTestAll(): boolean {
        return this.targetsNames.length > 0;
    }

    isTestingAll(): boolean {
        return this.targetsNames.some(targetName => this.isTesting(targetName, this.activeEnvTab(targetName)));
    }

    isTesting(targetName: string, environmentName: string): boolean {
        return this.connectionChecks.get(this.checkKey(targetName, environmentName))?.testing ?? false;
    }

    resultOf(targetName: string, environmentName: string): TargetConnectionCheckResult | null {
        return this.connectionChecks.get(this.checkKey(targetName, environmentName))?.result ?? null;
    }

    statusKind(targetName: string, environmentName: string): StatusKind {
        const check = this.connectionChecks.get(this.checkKey(targetName, environmentName));
        if (!check) {
            return 'idle';
        }
        if (check.testing) {
            return 'testing';
        }
        if (!check.result || this.isExpired(check)) {
            return 'idle';
        }
        return reasonKind(check.result.reason);
    }

    /**
     * The server forgets a status once its retention has passed. Honouring the same limit here keeps an
     * open page from showing a verdict the server — and a colleague who just reloaded — no longer has.
     */
    private isExpired(check: ConnectionCheck): boolean {
        return !!check.ttlMs && this.currentAgeMs(check) > check.ttlMs;
    }

    /** Small colored dot: green = up, red = down, grey = not testable / not tested. */
    statusDotClass(targetName: string, environmentName: string): string {
        return {
            ok: 'status-dot-up',
            bad: 'status-dot-down',
            info: 'status-dot-unknown',
            idle: 'status-dot-idle',
            testing: 'status-dot-idle'
        }[this.statusKind(targetName, environmentName)];
    }

    /** Short word shown next to the dot, so the status is readable without hovering. */
    statusLabel(targetName: string, environmentName: string): string {
        return {
            ok: 'admin.targets.connection.status.up',
            bad: 'admin.targets.connection.status.down',
            info: 'admin.targets.connection.status.notTestable',
            idle: 'admin.targets.connection.status.notTested',
            testing: 'admin.targets.connection.testing'
        }[this.statusKind(targetName, environmentName)];
    }

    /** True once a check has run — the label then carries extra detail available on hover. */
    hasDetail(targetName: string, environmentName: string): boolean {
        return !!this.resultOf(targetName, environmentName);
    }

    /** Everything the user needs, delivered on hover (and as the accessible label). */
    statusTooltip(targetName: string, environmentName: string): string {
        const kind = this.statusKind(targetName, environmentName);
        if (kind === 'testing') {
            return this.translateService.instant('admin.targets.connection.testing');
        }
        const result = this.resultOf(targetName, environmentName);
        if (!result) {
            return this.translateService.instant('admin.targets.connection.status.notTested');
        }
        const lines = [this.translateService.instant(reasonTitleKey(result.reason))];
        if (result.status === 'UP') {
            lines[0] += ` · ${result.durationMs} ${this.translateService.instant('admin.targets.connection.ms')}`;
        } else {
            const hint = this.translateService.instant(reasonHintKey(result.reason));
            if (hint) {
                lines.push(hint);
            }
            if (result.detail) {
                lines.push(result.detail);
            }
        }
        const check = this.connectionChecks.get(this.checkKey(targetName, environmentName));
        if (check?.receivedAt) {
            lines.push(this.ageLabel(check));
        }
        return lines.join('\n');
    }

    private checkKey(targetName: string, environmentName: string): string {
        return `${targetName}::${environmentName}`;
    }

    private loadTargets() {
        this.environmentServiceSubscription = this.environmentService.listTargets().subscribe({
            next: envs => {
                this.environments = envs;
                this.targets = envs.flatMap(env => env.targets).sort(this.targetSortFunction());
                // Scope the page to a single environment from the start: the UI then only ever shows
                // and acts on one selected environment. We take the first environment as-is — even if
                // it has no targets, in which case the list is empty and there is simply nothing to
                // test (Test / Test all are hidden); switching to an environment that has targets shows
                // them and their controls. filter() also honours any search typed while still loading.
                if (this.environments.length) {
                    this.filter(this.environments[0]);
                } else {
                    this.filter();
                }
            },
            error: error => this.errorMessage = error.error
        });
    }


    findTarget(targetName: string, environment: Environment): Target {
        return environment?.targets.find(target => target.name === targetName);
    }

    exist(targetName: string, environment: Environment): boolean {
        return !!this.findTarget(targetName, environment);
    }



    activeEnvironmentTab(targetName: string) : string{
        if (this.environmentFilter) {
            return this.environmentFilter.name;
        }
        // No match is possible while the list and the search box are momentarily out of step, so this
        // stays optional rather than throwing during rendering.
        return this.environments.find(env =>
            this.targetFilter ? this.matchEnv(env, targetName): this.exist(targetName, env)
        )?.name ?? this.environments[0]?.name ?? '';
    }

    private matchEnv(env: Environment, targetName) {
        return !!env.targets.find(target => target.name === targetName && this.match(target));
    }

    private filterByKeyword(targets: Target[]): Target[] {
        if (!!this.targetFilter) {
            return  targets.filter(target => this.match(target));
        }
        return targets;

    }

    filter(env: Environment = null) {
        if (env) {
            if (env.name === this.environmentFilter?.name) {
                this.environmentFilter = null;
                this.targets = this.environments.flatMap(e => e.targets);
            } else {
                this.environmentFilter = env;
                this.targets = [...env.targets];
            }
            // Per-row tab choices belong to the previous filtering: keeping them would show a status
            // for an environment the user is no longer looking at.
            this.selectedEnvTabs.clear();
            this.targets.sort(this.targetSortFunction());
        }

       this.targetsNames = distinct(this.filterByKeyword(this.targets).map(target => target.name));
    }

    private match(target: Target): boolean{
        return match(target.name, this.targetFilter) || match(target.url, this.targetFilter) ||
            filterOnTextContent(target.properties, this.targetFilter, ['key', 'value'])?.length;
    }

    private targetSortFunction(): (a: Target, b: Target) => number {
        return (t1, t2) => t1.name.localeCompare(t2.name);
    }
}
