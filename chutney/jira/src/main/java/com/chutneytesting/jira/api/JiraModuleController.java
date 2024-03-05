/*
 * Copyright 2017-2023 Enedis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chutneytesting.jira.api;

import com.chutneytesting.jira.domain.JiraRepository;
import com.chutneytesting.jira.domain.JiraServerConfiguration;
import com.chutneytesting.jira.domain.JiraXrayService;
import com.chutneytesting.jira.xrayapi.XrayTestExecTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(JiraModuleController.BASE_URL)
@CrossOrigin(origins = "*")
public class JiraModuleController {

    public static final String BASE_URL = "/api/ui/jira/v1/";
    public static final String BASE_SCENARIO_URL = "scenario";
    public static final String BASE_CAMPAIGN_URL = "campaign";
    public static final String BASE_CAMPAIGN_EXEC_URL = "campaign_execution";
    public static final String BASE_TEST_EXEC_URL = "testexec";
    public static final String BASE_CONFIGURATION_URL = "configuration";

    private final JiraRepository jiraRepository;
    private final JiraXrayService jiraXrayService;

    public JiraModuleController(JiraRepository jiraRepository, JiraXrayService jiraXrayService) {
        this.jiraRepository = jiraRepository;
        this.jiraXrayService = jiraXrayService;
    }

    @PreAuthorize("hasAuthority('SCENARIO_READ') or hasAuthority('CAMPAIGN_WRITE')")
    @GetMapping(path = BASE_SCENARIO_URL, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getLinkedScenarios() {
        return jiraRepository.getAllLinkedScenarios();
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping(path = BASE_CAMPAIGN_URL, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getLinkedCampaigns() {
        return jiraRepository.getAllLinkedCampaigns();
    }

    @PreAuthorize("hasAuthority('SCENARIO_WRITE')")
    @GetMapping(path = BASE_SCENARIO_URL + "/{scenarioId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JiraDto getByScenarioId(@PathVariable String scenarioId) {
        String jiraId = jiraRepository.getByScenarioId(scenarioId);
        return ImmutableJiraDto.builder()
            .id(jiraId)
            .chutneyId(scenarioId)
            .build();
    }

    @PreAuthorize("hasAuthority('SCENARIO_WRITE')")
    @PostMapping(path = BASE_SCENARIO_URL,
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public JiraDto saveForScenario(@RequestBody JiraDto jiraDto) {
        jiraRepository.saveForScenario(jiraDto.chutneyId(), jiraDto.id());
        return ImmutableJiraDto.builder()
            .id(jiraDto.id())
            .chutneyId(jiraDto.chutneyId())
            .build();
    }

    @PreAuthorize("hasAuthority('SCENARIO_WRITE')")
    @DeleteMapping(path = BASE_SCENARIO_URL + "/{scenarioId}")
    public void removeForScenario(@PathVariable String scenarioId) {
        jiraRepository.removeForScenario(scenarioId);
    }


    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping(path = BASE_CAMPAIGN_URL + "/{campaignId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JiraDto getByCampaignId(@PathVariable String campaignId) {
        String jiraId = jiraRepository.getByCampaignId(campaignId);
        return ImmutableJiraDto.builder()
            .id(jiraId)
            .chutneyId(campaignId)
            .build();
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_WRITE')")
    @GetMapping(path = BASE_TEST_EXEC_URL + "/{testExecId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<JiraDto> getScenariosByTestExecutionId(@PathVariable String testExecId) {
        if (testExecId.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, String> allLinkedScenarios = jiraRepository.getAllLinkedScenarios();
        Map<String, XrayTestExecTest> collect = jiraXrayService.getTestExecutionScenarios(testExecId).stream().collect(Collectors.toMap(XrayTestExecTest::getKey, Function.identity()));

        return allLinkedScenarios.entrySet()
            .stream()
            .filter(entry -> collect.get(entry.getValue()) != null)
            .map(m -> ImmutableJiraDto.builder()
                .id(m.getValue())
                .chutneyId(m.getKey())
                .executionStatus(Optional.ofNullable(collect.get(m.getValue()).getStatus()))
                .build())
            .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_WRITE')")
    @GetMapping(path = BASE_CAMPAIGN_EXEC_URL + "/{campaignExecutionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JiraTestExecutionDto getScenariosByCampaignExecutionId(@PathVariable String campaignExecutionId) {
        if (campaignExecutionId.isEmpty()) {
            throw new IllegalArgumentException("Empty campaign execution id");
        }

        String testExecId = jiraRepository.getByCampaignExecutionId(campaignExecutionId);
        List<JiraDto> jiraDtoList = getScenariosByTestExecutionId(testExecId);

        return ImmutableJiraTestExecutionDto.builder()
            .id(testExecId)
            .jiraScenarios(jiraDtoList)
            .build();
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_WRITE')")
    @PostMapping(path = BASE_CAMPAIGN_URL,
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public JiraDto saveForCampaign(@RequestBody JiraDto jiraDto) {
        jiraRepository.saveForCampaign(jiraDto.chutneyId(), jiraDto.id());
        return ImmutableJiraDto.builder()
            .id(jiraDto.id())
            .chutneyId(jiraDto.chutneyId())
            .build();
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_WRITE')")
    @DeleteMapping(path = BASE_CAMPAIGN_URL + "/{campaignId}")
    public void removeForCampaign(@PathVariable String campaignId) {
        jiraRepository.removeForCampaign(campaignId);
    }

    @PreAuthorize("hasAuthority('ADMIN_ACCESS')")
    @GetMapping(path = BASE_CONFIGURATION_URL, produces = MediaType.APPLICATION_JSON_VALUE)
    public JiraConfigurationDto getConfiguration() {
        JiraServerConfiguration jiraServerConfiguration = jiraRepository.loadServerConfiguration();
        return ImmutableJiraConfigurationDto.builder()
            .url(jiraServerConfiguration.url())
            .username(jiraServerConfiguration.username())
            .password(jiraServerConfiguration.password())
            .urlProxy(jiraServerConfiguration.urlProxy())
            .userProxy(jiraServerConfiguration.userProxy())
            .passwordProxy(jiraServerConfiguration.passwordProxy())
            .build();
    }

    @PreAuthorize("hasAuthority('SCENARIO_READ') or hasAuthority('CAMPAIGN_READ')")
    @GetMapping(path = BASE_CONFIGURATION_URL + "/url", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getConfigurationUrl() {
        JiraServerConfiguration jiraServerConfiguration = jiraRepository.loadServerConfiguration();
        return jiraServerConfiguration.url();
    }

    @PreAuthorize("hasAuthority('ADMIN_ACCESS')")
    @PostMapping(path = BASE_CONFIGURATION_URL,
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public void saveConfiguration(@RequestBody JiraConfigurationDto jiraConfigurationDto) {
        jiraRepository.saveServerConfiguration(
            new JiraServerConfiguration(
                jiraConfigurationDto.url(),
                jiraConfigurationDto.username(),
                jiraConfigurationDto.password(),
                jiraConfigurationDto.urlProxy().orElse(null),
                jiraConfigurationDto.userProxy().orElse(null),
                jiraConfigurationDto.passwordProxy().orElse(null)
            )
        );
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_WRITE')")
    @PutMapping(path = BASE_TEST_EXEC_URL + "/{testExecId}",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE)
    public void updateScenarioStatus(@PathVariable String testExecId, @RequestBody JiraDto jiraDto) {
        if (!testExecId.isEmpty() && jiraDto.executionStatus().isPresent()) {
            jiraXrayService.updateScenarioStatus(testExecId, jiraDto.chutneyId(), jiraDto.executionStatus().get());
        }
    }
}
