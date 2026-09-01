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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:wms_mp;MODE=LEGACY;DB_CLOSE_DELAY=-1")
class WarehouseMybatisPlusIntegrationTests {
    private static final TenantContext.Identity IDENTITY = new TenantContext.Identity(99, "mp-tenant", "MP 租户", 1001, "pda", "warehouse");

    @Autowired JdbcTemplate jdbc;
    @Autowired WarehouseService warehouse;

    @BeforeEach
    void prepareWmsTables() {
        jdbc.execute("create table if not exists wh_warehouse(id bigint auto_increment primary key, tenant_id bigint not null, warehouse_code varchar(64) not null, warehouse_name varchar(120) not null, warehouse_type varchar(30), owner_code varchar(64), status varchar(20), remark varchar(255), created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp)");
        jdbc.execute("create table if not exists wh_material(id bigint auto_increment primary key, tenant_id bigint not null, material_code varchar(64) not null, material_name varchar(120) not null, material_type varchar(30), unit varchar(20), lot_control tinyint, serial_control tinyint, shelf_life_days int, safety_stock int, status varchar(20), remark varchar(255), created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp)");
        jdbc.execute("create table if not exists wh_batch(id bigint auto_increment primary key, tenant_id bigint not null, material_code varchar(64) not null, batch_no varchar(64) not null, production_date date, expiry_date date, supplier_code varchar(64), quality_status varchar(20), batch_status varchar(20), remark varchar(255), created_at timestamp default current_timestamp)");
        jdbc.execute("create table if not exists inventory(id bigint auto_increment primary key, tenant_id bigint not null, material_code varchar(64) not null, material_name varchar(120), warehouse_code varchar(64) not null, location_code varchar(64) not null, batch_no varchar(64), available_qty int, locked_qty int, reserved_qty int, in_transit_qty int, unit varchar(20), safety_stock int, stock_status varchar(20), expiry_date date, version_no bigint, updated_at timestamp default current_timestamp)");
        jdbc.execute("create table if not exists wh_document(id bigint auto_increment primary key, tenant_id bigint not null, document_no varchar(100), document_type varchar(30), status varchar(20), source_doc_no varchar(64), warehouse_code varchar(64), from_warehouse_code varchar(64), to_warehouse_code varchar(64), operator_name varchar(100), remark varchar(255), idempotency_key varchar(120), created_at timestamp default current_timestamp, completed_at timestamp)");
        jdbc.execute("create table if not exists wh_document_line(id bigint auto_increment primary key, tenant_id bigint not null, document_id bigint, line_no int, material_code varchar(64), material_name varchar(120), unit varchar(20), planned_qty int, actual_qty int, batch_no varchar(64), from_location_code varchar(64), to_location_code varchar(64), work_order_no varchar(64), quality_status varchar(20), created_at timestamp default current_timestamp)");
        jdbc.execute("create table if not exists material_transaction(id bigint auto_increment primary key, tenant_id bigint not null, transaction_no varchar(100), transaction_type varchar(30), material_code varchar(64), material_name varchar(120), warehouse_code varchar(64), location_code varchar(64), batch_no varchar(64), quantity int, unit varchar(20), operator_name varchar(100), source_doc_no varchar(64), document_no varchar(100), from_warehouse_code varchar(64), from_location_code varchar(64), to_warehouse_code varchar(64), to_location_code varchar(64), reason_code varchar(64), idempotency_key varchar(120), status varchar(20), remark varchar(255), created_at timestamp default current_timestamp)");
        jdbc.update("delete from material_transaction where tenant_id=?", 99);
        jdbc.update("delete from wh_document_line where tenant_id=?", 99);
        jdbc.update("delete from wh_document where tenant_id=?", 99);
        jdbc.update("delete from inventory where tenant_id=?", 99);
        jdbc.update("delete from wh_material where tenant_id=?", 99);
        jdbc.update("delete from wh_warehouse where tenant_id=?", 99);
    }

    @Test
    void mpServicePostsIdempotentReceiptAndMaintainsInventory() {
        TenantContext.run(IDENTITY, () -> {
            Map<String, Object> warehousePayload = Map.of("warehouseCode", "MP-WH", "warehouseName", "MP 测试仓");
            assertEquals("MP-WH", warehouse.saveWarehouse(warehousePayload).get("warehouse_code"));

            Map<String, Object> materialPayload = Map.of("materialCode", "MP-MAT", "materialName", "MP 测试物料", "unit", "件");
            warehouse.saveMaterial(materialPayload);
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("transactionType", "RECEIPT"); receipt.put("materialCode", "MP-MAT"); receipt.put("warehouseCode", "MP-WH"); receipt.put("locationCode", "A-01"); receipt.put("quantity", 8); receipt.put("idempotencyKey", "MP-RECEIPT-1");

            Map<String, Object> first = warehouse.postTransaction(receipt, "pda");
            Map<String, Object> second = warehouse.postTransaction(receipt, "pda");
            assertEquals(first.get("transactionNo"), second.get("transactionNo"));
            List<Map<String, Object>> inventory = warehouse.listInventory(null, "MP-WH", null);
            assertEquals(1, inventory.size());
            assertEquals(8, ((Number) inventory.get(0).get("available_qty")).intValue());
            assertFalse(warehouse.listTransactions(null, null, 20).isEmpty());
        });
    }
}
