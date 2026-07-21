/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { FileSaverService } from 'ngx-filesaver';

import { EnvironmentService } from './environment.service';
import { Target } from '@model';

describe('EnvironmentService', () => {

    let service: EnvironmentService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            providers: [
                EnvironmentService,
                { provide: FileSaverService, useValue: {} },
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting()
            ]
        });
        service = TestBed.inject(EnvironmentService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('should POST to the target connection-check endpoint, forcing a fresh probe', () => {
        const expected = { environmentName: 'DEFAULT', targetName: 'myTarget', status: 'UP', reason: 'OK', detail: null, durationMs: 5, ageMs: 0, ttlMs: 900000 };

        service.checkTargetConnection('DEFAULT', 'myTarget').subscribe(result => {
            expect(result.status).toBe('UP');
            expect(result.durationMs).toBe(5);
            expect(result.ttlMs).toBe(900000);
        });

        const req = httpMock.expectOne(r => r.url.endsWith('/api/v2/environments/DEFAULT/targets/myTarget/connection-check'));
        expect(req.request.method).toBe('POST');
        expect(req.request.params.get('force')).toBe('true');
        req.flush(expected);
    });

    it('should not force a probe for a bulk check', () => {
        service.checkTargetConnection('DEFAULT', 'myTarget', false).subscribe();

        const req = httpMock.expectOne(r => r.url.endsWith('/api/v2/environments/DEFAULT/targets/myTarget/connection-check'));
        expect(req.request.params.get('force')).toBe('false');
        req.flush({});
    });

    it('should GET the statuses shared by the instance', () => {
        service.listTargetConnectionStatuses().subscribe(statuses => {
            expect(statuses.length).toBe(1);
            expect(statuses[0].targetName).toBe('myTarget');
        });

        const req = httpMock.expectOne(r => r.url.endsWith('/api/v2/targets/connection-status'));
        expect(req.request.method).toBe('GET');
        req.flush([{ environmentName: 'DEFAULT', targetName: 'myTarget', status: 'UP', reason: 'OK', detail: null, durationMs: 5, ageMs: 0, ttlMs: 900000 }]);
    });

    it('should POST the target definition to the test-before-save endpoint', () => {
        const target = new Target('myTarget', 'https://api.example.com', [], 'DEFAULT');

        service.checkTargetValues(target).subscribe(result => {
            expect(result.reason).toBe('AUTH_FAILED');
        });

        const req = httpMock.expectOne(r => r.url.endsWith('/api/v2/targets/connection-check'));
        expect(req.request.method).toBe('POST');
        expect(req.request.body.url).toBe('https://api.example.com');
        req.flush({ status: 'DOWN', reason: 'AUTH_FAILED', detail: 'Authentication failed (HTTP 401)', durationMs: 40 });
    });
});
