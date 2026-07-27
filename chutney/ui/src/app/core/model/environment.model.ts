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
        return a.name.toUpperCase().localeCompare(b.name.toUpperCase());
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

/**
 * Last known connectivity result of a saved target, as recorded by the server and shared by every
 * user of the instance.
 *
 * Freshness comes as an age and a retention rather than a timestamp: the age can then be tracked and
 * the result dropped at the same moment the server drops it, using only the browser's own clock —
 * which need not agree with the server's.
 */
export class TargetConnectionCheckEntry {
    constructor(
        public environmentName: string,
        public targetName: string,
        public status: TargetConnectionStatus,
        public reason: TargetConnectionReason,
        public detail: string,
        public durationMs: number,
        public ageMs: number,
        public ttlMs: number,
    ) {
    }
}
