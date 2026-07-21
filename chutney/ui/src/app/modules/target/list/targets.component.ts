/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { Component, OnDestroy, OnInit } from '@angular/core';

import { Environment, Target, Authorization, TargetConnectionCheckResult } from '@model';
import { EnvironmentService, LoginService } from '@core/services';
import { distinct, filterOnTextContent, match } from '@shared/tools';
import { Subject, Subscription, takeUntil } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { ConnectionKind, reasonHintKey, reasonKind, reasonTitleKey } from '../connection-check.util';

type StatusKind = ConnectionKind | 'idle' | 'testing';

interface ConnectionCheck {
    testing: boolean;
    result: TargetConnectionCheckResult | null;
    checkedAt?: number;
}

const CHECKS_STORAGE_PREFIX = 'chutney.targets.connection-checks';

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

    private currentUserId = 'anonymous';
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
        this.loginService.getUser()
            .pipe(takeUntil(this.unsubscribeSub$))
            .subscribe(user => this.currentUserId = user?.id || 'anonymous');
        this.restoreChecks();
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

    testConnection(targetName: string, environmentName: string) {
        const key = this.checkKey(targetName, environmentName);
        this.connectionChecks.set(key, { testing: true, result: null });
        this.connectionSubscriptions.add(
            this.environmentService.checkTargetConnection(environmentName, targetName).subscribe({
                next: (result: TargetConnectionCheckResult) => this.storeCheck(key, result),
                error: error => this.storeCheck(key,
                    new TargetConnectionCheckResult('DOWN', 'UNREACHABLE', error?.error ?? error?.message ?? '', 0))
            })
        );
    }

    private storeCheck(key: string, result: TargetConnectionCheckResult) {
        this.connectionChecks.set(key, { testing: false, result, checkedAt: Date.now() });
        this.persistChecks();
    }

    /**
     * Results are kept in this browser only — they are never shared with other users. The key is
     * scoped to the signed-in user so that logging in as someone else on the same browser never
     * surfaces the previous user's checks.
     */
    private storageKey(): string {
        return `${CHECKS_STORAGE_PREFIX}.${this.currentUserId}`;
    }

    private persistChecks() {
        const snapshot = {};
        this.connectionChecks.forEach((check, key) => {
            if (check.result && check.checkedAt) {
                snapshot[key] = { result: check.result, checkedAt: check.checkedAt };
            }
        });
        try {
            localStorage.setItem(this.storageKey(), JSON.stringify(snapshot));
        } catch (e) {
            // storage unavailable (private mode / quota) — statuses simply do not survive the reload
        }
    }

    private restoreChecks() {
        try {
            const raw = localStorage.getItem(this.storageKey());
            if (!raw) {
                return;
            }
            const snapshot = JSON.parse(raw);
            Object.keys(snapshot).forEach(key => {
                const entry = snapshot[key];
                this.connectionChecks.set(key, { testing: false, result: entry.result, checkedAt: entry.checkedAt });
            });
        } catch (e) {
            // unreadable snapshot — start from a clean slate
        }
    }

    private ageLabel(checkedAt: number): string {
        const seconds = Math.max(0, Math.round((Date.now() - checkedAt) / 1000));
        if (seconds < 60) {
            return this.translateService.instant('admin.targets.connection.testedNow');
        }
        const minutes = Math.round(seconds / 60);
        const age = minutes < 60 ? `${minutes} min` : `${Math.round(minutes / 60)} h`;
        return this.translateService.instant('admin.targets.connection.checkedAgo', { age });
    }

    /** Probes every visible target on its active environment tab, so the status column fills in one click. */
    testAll() {
        this.targetsNames.forEach(targetName => {
            const environmentName = this.activeEnvTab(targetName);
            if (environmentName && !this.isTesting(targetName, environmentName)) {
                this.testConnection(targetName, environmentName);
            }
        });
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
        return check.result ? reasonKind(check.result.reason) : 'idle';
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
        const checkedAt = this.connectionChecks.get(this.checkKey(targetName, environmentName))?.checkedAt;
        if (checkedAt) {
            lines.push(this.ageLabel(checkedAt));
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
                this.targetsNames = distinct(this.targets.map(target => target.name));
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
        return this.environments.find(env =>
            this.targetFilter ? this.matchEnv(env, targetName): this.exist(targetName, env)
        ).name;
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
                this.targets = env.targets;
            }
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
