package com.managementcontrol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.managementcontrol.domain.Device;
import com.managementcontrol.domain.DeviceStatus;
import com.managementcontrol.domain.EnrollmentCode;
import com.managementcontrol.repository.DeviceRepository;
import com.managementcontrol.repository.EnrollmentCodeRepository;
import com.managementcontrol.web.admin.dto.CreateEnrollmentCodeRequest;
import com.managementcontrol.web.admin.dto.LoginRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Testing: HTTP Request → REST API → Business Logic → Data Access → Database.
 * Використовуються реальні компоненти (MockMvc + справжня H2-база тестового профілю),
 * без mock-об'єктів — перевіряється фактична взаємодія шарів, включно з тим,
 * що дані дійсно потрапляють/оновлюються/відсутні в базі.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationApiDbTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EnrollmentCodeRepository enrollmentCodeRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @AfterEach
    void cleanUp() {
        // Очищення тестових даних, щоб результати одного тесту не впливали на інші.
        enrollmentCodeRepository.deleteAll();
        deviceRepository.deleteAll();
    }

    // ---------- 1. Створення коду реєстрації: API -> Service -> Repository -> DB ----------

    @Test
    void createEnrollmentCode_ShouldPersistCodeInDatabase() throws Exception {
        String adminToken = loginAsAdmin();

        CreateEnrollmentCodeRequest request = new CreateEnrollmentCodeRequest(
                "Integration test", "manager-it", null, Duration.ofHours(1));

        String body = mockMvc.perform(post("/api/v1/admin/enrollment-codes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String code = objectMapper.readTree(body).get("code").asText();

        Optional<EnrollmentCode> saved = enrollmentCodeRepository.findByCode(code);
        assertThat(saved).isPresent();
        assertThat(saved.get().getLabel()).isEqualTo("Integration test");
        assertThat(saved.get().getAssignedTo()).isEqualTo("manager-it");
        assertThat(saved.get().isActive()).isTrue();
    }

    // ---------- 2. Блокування пристрою: API -> Service -> DB (оновлення стану) ----------

    @Test
    void lockDevice_ShouldUpdateStatusInDatabase() throws Exception {
        String adminToken = loginAsAdmin();
        Long deviceDbId = createEnrolledDevice(adminToken, "device-it-lock-" + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/admin/devices/" + deviceDbId + "/lock")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("reason", "Integration test lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        Device fromDb = deviceRepository.findById(deviceDbId).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(DeviceStatus.LOCKED);
        assertThat(fromDb.getLockReason()).isEqualTo("Integration test lock");
        assertThat(fromDb.getLockedAt()).isNotNull();
    }

    // ---------- 3. Пагінація списку пристроїв на реальних даних ----------

    @Test
    void listDevices_ShouldReturnPagedResults_WhenMultipleDevicesExist() throws Exception {
        String adminToken = loginAsAdmin();
        createEnrolledDevice(adminToken, "device-it-page-1-" + UUID.randomUUID());
        createEnrolledDevice(adminToken, "device-it-page-2-" + UUID.randomUUID());
        createEnrolledDevice(adminToken, "device-it-page-3-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.size").value(2));
    }

    // ---------- 4. Валідація вхідних даних: відсутнє обов'язкове поле ----------

    @Test
    void createCommand_ShouldReturn400WithValidationDetails_WhenTypeIsMissing() throws Exception {
        String adminToken = loginAsAdmin();
        Long deviceDbId = createEnrolledDevice(adminToken, "device-it-validation-" + UUID.randomUUID());

        String invalidBody = "{\"payload\": \"no type field\"}";

        mockMvc.perform(post("/api/v1/admin/devices/" + deviceDbId + "/commands")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Помилка валідації запиту"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("type")));
    }

    // ---------- 5. Помилковий сценарій: запит неіснуючого пристрою ----------

    @Test
    void getDevice_ShouldReturn404WithApiErrorStructure_WhenDeviceDoesNotExist() throws Exception {
        String adminToken = loginAsAdmin();

        mockMvc.perform(get("/api/v1/admin/devices/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("999999")))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/devices/999999"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // ---------- Допоміжні методи ----------

    private String loginAsAdmin() throws Exception {
        LoginRequest request = new LoginRequest("admin", "admin123");
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private Long createEnrolledDevice(String adminToken, String deviceId) throws Exception {
        CreateEnrollmentCodeRequest codeRequest = new CreateEnrollmentCodeRequest(
                "IT", "manager-it", null, Duration.ofHours(1));

        String codeBody = mockMvc.perform(post("/api/v1/admin/enrollment-codes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(codeRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(codeBody).get("code").asText();

        com.managementcontrol.web.agent.dto.EnrollRequest enrollRequest =
                new com.managementcontrol.web.agent.dto.EnrollRequest(code, deviceId, "it-pc",
                        "Windows 11", "11.0", "0.1.0", "00:11:22:33:55:" + (int) (Math.random() * 90 + 10),
                        "192.168.1.60", "manager-it");

        String enrollBody = mockMvc.perform(post("/api/v1/agent/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enrollRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(enrollBody).get("deviceDbId").asLong();
    }
}
