/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.target.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.enedis.chutney.config.web.WebConfiguration;
import fr.enedis.chutney.environment.api.target.dto.TargetDto;
import fr.enedis.chutney.target.api.dto.TargetConnectionCheckResultDto;
import fr.enedis.chutney.target.domain.TargetConnectionCheckResult;
import fr.enedis.chutney.target.domain.TargetConnectionCheckResult.Reason;
import fr.enedis.chutney.target.domain.TargetConnectionCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class TargetConnectionCheckControllerTest {

    private final TargetConnectionCheckService service = mock(TargetConnectionCheckService.class);
    private final JsonMapper om = (JsonMapper) new WebConfiguration().webObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TargetConnectionCheckController controller = new TargetConnectionCheckController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new JacksonJsonHttpMessageConverter(om))
            .build();
    }

    @Test
    void should_return_a_saved_target_check_result_as_json() throws Exception {
        // Given
        when(service.check("DEFAULT", "myTarget")).thenReturn(TargetConnectionCheckResult.up(42));

        // When
        MvcResult mvcResult = mockMvc.perform(
                post(TargetConnectionCheckController.BASE_URL + "/DEFAULT/targets/myTarget/connection-check"))
            .andExpect(status().isOk())
            .andReturn();

        // Then
        TargetConnectionCheckResultDto dto = om.readValue(mvcResult.getResponse().getContentAsString(), TargetConnectionCheckResultDto.class);
        assertThat(dto.status()).isEqualTo("UP");
        assertThat(dto.reason()).isEqualTo("OK");
        assertThat(dto.durationMs()).isEqualTo(42);
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
}
