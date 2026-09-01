package com.polaris.mes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.mes.security.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:traffic_integration;MODE=LEGACY;DB_CLOSE_DELAY=-1")
class TenantTrafficIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordHasher passwordHasher;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void seedCustomerAdmin() {
        try {
            jdbc.update("insert into sys_user(tenant_id, username, display_name, password_hash, status, role_code) values(1,'admin','管理员',?,1,'admin')", passwordHasher.hash("admin123"));
        } catch (org.springframework.dao.DataAccessException ignored) {
            // The in-memory context may be reused between test methods.
        }
        jdbc.update("update tenant_traffic_account set quota_bytes=1073741824, used_bytes=0, warning_threshold_percent=10 where tenant_id=1");
    }

    @Test
    void zeroQuotaIsVisibleButBusinessRequestsAreRejected() throws Exception {
        String platformToken = login("polaris-admin", "platform-admin", "admin123");
        mockMvc.perform(put("/api/platform/traffic").header("Authorization", "Bearer " + platformToken)
                        .contentType("application/json")
                        .content("{\"tenantId\":1,\"quotaBytes\":0,\"warningThresholdPercent\":10,\"reason\":\"暂停使用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quota_bytes", is(0)));

        String customerToken = login("demo", "admin", "admin123");
        mockMvc.perform(get("/api/tenant/traffic").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exhausted", is(true)));
        mockMvc.perform(get("/api/warehouse/inventory").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.message", is("租户流量已用尽，请联系总管理员分配流量")));
    }

    private String login(String tenantCode, String username, String password) throws Exception {
        String body = "{\"tenantCode\":\"" + tenantCode + "\",\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String response = mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode root = mapper.readTree(response);
        return root.path("data").path("token").asText();
    }
}
