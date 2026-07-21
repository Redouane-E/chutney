/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

import { Component, Input } from '@angular/core';
import { TargetConnectionCheckResult } from '@model';
import { ConnectionKind, reasonHintKey, reasonKind, reasonTitleKey } from '../connection-check.util';

@Component({
    selector: 'chutney-connection-result',
    templateUrl: './target-connection-result.component.html',
    styleUrls: ['./target-connection-result.component.scss'],
    standalone: false
})
export class TargetConnectionResultComponent {

    @Input() testing = false;

    private _result: TargetConnectionCheckResult | null = null;
    showDetails = false;

    @Input()
    set result(value: TargetConnectionCheckResult | null) {
        this._result = value;
        this.showDetails = false;
    }

    get result(): TargetConnectionCheckResult | null {
        return this._result;
    }

    get kind(): ConnectionKind {
        return this._result ? reasonKind(this._result.reason) : 'info';
    }

    get titleKey(): string {
        return this._result ? reasonTitleKey(this._result.reason) : '';
    }

    get hintKey(): string {
        return this._result ? reasonHintKey(this._result.reason) : '';
    }

    get hasHint(): boolean {
        return !!this._result && this._result.reason !== 'OK';
    }

    get alertClass(): string {
        return { ok: 'alert-success', bad: 'alert-danger', info: 'alert-secondary' }[this.kind];
    }

    /** Icon shape only — the colour is inherited from the alert, so it stays visible on any theme. */
    get iconClass(): string {
        return {
            ok: 'bi-check-circle-fill',
            bad: 'bi-x-circle-fill',
            info: 'bi-info-circle-fill'
        }[this.kind];
    }
}
