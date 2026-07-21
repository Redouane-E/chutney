/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { of, throwError } from 'rxjs';

import { TargetsComponent } from './targets.component';
import { TargetConnectionCheckResult } from '@model';

describe('TargetsComponent', () => {

    let environmentService: any;
    let loginService: any;
    let translateService: any;
    let component: TargetsComponent;

    beforeEach(() => {
        environmentService = jasmine.createSpyObj('EnvironmentService', ['listTargets', 'checkTargetConnection']);
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
        environmentService.checkTargetConnection.and.returnValue(of(new TargetConnectionCheckResult('UP', 'OK', null, 12)));

        component.testConnection('a-target', 'DEFAULT');

        expect(environmentService.checkTargetConnection).toHaveBeenCalledWith('DEFAULT', 'a-target');
        expect(component.statusKind('a-target', 'DEFAULT')).toBe('ok');
        expect(component.statusDotClass('a-target', 'DEFAULT')).toBe('status-dot-up');
        expect(component.statusTooltip('a-target', 'DEFAULT')).toContain('12');
    });

    it('should show a red dot and expose the reason on hover when the check fails', () => {
        environmentService.checkTargetConnection.and.returnValue(
            of(new TargetConnectionCheckResult('DOWN', 'AUTH_FAILED', 'Authentication failed (HTTP 401)', 30)));

        component.testConnection('a-target', 'DEFAULT');

        expect(component.statusDotClass('a-target', 'DEFAULT')).toBe('status-dot-down');
        const tooltip = component.statusTooltip('a-target', 'DEFAULT');
        expect(tooltip).toContain('admin.targets.connection.reason.AUTH_FAILED.title');
        expect(tooltip).toContain('Authentication failed (HTTP 401)');
    });

    it('should show a grey dot when the protocol is not testable', () => {
        environmentService.checkTargetConnection.and.returnValue(
            of(new TargetConnectionCheckResult('UNKNOWN', 'PROTOCOL_NOT_SUPPORTED', null, 0)));

        component.testConnection('a-target', 'DEFAULT');

        expect(component.statusDotClass('a-target', 'DEFAULT')).toBe('status-dot-unknown');
    });

    it('should fall back to a DOWN result when the request errors', () => {
        environmentService.checkTargetConnection.and.returnValue(throwError(() => ({ error: 'boom' })));

        component.testConnection('a-target', 'DEFAULT');

        expect(component.statusDotClass('a-target', 'DEFAULT')).toBe('status-dot-down');
        expect(component.resultOf('a-target', 'DEFAULT').detail).toBe('boom');
    });

    it('should test every listed target in one click', () => {
        environmentService.checkTargetConnection.and.returnValue(of(new TargetConnectionCheckResult('UP', 'OK', null, 5)));
        component.targetsNames = ['t1', 't2'];
        spyOn(component, 'activeEnvTab').and.returnValue('DEFAULT');

        component.testAll();

        expect(environmentService.checkTargetConnection).toHaveBeenCalledWith('DEFAULT', 't1');
        expect(environmentService.checkTargetConnection).toHaveBeenCalledWith('DEFAULT', 't2');
        expect(component.statusKind('t1', 'DEFAULT')).toBe('ok');
        expect(component.statusKind('t2', 'DEFAULT')).toBe('ok');
    });

    it('should keep statuses independent per (target, environment)', () => {
        environmentService.checkTargetConnection.and.returnValue(of(new TargetConnectionCheckResult('UP', 'OK', null, 5)));

        component.testConnection('a-target', 'DEFAULT');

        expect(component.statusKind('a-target', 'DEFAULT')).toBe('ok');
        expect(component.statusKind('a-target', 'OTHER')).toBe('idle');
    });
});
