/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.enedis.chutney.config.web.WebConfiguration;
import fr.enedis.chutney.environment.api.target.dto.TargetDto;
import fr.enedis.chutney.target.api.dto.TargetConnectionCheckResultDto;
import fr.enedis.chutney.target.api.dto.TargetConnectionStatusDto;
import fr.enedis.chutney.target.domain.TargetConnectionCheckResult;
import fr.enedis.chutney.target.domain.TargetConnectionCheckResult.Reason;
import fr.enedis.chutney.target.domain.TargetConnectionCheckService;
import fr.enedis.chutney.target.domain.TargetConnectionStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class TargetConnectionCheckControllerTest {

    private final TargetConnectionCheckService service = mock(TargetConnectionCheckService.class);
    private final JsonMapper om = (JsonMapper) new WebConfiguration().webObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(service.statusTtlMs()).thenReturn(900000L);
        TargetConnectionCheckController controller = new TargetConnectionCheckController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new JacksonJsonHttpMessageConverter(om))
            .build();
    }

    @Test
    void should_return_a_saved_target_check_result_as_json() throws Exception {
        // Given
        when(service.check(eq("DEFAULT"), eq("myTarget"), any(Boolean.class)))
            .thenReturn(lastStatus(TargetConnectionCheckResult.up(42)));

        // When
        MvcResult mvcResult = mockMvc.perform(
                post(TargetConnectionCheckController.BASE_URL + "/DEFAULT/targets/myTarget/connection-check"))
            .andExpect(status().isOk())
            .andReturn();

        // Then
        TargetConnectionStatusDto dto = om.readValue(mvcResult.getResponse().getContentAsString(), TargetConnectionStatusDto.class);
        assertThat(dto.status()).isEqualTo("UP");
        assertThat(dto.reason()).isEqualTo("OK");
        assertThat(dto.durationMs()).isEqualTo(42);
        assertThat(dto.ttlMs()).isPositive();
    }

    @Test
    void should_not_force_a_probe_by_default_so_bulk_checks_reuse_recent_results() throws Exception {
        when(service.check(eq("DEFAULT"), eq("myTarget"), any(Boolean.class)))
            .thenReturn(lastStatus(TargetConnectionCheckResult.up(1)));

        mockMvc.perform(post(TargetConnectionCheckController.BASE_URL + "/DEFAULT/targets/myTarget/connection-check"))
            .andExpect(status().isOk());

        verify(service).check("DEFAULT", "myTarget", false);
    }

    @Test
    void should_force_a_probe_when_asked() throws Exception {
        when(service.check(eq("DEFAULT"), eq("myTarget"), any(Boolean.class)))
            .thenReturn(lastStatus(TargetConnectionCheckResult.up(1)));

        mockMvc.perform(post(TargetConnectionCheckController.BASE_URL + "/DEFAULT/targets/myTarget/connection-check")
                .param("force", "true"))
            .andExpect(status().isOk());

        verify(service).check("DEFAULT", "myTarget", true);
    }

    @Test
    void should_expose_the_last_known_status_of_every_target() throws Exception {
        // Given
        when(service.statusTtlMs()).thenReturn(900000L);
        when(service.lastStatuses()).thenReturn(List.of(lastStatus(TargetConnectionCheckResult.up(7))));

        // When
        MvcResult mvcResult = mockMvc.perform(get(TargetConnectionCheckController.TARGET_STATUS_URL))
            .andExpect(status().isOk())
            .andReturn();

        // Then
        List<TargetConnectionStatusDto> statuses =
            om.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
            });
        assertThat(statuses).singleElement().satisfies(dto -> {
            assertThat(dto.environmentName()).isEqualTo("DEFAULT");
            assertThat(dto.targetName()).isEqualTo("myTarget");
            assertThat(dto.status()).isEqualTo("UP");
        });
    }

    @Test
    void should_probe_a_target_definition_from_the_body() throws Exception {
        // Given
        when(service.check(any(TargetDto.class)))
            .thenReturn(TargetConnectionCheckResult.down(Reason.AUTH_FAILED, "Authentication failed (HTTP 401)", 120));

        // When
        MvcResult mvcResult = mockMvc.perform(
                post(TargetConnectionCheckController.TARGET_CHECK_URL)
                    .contentType(APPLICATION_JSON_VALUE)
                    .content("{\"name\":\"t\",\"url\":\"http://localhost\",\"environment\":\"DEFAULT\"}"))
            .andExpect(status().isOk())
            .andReturn();

        // Then
        TargetConnectionCheckResultDto dto = om.readValue(mvcResult.getResponse().getContentAsString(), TargetConnectionCheckResultDto.class);
        assertThat(dto.status()).isEqualTo("DOWN");
        assertThat(dto.reason()).isEqualTo("AUTH_FAILED");
        assertThat(dto.detail()).isEqualTo("Authentication failed (HTTP 401)");
    }

    private TargetConnectionStatus lastStatus(TargetConnectionCheckResult result) {
        return new TargetConnectionStatus("DEFAULT", "myTarget", result, Instant.now());
    }
}
