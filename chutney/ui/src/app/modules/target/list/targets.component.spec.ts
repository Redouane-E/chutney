/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { of, throwError } from 'rxjs';

import { TargetsComponent } from './targets.component';
import { TargetConnectionCheckEntry } from '@model';

/** A status as the server returns it: the result plus when the probe ran. */
function entry(status: any, reason: any, detail: string, durationMs: number,
               targetName = 'a-target', environmentName = 'DEFAULT',
               ageMs = 0, ttlMs = 900000): TargetConnectionCheckEntry {
    return new TargetConnectionCheckEntry(environmentName, targetName, status, reason, detail, durationMs, ageMs, ttlMs);
}

describe('TargetsComponent', () => {

    let environmentService: any;
    let loginService: any;
    let translateService: any;
    let component: TargetsComponent;

    beforeEach(() => {
        environmentService = jasmine.createSpyObj('EnvironmentService',
            ['listTargets', 'checkTargetConnection', 'listTargetConnectionStatuses']);
        environmentService.listTargets.and.returnValue(of([]));
        environmentService.listTargetConnectionStatuses.and.returnValue(of([]));
        loginService = jasmine.createSpyObj('LoginService', ['hasAuthorization', 'getUser']);
        loginService.getUser.and.returnValue(of({ id: 'user-1' }));
        loginService.hasAuthorization.and.returnValue(true);
        translateService = jasmine.createSpyObj('TranslateService', ['instant']);
        translateService.instant.and.callFake((key: string) => key);
        component = new TargetsComponent(environmentService, loginService, translateService);
    });

    it('should be idle before any check', () => {
        expect(component.statusKind('a-target', 'DEFAULT')).toBe('idle');
        expect(component.statusDotClass('a-target', 'DEFAULT')).toBe('status-dot-idle');
        expect(component.statusTooltip('a-target', 'DEFAULT')).toBe('admin.targets.connection.status.notTested');
    });

    it('should show a green dot after a successful check', () => {
        environmentService.checkTargetConnection.and.returnValue(of(entry('UP', 'OK', null, 12)));

        component.testConnection('a-target', 'DEFAULT');

        // an explicit single test always re-probes rather than reusing a recent verdict
        expect(environmentService.checkTargetConnection).toHaveBeenCalledWith('DEFAULT', 'a-target', true);
        expect(component.statusKind('a-target', 'DEFAULT')).toBe('ok');
        expect(component.statusDotClass('a-target', 'DEFAULT')).toBe('status-dot-up');
        expect(component.statusTooltip('a-target', 'DEFAULT')).toContain('12');
    });

    it('should show a red dot and expose the reason on hover when the check fails', () => {
        environmentService.checkTargetConnection.and.returnValue(
            of(entry('DOWN', 'AUTH_FAILED', 'Authentication failed (HTTP 401)', 30)));

        component.testConnection('a-target', 'DEFAULT');

        expect(component.statusDotClass('a-target', 'DEFAULT')).toBe('status-dot-down');
        const tooltip = component.statusTooltip('a-target', 'DEFAULT');
        expect(tooltip).toContain('admin.targets.connection.reason.AUTH_FAILED.title');
        expect(tooltip).toContain('Authentication failed (HTTP 401)');
    });

    it('should show a grey dot when the protocol is not testable', () => {
        environmentService.checkTargetConnection.and.returnValue(
            of(entry('UNKNOWN', 'PROTOCOL_NOT_SUPPORTED', null, 0)));

        component.testConnection('a-target', 'DEFAULT');

        expect(component.statusDotClass('a-target', 'DEFAULT')).toBe('status-dot-unknown');
    });

    it('should not blame the target when the request itself fails', () => {
        // a 403/404/offline says nothing about the target, so it must not be shown as "down"
        environmentService.checkTargetConnection.and.returnValue(throwError(() => ({ error: 'boom' })));

        component.testConnection('a-target', 'DEFAULT');

        expect(component.statusKind('a-target', 'DEFAULT')).toBe('idle');
        expect(component.errorMessage).toBe('boom');
    });

    it('should keep the previously known status when a re-check request fails', () => {
        environmentService.checkTargetConnection.and.returnValue(of(entry('UP', 'OK', null, 12)));
        component.testConnection('a-target', 'DEFAULT');

        environmentService.checkTargetConnection.and.returnValue(throwError(() => ({ status: 403 })));
        component.testConnection('a-target', 'DEFAULT');

        expect(component.statusKind('a-target', 'DEFAULT')).toBe('ok');
    });

    it('should stop showing a status once the server would have forgotten it', () => {
        // the server keeps a result for ttlMs; an open page must not outlive that
        environmentService.listTargetConnectionStatuses.and.returnValue(of([
            entry('UP', 'OK', null, 12, 'stale', 'DEFAULT', 20 * 60 * 1000, 15 * 60 * 1000)
        ]));

        component.ngOnInit();

        expect(component.statusKind('stale', 'DEFAULT')).toBe('idle');
    });

    it('should test every listed target in one click', () => {
        environmentService.checkTargetConnection.and.returnValue(of(entry('UP', 'OK', null, 5)));
        component.targetsNames = ['t1', 't2'];
        spyOn(component, 'activeEnvTab').and.returnValue('DEFAULT');

        component.testAll();

        // unforced: a bulk check reuses recent results so concurrent users do not stampede the systems
        expect(environmentService.checkTargetConnection).toHaveBeenCalledWith('DEFAULT', 't1', false);
        expect(environmentService.checkTargetConnection).toHaveBeenCalledWith('DEFAULT', 't2', false);
        expect(component.statusKind('t1', 'DEFAULT')).toBe('ok');
        expect(component.statusKind('t2', 'DEFAULT')).toBe('ok');
    });

    it('should show what the instance already knows when the page opens', () => {
        // Given: another user already probed these targets — the result is shared, not per browser
        environmentService.listTargetConnectionStatuses.and.returnValue(of([
            entry('UP', 'OK', null, 12, 'shared-up', 'DEFAULT'),
            entry('DOWN', 'CONNECTION_REFUSED', 'refused', 3, 'shared-down', 'PROD')
        ]));

        component.ngOnInit();

        expect(component.statusKind('shared-up', 'DEFAULT')).toBe('ok');
        expect(component.statusKind('shared-down', 'PROD')).toBe('bad');
    });

    it('should keep the target list usable when statuses cannot be read', () => {
        environmentService.listTargetConnectionStatuses.and.returnValue(throwError(() => new Error('nope')));

        component.ngOnInit();

        expect(component.statusKind('a-target', 'DEFAULT')).toBe('idle');
    });

    it('should keep statuses independent per (target, environment)', () => {
        environmentService.checkTargetConnection.and.returnValue(of(entry('UP', 'OK', null, 5)));

        component.testConnection('a-target', 'DEFAULT');

        expect(component.statusKind('a-target', 'DEFAULT')).toBe('ok');
        expect(component.statusKind('a-target', 'OTHER')).toBe('idle');
    });
});
