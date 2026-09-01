package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:legacy_inventory_compat;MODE=LEGACY;DB_CLOSE_DELAY=-1")
class LegacyInventoryCompatibilityIntegrationTests {
    private static final TenantContext.Identity DEMO = new TenantContext.Identity(1, "demo", "华东一厂", 1, "warehouse", "warehouse");

    @Autowired JdbcTemplate jdbc;
    @Autowired WarehouseService warehouse;

    @BeforeEach
    void prepareLegacyInventoryTable() {
        jdbc.execute("DROP TABLE IF EXISTS inventory");
        jdbc.execute("CREATE TABLE inventory (id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120) NOT NULL, warehouse_code VARCHAR(64) NOT NULL, location_code VARCHAR(64) NOT NULL, batch_no VARCHAR(64), available_qty INT NOT NULL, locked_qty INT NOT NULL, unit VARCHAR(20) NOT NULL, safety_stock INT NOT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO inventory(tenant_id, material_code, material_name, warehouse_code, location_code, batch_no, available_qty, locked_qty, unit, safety_stock) VALUES(1,?,?,?,?,?,?,?,?,?)", "LEGACY-001", "兼容测试物料", "WH-TEST", "A-01", "B-001", 8, 0, "件", 10);
    }

    @Test
    void inventoryReadDoesNotFailWhenNewColumnsAreMissing() {
        List<Map<String, Object>> rows = TenantContext.run(DEMO, () -> warehouse.listInventory(null, null, null));

        assertEquals(1, rows.size());
        assertEquals(0, ((Number) rows.get(0).get("reserved_qty")).intValue());
        assertEquals("AVAILABLE", rows.get(0).get("stock_status"));
    }
}
