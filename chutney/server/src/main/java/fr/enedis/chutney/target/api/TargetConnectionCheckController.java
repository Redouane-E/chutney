/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.api;

import fr.enedis.chutney.environment.api.target.dto.TargetDto;
import fr.enedis.chutney.target.api.dto.TargetConnectionCheckResultDto;
import fr.enedis.chutney.target.api.dto.TargetConnectionStatusDto;
import fr.enedis.chutney.target.domain.TargetConnectionCheckService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TargetConnectionCheckController {

    public static final String BASE_URL = "/api/v2/environments";
    public static final String TARGET_CHECK_URL = "/api/v2/targets/connection-check";
    public static final String TARGET_STATUS_URL = "/api/v2/targets/connection-status";

    private final TargetConnectionCheckService targetConnectionCheckService;

    TargetConnectionCheckController(TargetConnectionCheckService targetConnectionCheckService) {
        this.targetConnectionCheckService = targetConnectionCheckService;
    }

    /**
     * Probe a saved target, looked up by environment + name (used by the target list). The outcome is
     * recorded so every user sees it.
     *
     * @param force pass true to always probe; false reuses a recent result, which is what a bulk
     *              "test all" does so concurrent users do not stampede the probed systems.
     */
    @PreAuthorize("hasAnyAuthority('TARGET_READ', 'ADMIN_ACCESS')")
    @PostMapping(path = BASE_URL + "/{environmentName}/targets/{targetName}/connection-check", produces = MediaType.APPLICATION_JSON_VALUE)
    public TargetConnectionStatusDto checkConnection(@PathVariable("environmentName") String environmentName,
                                                     @PathVariable("targetName") String targetName,
                                                     @RequestParam(value = "force", defaultValue = "false") boolean force) {
        return TargetConnectionStatusDto.from(
            targetConnectionCheckService.check(environmentName, targetName, force),
            targetConnectionCheckService.statusTtlMs());
    }

    /**
     * The last known status of every target, so a freshly opened page shows what the instance already
     * knows instead of starting blank.
     */
    @PreAuthorize("hasAnyAuthority('TARGET_READ', 'ADMIN_ACCESS')")
    @GetMapping(path = TARGET_STATUS_URL, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TargetConnectionStatusDto> lastConnectionStatuses() {
        long ttlMs = targetConnectionCheckService.statusTtlMs();
        return targetConnectionCheckService.lastStatuses().stream()
            .map(status -> TargetConnectionStatusDto.from(status, ttlMs))
            .toList();
    }

    /**
     * Probe a target definition supplied in the body — used to test the values being edited before
     * they are saved.
     * <p>
     * This one takes an arbitrary url from the caller, so it makes the server open a connection of
     * the caller's choosing. It therefore requires {@code TARGET_WRITE}: the same authority the edit
     * screen already demands, and one that could anyway save such a target and probe it by name. Read
     * -only users must not be able to reach arbitrary hosts through it.
     */
    @PreAuthorize("hasAnyAuthority('TARGET_WRITE', 'ADMIN_ACCESS')")
    @PostMapping(path = TARGET_CHECK_URL, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TargetConnectionCheckResultDto checkConnection(@RequestBody TargetDto targetDto) {
        return TargetConnectionCheckResultDto.from(targetConnectionCheckService.check(targetDto));
    }
}
