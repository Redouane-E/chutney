/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { reasonHintKey, reasonKind, reasonTitleKey } from './connection-check.util';

describe('connection-check.util', () => {

    it('should map reasons to a visual kind', () => {
        expect(reasonKind('OK')).toBe('ok');
        expect(reasonKind('PROTOCOL_NOT_SUPPORTED')).toBe('info');
        expect(reasonKind('AUTH_FAILED')).toBe('bad');
        expect(reasonKind('UNREACHABLE')).toBe('bad');
        expect(reasonKind('TIMEOUT')).toBe('bad');
    });

    it('should build the i18n title and hint keys', () => {
        expect(reasonTitleKey('AUTH_FAILED')).toBe('admin.targets.connection.reason.AUTH_FAILED.title');
        expect(reasonHintKey('TIMEOUT')).toBe('admin.targets.connection.reason.TIMEOUT.hint');
    });
});
