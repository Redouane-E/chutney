/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.api;

import fr.enedis.chutney.environment.api.target.dto.TargetDto;
import fr.enedis.chutney.target.api.dto.TargetConnectionCheckResultDto;
import fr.enedis.chutney.target.domain.TargetConnectionCheckService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TargetConnectionCheckController {

    public static final String BASE_URL = "/api/v2/environments";
    public static final String TARGET_CHECK_URL = "/api/v2/targets/connection-check";

    private final TargetConnectionCheckService targetConnectionCheckService;

    TargetConnectionCheckController(TargetConnectionCheckService targetConnectionCheckService) {
        this.targetConnectionCheckService = targetConnectionCheckService;
    }

    /**
     * Probe a saved target, looked up by environment + name (used by the target list).
     */
    @PreAuthorize("hasAnyAuthority('TARGET_READ', 'ADMIN_ACCESS')")
    @PostMapping(path = BASE_URL + "/{environmentName}/targets/{targetName}/connection-check", produces = MediaType.APPLICATION_JSON_VALUE)
    public TargetConnectionCheckResultDto checkConnection(@PathVariable("environmentName") String environmentName,
                                                          @PathVariable("targetName") String targetName) {
        return TargetConnectionCheckResultDto.from(targetConnectionCheckService.check(environmentName, targetName));
    }

    /**
     * Probe a target definition supplied in the body — used to test the values being edited before
     * they are saved.
     */
    @PreAuthorize("hasAnyAuthority('TARGET_READ', 'ADMIN_ACCESS')")
    @PostMapping(path = TARGET_CHECK_URL, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TargetConnectionCheckResultDto checkConnection(@RequestBody TargetDto targetDto) {
        return TargetConnectionCheckResultDto.from(targetConnectionCheckService.check(targetDto));
    }
}
