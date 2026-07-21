/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.api.dto;

import fr.enedis.chutney.target.domain.TargetConnectionCheckResult;

public record TargetConnectionCheckResultDto(String status, String reason, String detail, long durationMs) {

    public static TargetConnectionCheckResultDto from(TargetConnectionCheckResult result) {
        return new TargetConnectionCheckResultDto(
            result.status().name(),
            result.reason().name(),
            result.detail(),
            result.durationMs());
    }
}
