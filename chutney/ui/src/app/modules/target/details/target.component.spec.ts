/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { of, throwError } from 'rxjs';

import { TargetComponent } from './target.component';
import { Target, TargetConnectionCheckResult } from '@model';

describe('TargetComponent (edit)', () => {

    let component: TargetComponent;
    let environmentService: any;
    let validationService: any;
    let router: any;

    beforeEach(() => {
        environmentService = jasmine.createSpyObj('EnvironmentService', ['checkTargetValues']);
        validationService = jasmine.createSpyObj('ValidationService', ['isValidUrl']);
        router = jasmine.createSpyObj('Router', ['navigate']);
        // route.data is not read here (ngOnInit is not invoked)
        component = new TargetComponent({ data: of({}) } as any, router, environmentService, validationService);
        component.name = 'my-target';
    });

    it('should allow testing only when the url is concrete', () => {
        validationService.isValidUrl.and.returnValue(true);
        expect(component.canTest(new Target('t', 'http://host:8080', [], 'DEFAULT'))).toBeTrue();

        validationService.isValidUrl.and.returnValue(false);
        expect(component.canTest(new Target('t', '${#dynamicUri}', [], 'DEFAULT'))).toBeFalse();
    });

    it('should probe the live edited values (test before save) and store the result', () => {
        environmentService.checkTargetValues.and.returnValue(of(new TargetConnectionCheckResult('UP', 'OK', null, 50)));
        const target = new Target('old-name', 'https://api.example.com', [], 'DEFAULT');

        component.testConnection(target);

        expect(environmentService.checkTargetValues).toHaveBeenCalledWith(
            jasmine.objectContaining({ url: 'https://api.example.com', name: 'my-target' }));
        expect(component.isTesting('DEFAULT')).toBeFalse();
        expect(component.resultFor(target).status).toBe('UP');
    });

    it('should fall back to a DOWN result when the request errors', () => {
        environmentService.checkTargetValues.and.returnValue(throwError(() => ({ error: 'boom' })));
        const target = new Target('t', 'https://api.example.com', [], 'DEFAULT');

        component.testConnection(target);

        expect(component.resultFor(target).status).toBe('DOWN');
        expect(component.resultFor(target).detail).toBe('boom');
    });

    it('should drop the result once the tested values are edited', () => {
        environmentService.checkTargetValues.and.returnValue(of(new TargetConnectionCheckResult('UP', 'OK', null, 50)));
        const target = new Target('t', 'https://api.example.com', [], 'DEFAULT');
        component.testConnection(target);
        expect(component.resultFor(target).status).toBe('UP');

        // the user edits the url after a successful test
        target.url = 'https://other.example.com';

        expect(component.resultFor(target)).toBeNull();
    });
});
