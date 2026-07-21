/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { TargetConnectionResultComponent } from './target-connection-result.component';
import { TargetConnectionCheckResult } from '@model';

describe('TargetConnectionResultComponent', () => {

    let component: TargetConnectionResultComponent;

    beforeEach(() => {
        component = new TargetConnectionResultComponent();
    });

    it('should render a success result without a hint', () => {
        component.result = new TargetConnectionCheckResult('UP', 'OK', null, 80);

        expect(component.kind).toBe('ok');
        expect(component.alertClass).toBe('alert-success');
        expect(component.iconClass).toContain('bi-check-circle-fill');
        expect(component.hasHint).toBeFalse();
    });

    it('should render a failure result with a hint and title key', () => {
        component.result = new TargetConnectionCheckResult('DOWN', 'AUTH_FAILED', 'Authentication failed (HTTP 401)', 12);

        expect(component.kind).toBe('bad');
        expect(component.alertClass).toBe('alert-danger');
        expect(component.hasHint).toBeTrue();
        expect(component.titleKey).toBe('admin.targets.connection.reason.AUTH_FAILED.title');
    });

    it('should render a not-testable result as info', () => {
        component.result = new TargetConnectionCheckResult('UNKNOWN', 'PROTOCOL_NOT_SUPPORTED', null, 0);

        expect(component.kind).toBe('info');
        expect(component.alertClass).toBe('alert-secondary');
        expect(component.iconClass).toContain('bi-info-circle-fill');
    });

    it('should collapse the details toggle whenever a new result is set', () => {
        component.showDetails = true;

        component.result = new TargetConnectionCheckResult('DOWN', 'TIMEOUT', 'detail', 10);

        expect(component.showDetails).toBeFalse();
    });
});
