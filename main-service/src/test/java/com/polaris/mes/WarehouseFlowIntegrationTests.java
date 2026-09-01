package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class WarehouseFlowIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired WarehouseService warehouse;

    @BeforeEach
    void prepareLegacyTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS inventory (id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120) NOT NULL, warehouse_code VARCHAR(64) NOT NULL, location_code VARCHAR(64) NOT NULL, batch_no VARCHAR(64), available_qty INT NOT NULL, locked_qty INT NOT NULL, reserved_qty INT NOT NULL DEFAULT 0, in_transit_qty INT NOT NULL DEFAULT 0, unit VARCHAR(20) NOT NULL, safety_stock INT NOT NULL, stock_status VARCHAR(20) DEFAULT 'AVAILABLE', expiry_date DATE NULL, version_no BIGINT DEFAULT 0, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS material_transaction (id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, transaction_no VARCHAR(100) NOT NULL, transaction_type VARCHAR(30) NOT NULL, material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120), warehouse_code VARCHAR(64) NOT NULL, location_code VARCHAR(64), batch_no VARCHAR(64), quantity INT NOT NULL, unit VARCHAR(20) NOT NULL, operator_name VARCHAR(100) NOT NULL, source_doc_no VARCHAR(64), document_no VARCHAR(100), from_warehouse_code VARCHAR(64), from_location_code VARCHAR(64), to_warehouse_code VARCHAR(64), to_location_code VARCHAR(64), reason_code VARCHAR(64), idempotency_key VARCHAR(120), status VARCHAR(20) DEFAULT 'COMPLETED', remark VARCHAR(255), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS barcode (id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, barcode VARCHAR(100) NOT NULL, barcode_type VARCHAR(30) NOT NULL, material_code VARCHAR(64), batch_no VARCHAR(64), status VARCHAR(20) NOT NULL, source_doc_no VARCHAR(64), warehouse_code VARCHAR(64), location_code VARCHAR(64), printed_count INT DEFAULT 0, voided_at TIMESTAMP NULL, printed_at TIMESTAMP NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("delete from material_transaction where tenant_id=1 and material_code='TEST-WMS-001'");
        jdbc.update("delete from inventory where tenant_id=1 and material_code='TEST-WMS-001'");
        jdbc.update("delete from wh_batch where tenant_id=1 and material_code='TEST-WMS-001'");
        jdbc.update("delete from wh_material where tenant_id=1 and material_code='TEST-WMS-001'");
        jdbc.update("insert into inventory(tenant_id, material_code, material_name, warehouse_code, location_code, batch_no, available_qty, locked_qty, reserved_qty, in_transit_qty, unit, safety_stock, stock_status, version_no) values(1,'TEST-WMS-001','测试物料','WH-TEST','A-01', 'B-TEST', 10,0,0,0,'件',1,'AVAILABLE',0)");
    }

    @Test
    void receiptIssueAndTraceFlow() {
        TenantContext.Identity identity = new TenantContext.Identity(1, "demo", "华东一厂", 1, "warehouse", "warehouse");
        TenantContext.run(identity, () -> {
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("transactionType", "RECEIPT"); receipt.put("materialCode", "TEST-WMS-001"); receipt.put("materialName", "测试物料");
            receipt.put("warehouseCode", "WH-TEST"); receipt.put("locationCode", "A-01"); receipt.put("batchNo", "B-TEST"); receipt.put("quantity", 5); receipt.put("unit", "件"); receipt.put("qualityStatus", "PASSED");
            Map<String, Object> receiptResult = warehouse.postTransaction(receipt, "warehouse");
            assertEquals("COMPLETED", receiptResult.get("status"));

            Map<String, Object> issue = new LinkedHashMap<>(receipt);
            issue.put("transactionType", "ISSUE"); issue.put("quantity", 3); issue.put("sourceDocNo", "WO-TEST-001");
            warehouse.postTransaction(issue, "warehouse");

            Map<String, Object> row = warehouse.listInventory("TEST-WMS-001", "WH-TEST", null).get(0);
            assertEquals(12, ((Number) row.get("available_qty")).intValue());
            assertEquals(2, warehouse.trace("TEST-WMS-001", "B-TEST", null).size());
            assertTrue(warehouse.listDocuments(null, null).stream().anyMatch(item -> String.valueOf(item.get("document_type")).equals("RECEIPT")));
        });
    }

    @Test
    void frozenStockRejectsOutboundWithoutForce() {
        TenantContext.Identity identity = new TenantContext.Identity(1, "demo", "华东一厂", 1, "warehouse", "warehouse");
        TenantContext.run(identity, () -> {
            jdbc.update("update inventory set stock_status='FROZEN' where tenant_id=1 and material_code='TEST-WMS-001'");
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("transactionType", "ISSUE"); issue.put("materialCode", "TEST-WMS-001"); issue.put("warehouseCode", "WH-TEST");
            issue.put("locationCode", "A-01"); issue.put("batchNo", "B-TEST"); issue.put("quantity", 1); issue.put("unit", "件");
            assertThrows(IllegalArgumentException.class, () -> warehouse.postTransaction(issue, "warehouse"));
        });
    }

    @Test
    void pendingQualityBatchRejectsOutboundUntilReleased() {
        TenantContext.Identity identity = new TenantContext.Identity(1, "demo", "华东一厂", 1, "warehouse", "warehouse");
        TenantContext.run(identity, () -> {
            jdbc.update("insert into wh_batch(tenant_id, material_code, batch_no, quality_status, batch_status) values(?,?,?,?,?)", 1, "TEST-WMS-001", "B-TEST", "PENDING", "ACTIVE");
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("transactionType", "ISSUE"); issue.put("materialCode", "TEST-WMS-001"); issue.put("warehouseCode", "WH-TEST");
            issue.put("locationCode", "A-01"); issue.put("batchNo", "B-TEST"); issue.put("quantity", 1); issue.put("unit", "件");
            assertThrows(IllegalArgumentException.class, () -> warehouse.postTransaction(issue, "warehouse"));
            jdbc.update("update wh_batch set quality_status='PASSED' where tenant_id=1 and material_code='TEST-WMS-001' and batch_no='B-TEST'");
            assertEquals("COMPLETED", warehouse.postTransaction(issue, "warehouse").get("status"));
        });
    }
}
