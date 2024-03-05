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

package com.chutneytesting.jira.infra;

import static com.chutneytesting.tools.file.FileUtils.initFolder;

import com.chutneytesting.jira.domain.JiraRepository;
import com.chutneytesting.jira.domain.JiraServerConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class JiraFileRepository implements JiraRepository {

    private static final String FILE_EXTENSION = ".json";
    private static final String SCENARIO_FILE = "scenario_link" + FILE_EXTENSION;
    private static final String CAMPAIGN_FILE = "campaign_link" + FILE_EXTENSION;
    private static final String CAMPAIGN_EXECUTION_FILE = "campaign_execution_link" + FILE_EXTENSION;
    private static final String CONFIGURATION_FILE = "jira_config" + FILE_EXTENSION;

    private final Path storeFolderPath;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public JiraFileRepository(String storeFolderPath) throws UncheckedIOException {
        this.storeFolderPath = Paths.get(storeFolderPath);
        initFolder(this.storeFolderPath);
    }

    @Override
    public Path getFolderPath() {
        return storeFolderPath;
    }

    @Override
    public Map<String, String> getAllLinkedCampaigns() {
        return getAll(CAMPAIGN_FILE);
    }

    @Override
    public Map<String, String> getAllLinkedScenarios() {
        return getAll(SCENARIO_FILE)
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public String getByScenarioId(String scenarioId) {
        return getById(SCENARIO_FILE, scenarioId);
    }

    @Override
    public void saveForScenario(String scenarioId, String jiraId) {
        save(SCENARIO_FILE, scenarioId, jiraId);
    }

    @Override
    public void removeForScenario(String scenarioId) {
        remove(SCENARIO_FILE, scenarioId);
    }

    @Override
    public String getByCampaignId(String campaignId) {
        return getById(CAMPAIGN_FILE, campaignId);
    }

    @Override
    public void saveForCampaign(String campaignId, String jiraId) {
        save(CAMPAIGN_FILE, campaignId, jiraId);
    }

    @Override
    public void removeForCampaign(String campaignId) {
        remove(CAMPAIGN_FILE, campaignId);
    }

    @Override
    public String getByCampaignExecutionId(String campaignExecutionId) {
        return getById(CAMPAIGN_EXECUTION_FILE, campaignExecutionId);
    }

    @Override
    public void saveForCampaignExecution(String campaignExecutionId, String jiraId) {
        save(CAMPAIGN_EXECUTION_FILE, campaignExecutionId, jiraId);
    }

    @Override
    public void removeForCampaignExecution(String campaignExecutionId) {
        remove(CAMPAIGN_EXECUTION_FILE, campaignExecutionId);
    }

    @Override
    public JiraServerConfiguration loadServerConfiguration() {
        JiraTargetConfigurationDto dto = doLoadServerConfiguration();
        return new JiraServerConfiguration(dto.url, dto.username, dto.password, dto.urlProxy, dto.userProxy, dto.passwordProxy);
    }

    @Override
    public void saveServerConfiguration(JiraServerConfiguration jiraServerConfiguration) {
        JiraTargetConfigurationDto jiraTargetConfigurationDto = new JiraTargetConfigurationDto(
            jiraServerConfiguration.url(),
            jiraServerConfiguration.username(),
            jiraServerConfiguration.password(),
            jiraServerConfiguration.urlProxy(),
            jiraServerConfiguration.userProxy(),
            jiraServerConfiguration.passwordProxy()
        );
        Path resolvedFilePath = storeFolderPath.resolve(CONFIGURATION_FILE);
        doSave(resolvedFilePath, jiraTargetConfigurationDto);
    }

    private JiraTargetConfigurationDto doLoadServerConfiguration() {
        Path configurationFilePath = storeFolderPath.resolve(CONFIGURATION_FILE);
        if (!Files.exists(configurationFilePath)) {
            return new JiraTargetConfigurationDto();
        }
        try {
            byte[] bytes = Files.readAllBytes(configurationFilePath);
            try {
                return objectMapper.readValue(bytes, JiraTargetConfigurationDto.class);
            } catch (IOException e) {
                throw new UnsupportedOperationException("Cannot deserialize configuration file: " + configurationFilePath, e);
            }
        } catch (IOException e) {
            throw new UnsupportedOperationException("Cannot read configuration file: " + configurationFilePath, e);
        }
    }

    private String getById(String filePath, String id) {
        return getAll(filePath).getOrDefault(id, "");
    }

    private Map<String, String> getAll(String filePath) {
        Path resolvedFilePath = storeFolderPath.resolve(filePath);
        if (!Files.exists(resolvedFilePath)) {
            return new HashMap<>();
        }
        try {
            byte[] bytes = Files.readAllBytes(resolvedFilePath);
            try {
                return objectMapper.readValue(bytes, new TypeReference<>() {
                });
            } catch (IOException e) {
                throw new UnsupportedOperationException("Cannot deserialize configuration file: " + resolvedFilePath, e);
            }
        } catch (IOException e) {
            throw new UnsupportedOperationException("Cannot read configuration file: " + resolvedFilePath, e);
        }
    }

    private void save(String filePath, String chutneyId, String jiraId) {
        Path resolvedFilePath = storeFolderPath.resolve(filePath);
        try {
            Map<String, String> map = new HashMap<>();

            if (Files.exists(resolvedFilePath)) {
                byte[] bytes = Files.readAllBytes(resolvedFilePath);
                map.putAll(objectMapper.readValue(bytes, new TypeReference<Map<String, String>>() {
                }));
            }

            if (jiraId.isEmpty())
                map.remove(chutneyId);
            else
                map.put(chutneyId, jiraId);
            doSave(resolvedFilePath, map);

        } catch (IOException e) {
            throw new UnsupportedOperationException("Cannot read configuration file: " + resolvedFilePath, e);
        }
    }

    private void doSave(Path path, Object map) {

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(map);
            try {
                Files.write(path, bytes);
            } catch (IOException e) {
                throw new UnsupportedOperationException("Cannot write in configuration directory: " + storeFolderPath, e);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot serialize " + map, e);
        }
    }

    private void remove(String filePath, String chutneyId) {
        Path resolvedFilePath = storeFolderPath.resolve(filePath);
        try {
            Map<String, String> map = new HashMap<>();

            if (Files.exists(resolvedFilePath)) {
                byte[] bytes = Files.readAllBytes(resolvedFilePath);
                map.putAll(objectMapper.readValue(bytes, new TypeReference<Map<String, String>>() {
                }));
            }

            map.remove(chutneyId);
            doSave(resolvedFilePath, map);
        } catch (IOException e) {
            throw new UnsupportedOperationException("Cannot read configuration file: " + resolvedFilePath, e);
        }
    }

}
