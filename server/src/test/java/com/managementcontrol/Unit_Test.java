package com.managementcontrol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.managementcontrol.domain.AdminUser;
import com.managementcontrol.domain.Role;
import com.managementcontrol.repository.AdminUserRepository;
import com.managementcontrol.web.agent.dto.EnrollRequest;
import com.managementcontrol.web.agent.dto.EnrollResponse;
import com.managementcontrol.web.agent.dto.UsageSessionRequest;
import com.managementcontrol.web.agent.dto.UsageSessionBatchRequest;
import com.managementcontrol.web.agent.dto.BrowsingActivityRequest;
import com.managementcontrol.web.agent.dto.BrowsingBatchRequest;
import com.managementcontrol.web.agent.dto.ProcessEventRequest;
import com.managementcontrol.web.agent.dto.ProcessEventBatchRequest;
import com.managementcontrol.web.admin.dto.CreateCommandRequest;
import com.managementcontrol.web.admin.dto.CreateEnrollmentCodeRequest;
import com.managementcontrol.web.admin.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Наскрізні (E2E) сценарії "очима користувача": адміністратор у панелі керування
 * та агент на клієнтському пристрої. Кожен тест імітує повний шлях дії через
 * систему (HTTP-запит → бізнес-логіка → БД → відповідь), а не окремий метод в ізоляції.
 *
 * Верхній, найменший шар піраміди тестування — на відміну від unit-тестів,
 * тут свідомо перевіряється взаємодія кількох компонентів разом.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Unit_Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- Сценарій 1 ----------

    @Test
    void adminLogin_ShouldReturnJwtAndAllowAccess_WhenCredentialsAreValid() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/v1/admin/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ---------- Сценарій 2 ----------

    @Test
    void adminLogin_ShouldReturnUnauthorized_WhenPasswordIsWrong() throws Exception {
        LoginRequest request = new LoginRequest("admin", "wrong-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Сценарій 3 ----------

    @Test
    void deviceEnrollment_ShouldSucceed_WhenAdminIssuedCodeIsUsedByAgent() throws Exception {
        String adminToken = loginAsAdmin();

        CreateEnrollmentCodeRequest codeRequest = new CreateEnrollmentCodeRequest(
                "E2E тест", "manager-e2e", null, Duration.ofHours(1));

        String codeBody = mockMvc.perform(post("/api/v1/admin/enrollment-codes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(codeRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String code = objectMapper.readTree(codeBody).get("code").asText();

        EnrollRequest enrollRequest = new EnrollRequest(code, "device-e2e-" + UUID.randomUUID(),
                "e2e-pc", "Windows 11", "11.0", "0.1.0", "00:11:22:33:44:66",
                "192.168.1.20", "manager-e2e");

        mockMvc.perform(post("/api/v1/agent/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enrollRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.startsWith("mc_dev_")));
    }

    // ---------- Сценарій 4 ----------

    @Test
    void deviceLockAndUnlock_ShouldChangeDeviceStatus_WhenAdminIssuesCommands() throws Exception {
        String adminToken = loginAsAdmin();
        Long deviceDbId = enrollNewDevice(adminToken, "device-lock-" + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/admin/devices/" + deviceDbId + "/lock")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("reason", "E2E: перевірка блокування"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"))
                .andExpect(jsonPath("$.lockReason").value("E2E: перевірка блокування"));

        mockMvc.perform(get("/api/v1/admin/devices/" + deviceDbId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        mockMvc.perform(post("/api/v1/admin/devices/" + deviceDbId + "/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ---------- Сценарій 5 ----------

    @Test
    void remoteCommand_ShouldReachAgentAndBeAcknowledged_WhenFullCycleCompletes() throws Exception {
        String adminToken = loginAsAdmin();
        String deviceAgentToken = enrollNewDeviceAndGetAgentToken(adminToken, "device-cmd-" + UUID.randomUUID());
        Long deviceDbId = deviceIdFromToken(adminToken, deviceAgentToken);

        CreateCommandRequest commandRequest = new CreateCommandRequest(
                com.managementcontrol.domain.RemoteCommandType.TAKE_SCREENSHOT, null, Duration.ofMinutes(5));

        mockMvc.perform(post("/api/v1/admin/devices/" + deviceDbId + "/commands")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commandRequest)))
                .andExpect(status().isOk());

        String commandsBody = mockMvc.perform(get("/api/v1/agent/commands")
                        .header("Authorization", "Bearer " + deviceAgentToken)
                        .param("timeoutSeconds", "5"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode commands = objectMapper.readTree(commandsBody);
        assertThat(commands.isArray()).isTrue();
        assertThat(commands.size()).isGreaterThanOrEqualTo(1);
        long commandId = commands.get(0).get("id").asLong();

        mockMvc.perform(post("/api/v1/agent/commands/" + commandId + "/ack")
                        .header("Authorization", "Bearer " + deviceAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":" + commandId + ",\"status\":\"EXECUTED\",\"result\":\"OK\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/devices/" + deviceDbId + "/commands")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("EXECUTED"));
    }

    // ---------- Сценарій 6 ----------

    @Test
    void dailyReport_ShouldAggregateTelemetry_WhenAgentSubmitsMultipleBatches() throws Exception {
        String adminToken = loginAsAdmin();
        String agentToken = enrollNewDeviceAndGetAgentToken(adminToken, "device-report-" + UUID.randomUUID());
        Long deviceDbId = deviceIdFromToken(adminToken, agentToken);

        Instant now = Instant.now();

        UsageSessionBatchRequest usageBatch = new UsageSessionBatchRequest(List.of(
                new UsageSessionRequest("vscode.exe", "E2E", "C:\\vscode.exe",
                        now.minusSeconds(1200), now, 1200, false, true)));
        mockMvc.perform(post("/api/v1/agent/usage-sessions")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usageBatch)))
                .andExpect(status().isOk());

        BrowsingBatchRequest browsingBatch = new BrowsingBatchRequest(List.of(
                new BrowsingActivityRequest("https://e2e-test.example.com/page", "e2e-test.example.com",
                        "chrome", "E2E", now.minusSeconds(400), now, 400, false)));
        mockMvc.perform(post("/api/v1/agent/browsing")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(browsingBatch)))
                .andExpect(status().isOk());

        ProcessEventBatchRequest processBatch = new ProcessEventBatchRequest(List.of(
                new ProcessEventRequest("vscode.exe", "C:\\vscode.exe", null, now, "STARTED")));
        mockMvc.perform(post("/api/v1/agent/processes")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(processBatch)))
                .andExpect(status().isOk());

        String today = now.truncatedTo(ChronoUnit.DAYS).toString().substring(0, 10);

        mockMvc.perform(get("/api/v1/admin/reports/daily")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("deviceId", String.valueOf(deviceDbId))
                        .param("date", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSeconds").value(1200))
                .andExpect(jsonPath("$.topApps[0].name").value("vscode.exe"))
                .andExpect(jsonPath("$.topSites[0].name").value("e2e-test.example.com"));
    }

    // ---------- Сценарій 7 ----------

    @Test
    void adminEndpoints_ShouldRejectAccess_WhenTokenMissingOrRoleInsufficient() throws Exception {
        mockMvc.perform(get("/api/v1/admin/devices"))
                .andExpect(status().isUnauthorized());

        createViewerIfAbsent();
        LoginRequest viewerLogin = new LoginRequest("viewer-e2e", "viewer123");
        String viewerBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(viewerLogin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String viewerToken = objectMapper.readTree(viewerBody).get("token").asText();

        // VIEWER бачить список пристроїв (дозволено ADMIN та VIEWER)
        mockMvc.perform(get("/api/v1/admin/devices")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        // але не може блокувати пристрій (дія лише для ADMIN)
        String adminToken = loginAsAdmin();
        Long deviceDbId = enrollNewDevice(adminToken, "device-viewer-" + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/admin/devices/" + deviceDbId + "/lock")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
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

    private void createViewerIfAbsent() {
        if (adminUserRepository.existsByUsername("viewer-e2e")) {
            return;
        }
        AdminUser viewer = new AdminUser();
        viewer.setUsername("viewer-e2e");
        viewer.setPasswordHash(passwordEncoder.encode("viewer123"));
        viewer.setFullName("E2E Viewer");
        viewer.setRole(Role.VIEWER);
        viewer.setEnabled(true);
        viewer.setCreatedAt(Instant.now());
        adminUserRepository.save(viewer);
    }

    private String enrollNewDeviceAndGetAgentToken(String adminToken, String deviceId) throws Exception {
        CreateEnrollmentCodeRequest codeRequest = new CreateEnrollmentCodeRequest(
                "E2E", "manager-e2e", null, Duration.ofHours(1));

        String codeBody = mockMvc.perform(post("/api/v1/admin/enrollment-codes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(codeRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(codeBody).get("code").asText();

        EnrollRequest enrollRequest = new EnrollRequest(code, deviceId, "e2e-pc", "Windows 11", "11.0",
                "0.1.0", "00:11:22:33:44:77", "192.168.1.30", "manager-e2e");

        String enrollBody = mockMvc.perform(post("/api/v1/agent/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enrollRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(enrollBody).get("token").asText();
    }

    private Long enrollNewDevice(String adminToken, String deviceId) throws Exception {
        String agentToken = enrollNewDeviceAndGetAgentToken(adminToken, deviceId);
        return deviceIdFromToken(adminToken, agentToken);
    }

    private Long deviceIdFromToken(String adminToken, String agentToken) throws Exception {
        // Heartbeat агентом, щоб гарантовано знайти пристрій за списком (сортування за id desc)
        mockMvc.perform(post("/api/v1/agent/heartbeat")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        String listBody = mockMvc.perform(get("/api/v1/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(listBody).get("content").get(0).get("id").asLong();
    }
}
