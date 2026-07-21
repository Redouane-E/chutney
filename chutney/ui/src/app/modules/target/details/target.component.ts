/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Target, TargetConnectionCheckResult } from '@model';
import { ValidationService } from '../../../molecules/validation/validation.service';
import { EnvironmentService } from '@core/services';
import { Observable, Subject, takeUntil, zip } from 'rxjs';

interface ConnectionCheck {
    testing: boolean;
    result: TargetConnectionCheckResult | null;
    /** Values that were actually probed — the result is dropped once the form no longer matches. */
    snapshot?: string;
}

@Component({
    selector: 'chutney-target',
    templateUrl: './target.component.html',
    styleUrls: ['./target.component.scss'],
    standalone: false
})
export class TargetComponent implements OnInit, OnDestroy {
    targets: Target[] = [];
    existingEnvs: string[] = [];
    environmentsNames: string[];
    name: string = '';
    oldName: string = '';
    errorMessage: string;

    private readonly connectionChecks = new Map<string, ConnectionCheck>();

    private unsubscribeSub$: Subject<void> = new Subject();

    constructor(private route: ActivatedRoute,
                private router: Router,
                private environmentService: EnvironmentService,
                public validationService: ValidationService) {
    }

    ngOnInit(): void {
        this.route.data.subscribe((data: { targets: Target[], environmentsNames: string [] }) => {
            if (data.targets.length) {
                this.name = data.targets[0].name;
                this.oldName = this.name;
            }
            this.existingEnvs = data.targets.map(target => target.environment);
            this.environmentsNames = data.environmentsNames;
            const newTargets = this.environmentsNames.filter(name => !data.targets.map(target => target.environment).includes(name))
                .map(name => new Target(
                    this.name,
                    '',
                    null,
                    name
                ));
            this.targets = [...data.targets, ...newTargets].sort((a, b) => a.environment.localeCompare(b.environment));
        });
    }

    ngOnDestroy() {
        this.unsubscribeSub$.next();
        this.unsubscribeSub$.complete();
    }

    save() {
        const actions$: Observable<any> [] = this.targets
            .filter(target => this.validationService.isValidUrl(target.url))
            .map(target => ({...target, name: this.name}))
            .map(target => this.existOn(target.environment) ? this.environmentService.updateTarget(this.oldName, target) : this.environmentService.addTarget(target));

        zip([...actions$])
            .pipe(takeUntil(this.unsubscribeSub$))
            .subscribe({
                next: () => {
                    this.router.navigate(['targets']);
                },
                error: err => this.errorMessage = err.error
            });
    }

    deleteAll() {
        this.environmentService.deleteTarget(this.oldName)
            .pipe(takeUntil(this.unsubscribeSub$))
            .subscribe(() => {
                this.router.navigate(['targets']);
            });
    }

    delete(index: number) {
        let environmentName = this.targets[index].environment;
        this.environmentService.deleteEnvironmentTarget(environmentName, this.oldName)
            .pipe(takeUntil(this.unsubscribeSub$))
            .subscribe(() => {
                this.targets[index] = new Target(this.oldName, '', null, environmentName);
                this.existingEnvs = this.existingEnvs.filter(env => env !== environmentName);
            });
    }

    existOn(environment: string): boolean {
        return this.existingEnvs.includes(environment);
    }

    canTest(target: Target): boolean {
        return !!target.url && this.validationService.isValidUrl(target.url);
    }

    testConnection(target: Target) {
        const env = target.environment;
        const snapshot = this.snapshotOf(target);
        this.connectionChecks.set(env, { testing: true, result: null, snapshot });
        this.environmentService.checkTargetValues({ ...target, name: this.name })
            .pipe(takeUntil(this.unsubscribeSub$))
            .subscribe({
                next: result => this.connectionChecks.set(env, { testing: false, result, snapshot }),
                error: error => this.connectionChecks.set(env, {
                    testing: false,
                    result: new TargetConnectionCheckResult('DOWN', 'UNREACHABLE', error?.error ?? error?.message ?? '', 0),
                    snapshot
                })
            });
    }

    isTesting(environment: string): boolean {
        return this.connectionChecks.get(environment)?.testing ?? false;
    }

    /**
     * The result of the last check, but only while the form still holds the values that were probed —
     * editing the url or a property drops it rather than vouching for something that was never tested.
     */
    resultFor(target: Target): TargetConnectionCheckResult | null {
        const check = this.connectionChecks.get(target.environment);
        if (!check?.result) {
            return null;
        }
        return check.snapshot === this.snapshotOf(target) ? check.result : null;
    }

    private snapshotOf(target: Target): string {
        return JSON.stringify({ name: this.name, url: target.url, properties: target.properties });
    }

    canSave() {
        let definedTargets = this.targets.filter(target => target.url);
        return this.name && definedTargets.length &&
            definedTargets.every(target => this.validationService.isValidUrlOrSpel(target.url));
    }

    export(item: Target) {
        this.environmentService.exportTargetOn(item.environment, item.name)
            .pipe(takeUntil(this.unsubscribeSub$))
            .subscribe({
                    error: (error) => {
                        this.errorMessage = error.error;
                    }
            });
    }

    import(file: File, index: number) {
        const environment = this.targets[index].environment;
        this.environmentService.importTarget(file, environment)
            .pipe(takeUntil(this.unsubscribeSub$))
            .subscribe({
                next: target => {
                    this.targets[index] = target;
                    this.existingEnvs.push(environment)
                },
                error: err => this.errorMessage = err.error
            });
    }
}
