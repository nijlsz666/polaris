package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.ErpService;
import com.polaris.mes.service.PlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class ErpFlowIntegrationTests {
    @Autowired ErpService erp;
    @Autowired PlatformService platform;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanRecords() {
        jdbc.execute("create table if not exists inventory(id bigint auto_increment primary key, tenant_id bigint not null, material_code varchar(64) not null, material_name varchar(120), warehouse_code varchar(64) not null, location_code varchar(64) not null, batch_no varchar(64), available_qty int, locked_qty int, reserved_qty int, in_transit_qty int, unit varchar(20), safety_stock int, stock_status varchar(20), expiry_date date, version_no bigint, updated_at timestamp default current_timestamp)");
        jdbc.update("delete from erp_business_record where tenant_id=1 and record_no like 'TEST-ERP-%'");
        jdbc.update("delete from bom_item where tenant_id=1 and bom_id in (select id from bom where tenant_id=1 and bom_code like 'TEST-ERP-%')");
        jdbc.update("delete from bom where tenant_id=1 and bom_code like 'TEST-ERP-%'");
        jdbc.update("delete from inventory where tenant_id=1 and material_code like 'TEST-ERP-%'");
    }

    @Test
    void salesRecordCanBeCreatedListedAndTransitioned() {
        TenantContext.Identity identity = new TenantContext.Identity(1, "demo", "华东一厂", 1, "admin", "admin");
        TenantContext.run(identity, () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "orders"); payload.put("no", "TEST-ERP-SO-001"); payload.put("name", "AX90 控制器");
            payload.put("partner", "测试客户"); payload.put("amount", "268000"); payload.put("remark", "ERP 集成测试");

            Map<String, Object> created = erp.createRecord("sales", payload);
            assertEquals("REVIEW", created.get("status"));
            assertEquals("¥ 268,000", created.get("amount"));

            List<Map<String, Object>> records = erp.listRecords("sales", "orders", "TEST-ERP", null);
            assertEquals(1, records.size());
            assertEquals(268000, ((Number) erp.overview().get("salesAmount")).intValue());
            Map<String, Object> progressed = erp.transition("sales", ((Number) created.get("id")).longValue(), Map.of("status", "CONFIRMED"));
            assertEquals("CONFIRMED", progressed.get("status"));
        });
    }

    @Test
    void invalidTransitionIsRejected() {
        TenantContext.Identity identity = new TenantContext.Identity(1, "demo", "华东一厂", 1, "admin", "admin");
        TenantContext.run(identity, () -> {
            Map<String, Object> payload = Map.of("type", "orders", "no", "TEST-ERP-SO-002", "name", "测试订单", "partner", "测试客户", "amount", "100");
            Map<String, Object> created = erp.createRecord("sales", payload);
            assertThrows(IllegalArgumentException.class, () -> erp.transition("sales", ((Number) created.get("id")).longValue(), Map.of("status", "COMPLETED")));
        });
    }

    @Test
    void procurementRequisitionStoresHeaderAndLines() {
        TenantContext.Identity identity = new TenantContext.Identity(1, "demo", "华东一厂", 1, "planner", "planner");
        TenantContext.run(identity, () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "requisitions");
            payload.put("no", "TEST-ERP-PR-001");
            payload.put("name", "生产辅料请购");
            payload.put("partner", "测试供应商");
            payload.put("departmentCode", "PURCHASE");
            payload.put("requesterCode", "planner");
            payload.put("deliveryDate", "2026-09-01");
            payload.put("lines", List.of(Map.of(
                    "materialCode", "TEST-ERP-RM-001", "materialName", "测试电机", "specification", "24V",
                    "unit", "件", "requestedQty", 12, "unitPrice", 35, "taxRate", 13, "requiredDate", "2026-09-01")));

            Map<String, Object> created = erp.createRecord("procurement", payload);
            assertEquals("REVIEW", created.get("status"));
            assertEquals(1, ((Number) created.get("lineCount")).intValue());
            assertEquals("PURCHASE", created.get("departmentCode"));
            Map<String, Object> line = ((List<Map<String, Object>>) created.get("lines")).get(0);
            assertEquals("TEST-ERP-RM-001", line.get("materialCode"));
            assertEquals(12, ((Number) line.get("requestedQty")).intValue());
            assertEquals("2026-09-01", String.valueOf(line.get("requiredDate")));
        });
    }

    @Test
    void mrpExplodesReleasedBomAndCalculatesShortage() {
        TenantContext.Identity identity = new TenantContext.Identity(1, "demo", "华东一厂", 1, "admin", "admin");
        TenantContext.run(identity, () -> {
            Map<String, Object> bom = new LinkedHashMap<>();
            bom.put("bomCode", "TEST-ERP-BOM-001"); bom.put("productCode", "TEST-ERP-FG-001");
            bom.put("productName", "测试控制器"); bom.put("status", "RELEASED");
            bom.put("items", List.of(Map.of("materialCode", "TEST-ERP-RM-001", "materialName", "测试电机", "quantity", 5, "unit", "件", "lossRate", 10)));
            platform.insertBom(bom);
            jdbc.update("insert into inventory(tenant_id,material_code,material_name,warehouse_code,location_code,batch_no,available_qty,locked_qty,reserved_qty,in_transit_qty,unit,safety_stock,stock_status) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    1, "TEST-ERP-RM-001", "测试电机", "WH-TEST", "A-01", "B-01", 20, 0, 0, 8, "件", 0, "AVAILABLE");

            Map<String, Object> mrp = platform.calculateMrp(Map.of("productCode", "TEST-ERP-FG-001", "planQty", 10));
            assertEquals(1, ((Number) mrp.get("materialCount")).intValue());
            assertEquals(1, ((Number) mrp.get("shortageCount")).intValue());
            assertEquals(35, ((Number) mrp.get("shortageQty")).intValue());
            Map<String, Object> requirement = ((List<Map<String, Object>>) mrp.get("requirements")).get(0);
            assertEquals(55, requirement.get("requiredQty"));
            assertEquals(20, requirement.get("availableQty"));
            assertEquals("采购 / 委外", requirement.get("suggestion"));
        });
    }
}
