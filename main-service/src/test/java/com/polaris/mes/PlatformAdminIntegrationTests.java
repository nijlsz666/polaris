package com.polaris.mes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.polaris.mes.security.PasswordHasher;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:platform_admin_integration;MODE=LEGACY;DB_CLOSE_DELAY=-1")
class PlatformAdminIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordHasher passwordHasher;
    private final ObjectMapper mapper = new ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void seedCustomerAdmin() {
        try {
            jdbc.update("insert into sys_user(tenant_id, username, display_name, password_hash, status, role_code) values(1,'admin','管理员',?,1,'admin')", passwordHasher.hash("admin123"));
        } catch (org.springframework.dao.DataAccessException ignored) {
            // The in-memory context may be reused between test methods.
        }
    }

    @Test
    void platformTenantIsBootstrappedAndCanOperateAcrossCustomerTenants() throws Exception {
        String token = login("polaris-admin", "platform-admin", "admin123");

        mockMvc.perform(get("/api/platform/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerTenants", is(1)))
                .andExpect(jsonPath("$.data.activeTenants", is(1)));
        mockMvc.perform(get("/api/auth/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].tenant_code", hasItem("polaris-admin")));
        mockMvc.perform(post("/api/platform/points/adjust").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"tenantId\":1,\"amount\":100,\"reason\":\"平台活动奖励\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance", is(100)));
    }

    @Test
    void customerAdminCannotCallPlatformOperations() throws Exception {
        String token = login("demo", "admin", "admin123");
        mockMvc.perform(get("/api/platform/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("当前用户不是平台总管理员")));
    }

    @Test
    void platformCanConfigureAndTrackStorageBilling() throws Exception {
        String token = login("polaris-admin", "platform-admin", "admin123");

        mockMvc.perform(get("/api/platform/storage?tenantId=1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quota_bytes", is(10737418240L)))
                .andExpect(jsonPath("$.data.used_bytes", is(0)));

        mockMvc.perform(put("/api/platform/storage").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"tenantId\":1,\"quotaBytes\":21474836480,\"warningThresholdPercent\":15,\"unitPricePerGbMonth\":1.5,\"reason\":\"存储套餐升级\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quota_bytes", is(21474836480L)))
                .andExpect(jsonPath("$.data.warning_threshold_percent", is(15)));

        mockMvc.perform(post("/api/platform/storage/usage").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"tenantId\":1,\"usedBytes\":5368709120,\"reason\":\"对象存储同步\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used_bytes", is(5368709120L)))
                .andExpect(jsonPath("$.data.ledger[0].action_type", is("CONSUME")));
    }

    private String login(String tenantCode, String username, String password) throws Exception {
        String body = "{\"tenantCode\":\"" + tenantCode + "\",\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String response = mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = mapper.readTree(response);
        return root.path("data").path("token").asText();
    }
}
