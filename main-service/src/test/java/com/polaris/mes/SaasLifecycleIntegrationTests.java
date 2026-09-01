package com.polaris.mes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:saas_lifecycle;MODE=LEGACY;DB_CLOSE_DELAY=-1")
class SaasLifecycleIntegrationTests {
    @Autowired MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void customerCanCreateWorkspaceAndReceiveAdminSession() throws Exception {
        String code = "acme" + System.nanoTime();
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"tenantCode\":\"" + code + "\",\"tenantName\":\"Acme 智造\",\"displayName\":\"张工\",\"username\":\"owner\",\"password\":\"StrongPass!9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.tenant.code", is(code)))
                .andExpect(jsonPath("$.data.user.roleCode", is("admin")))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(response).path("data").path("token").asText();

        mockMvc.perform(get("/api/tenant/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.TENANT_CODE", is(code)))
                .andExpect(jsonPath("$.data.user_count", is(1)));
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].TITLE", is("欢迎使用 Polaris")));
        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", greaterThan(0)));
    }

    @Test
    void duplicateWorkspaceCodeIsRejected() throws Exception {
        String code = "dup" + System.nanoTime();
        String body = "{\"tenantCode\":\"" + code + "\",\"tenantName\":\"重复测试\",\"displayName\":\"管理员\",\"username\":\"owner\",\"password\":\"StrongPass!9\"}";
        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("租户编码已被注册")));
    }
}
