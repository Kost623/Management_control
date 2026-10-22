package com.managementcontrol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.managementcontrol.domain.EnrollmentCode;
import com.managementcontrol.repository.DeviceRepository;
import com.managementcontrol.service.EnrollmentService;
import com.managementcontrol.web.agent.dto.BrowsingActivityRequest;
import com.managementcontrol.web.agent.dto.BrowsingBatchRequest;
import com.managementcontrol.web.agent.dto.EnrollRequest;
import com.managementcontrol.web.agent.dto.EnrollResponse;
import com.managementcontrol.web.agent.dto.UsageSessionBatchRequest;
import com.managementcontrol.web.agent.dto.UsageSessionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Наскрізний тест: реєстрація пристрою → прийом телеметрії → звіт.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Test
    void агент_реєструється_і_передає_дані() throws Exception {
        EnrollmentCode code = enrollmentService.createCode("test", "manager-1", null, java.time.Duration.ofHours(1));

        EnrollRequest enrollRequest = new EnrollRequest(code.getCode(), "device-test-1", "work-pc",
                "Windows 11", "11.0", "0.1.0", "00:11:22:33:44:55", "192.168.1.10", "manager");

        String enrollBody = mockMvc.perform(post("/api/v1/agent/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enrollRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        EnrollResponse enrollResponse = objectMapper.readValue(enrollBody, EnrollResponse.class);
        String token = enrollResponse.token();
        assertThat(token).startsWith("mc_dev_");
        assertThat(deviceRepository.findByDeviceId("device-test-1")).isPresent();

        Instant now = Instant.now();
        UsageSessionBatchRequest usageBatch = new UsageSessionBatchRequest(List.of(
                new UsageSessionRequest("chrome.exe", "Приклад", "C:\\chrome.exe",
                        now.minusSeconds(600), now, 600, false, true)));

        mockMvc.perform(post("/api/v1/agent/usage-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usageBatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));

        BrowsingBatchRequest browsingBatch = new BrowsingBatchRequest(List.of(
                new BrowsingActivityRequest("https://example.com/page", "example.com", "chrome",
                        "Приклад", now.minusSeconds(300), now, 300, false)));

        mockMvc.perform(post("/api/v1/agent/browsing")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(browsingBatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));

        // Без токена агентський API не доступний
        mockMvc.perform(post("/api/v1/agent/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // Звіт доступний лише адміністратору
        mockMvc.perform(get("/api/v1/admin/reports/daily")
                        .param("deviceId", String.valueOf(enrollResponse.deviceDbId())))
                .andExpect(status().isUnauthorized());
    }
}
