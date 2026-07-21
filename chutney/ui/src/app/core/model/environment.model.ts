/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { Entry } from './entry.model';

export class Environment {
    constructor(
        public name: string,
        public description: string,
        public targets: Target [] = [],
        public variables: EnvironmentVariable[] = []) {
    }

    static compareByName(a: Environment, b: Environment): number {
        return a.name.toUpperCase() > b.name.toUpperCase() ? 1 : 0;
    }
}

export class Target {
    constructor(
        public name: string,
        public url: string,
        public properties: Entry [] = [],
        public environment: string = null,
    ) {
    }
}

export class EnvironmentVariable {
    constructor(
        public key: string,
        public value: string,
        public env: string = null
    ) {
    }
}

export class TargetFilter {
    constructor(
        public name: string = null,
        public environment: string = null,
    ) {
    }
}

export type TargetConnectionStatus = 'UP' | 'DOWN' | 'UNKNOWN';

export type TargetConnectionReason =
    'OK' | 'UNKNOWN_HOST' | 'CONNECTION_REFUSED' | 'TIMEOUT' |
    'AUTH_FAILED' | 'TLS_ERROR' | 'UNREACHABLE' | 'PROTOCOL_NOT_SUPPORTED';

export class TargetConnectionCheckResult {
    constructor(
        public status: TargetConnectionStatus,
        public reason: TargetConnectionReason,
        public detail: string,
        public durationMs: number,
    ) {
    }
}
