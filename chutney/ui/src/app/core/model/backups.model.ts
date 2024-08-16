/*
 * SPDX-FileCopyrightText: 2017-2024 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

export class Backup {
    constructor(
        public backupables: string[],
        public time?: Date,
        public id?: string) {
    }
}
