package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.security.PasswordHasher;
import com.polaris.mes.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:tenant_integration;MODE=LEGACY;DB_CLOSE_DELAY=-1")
class MultiTenantIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired WarehouseService warehouse;
    @Autowired PasswordHasher passwordHasher;

    @BeforeEach
    void prepareTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_user (id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, username VARCHAR(64) NOT NULL, display_name VARCHAR(100) NOT NULL, password_hash VARCHAR(255) NOT NULL, status INT NOT NULL, role_code VARCHAR(64) NOT NULL, last_login_at TIMESTAMP NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS inventory (id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120) NOT NULL, warehouse_code VARCHAR(64) NOT NULL, location_code VARCHAR(64) NOT NULL, batch_no VARCHAR(64), available_qty INT NOT NULL, locked_qty INT NOT NULL, unit VARCHAR(20) NOT NULL, safety_stock INT NOT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        try {
            jdbc.update("insert into sys_tenant(tenant_code, tenant_name, status) values('test-tenant', '测试租户', 1)");
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            // The same in-memory context is reused for the second test.
        }
        long testTenantId = jdbc.queryForObject("select id from sys_tenant where tenant_code=?", Long.class, "test-tenant");
        jdbc.update("delete from sys_user");
        jdbc.update("insert into sys_user(tenant_id, username, display_name, password_hash, status, role_code) values(1,'admin','管理员',?,1,'admin'),(?,'admin','测试管理员',?,1,'admin')",
                passwordHasher.hash("admin123"), testTenantId, passwordHasher.hash("admin123"));
        jdbc.update("delete from inventory");
        jdbc.update("insert into inventory(tenant_id, material_code, material_name, warehouse_code, location_code, batch_no, available_qty, locked_qty, unit, safety_stock) values(1,'A','租户一物料','W','L','B1',10,0,'件',1),(?,'B','租户二物料','W','L','B2',20,0,'件',1)", testTenantId);
    }

    @Test
    void warehouseServiceOnlyReturnsCurrentTenantRows() {
        TenantContext.Identity tenantOne = new TenantContext.Identity(1, "demo", "华东一厂", 1, "admin", "admin");
        long testTenantId = jdbc.queryForObject("select id from sys_tenant where tenant_code=?", Long.class, "test-tenant");
        long testUserId = jdbc.queryForObject("select id from sys_user where tenant_id=? and username=?", Long.class, testTenantId, "admin");
        TenantContext.Identity tenantTwo = new TenantContext.Identity(testTenantId, "test-tenant", "测试租户", testUserId, "admin", "admin");

        var one = TenantContext.run(tenantOne, () -> warehouse.listInventory(null, null, null));
        var two = TenantContext.run(tenantTwo, () -> warehouse.listInventory(null, null, null));

        org.junit.jupiter.api.Assertions.assertEquals(1, one.size());
        org.junit.jupiter.api.Assertions.assertEquals("A", value(one.get(0), "material_code"));
        org.junit.jupiter.api.Assertions.assertEquals(1, two.size());
        org.junit.jupiter.api.Assertions.assertEquals("B", value(two.get(0), "material_code"));
    }

    @Test
    void loginBindsTenantIntoTokenAndRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/warehouse/inventory"))
                .andExpect(status().isUnauthorized());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"tenantCode\":\"test-tenant\",\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.tenant.code", is("test-tenant")))
                .andReturn().getResponse().getContentAsString();
        String token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("data").path("token").asText();

        mockMvc.perform(get("/api/warehouse/inventory").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].MATERIAL_CODE", is("B")));
    }

    private static Object value(Map<String, Object> row, String key) {
        return row.containsKey(key) ? row.get(key) : row.get(key.toUpperCase());
    }
}
