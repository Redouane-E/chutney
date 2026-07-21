/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { TargetConnectionReason } from '@model';

export type ConnectionKind = 'ok' | 'bad' | 'info';

export function reasonKind(reason: TargetConnectionReason): ConnectionKind {
    if (reason === 'OK') {
        return 'ok';
    }
    if (reason === 'PROTOCOL_NOT_SUPPORTED') {
        return 'info';
    }
    return 'bad';
}

export function reasonTitleKey(reason: TargetConnectionReason): string {
    return `admin.targets.connection.reason.${reason}.title`;
}

export function reasonHintKey(reason: TargetConnectionReason): string {
    return `admin.targets.connection.reason.${reason}.hint`;
}
