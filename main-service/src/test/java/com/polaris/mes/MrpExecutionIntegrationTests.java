package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.MrpService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class MrpExecutionIntegrationTests {
    @Autowired MrpService mrp;
    @Autowired ErpService erp;
    @Autowired PlatformService platform;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("create table if not exists inventory(id bigint auto_increment primary key, tenant_id bigint not null, material_code varchar(64) not null, material_name varchar(120), warehouse_code varchar(64) not null, location_code varchar(64) not null, batch_no varchar(64), available_qty int, locked_qty int, reserved_qty int, in_transit_qty int, unit varchar(20), safety_stock int, stock_status varchar(20), expiry_date date, version_no bigint, updated_at timestamp default current_timestamp)");
        jdbc.update("delete from erp_business_record_line where tenant_id=1 and record_id in (select id from erp_business_record where tenant_id=1 and record_no='PO-TEST-001')");
        jdbc.update("delete from erp_business_record where tenant_id=1 and record_no='PO-TEST-001'");
        jdbc.update("delete from asn_line where tenant_id=1 and asn_id in (select id from asn where tenant_id=1 and asn_no like 'TEST-MRP-%')");
        jdbc.update("delete from asn where tenant_id=1 and asn_no like 'TEST-MRP-%'");
        jdbc.update("delete from material_call where tenant_id=1 and call_no like 'TEST-MRP-%'");
        jdbc.update("delete from mrp_shortage where tenant_id=1 and shortage_no like 'TEST-MRP-%'");
        jdbc.update("delete from mrp_requirement where tenant_id=1 and run_id in (select id from mrp_run where tenant_id=1 and run_no like 'TEST-MRP-%')");
        jdbc.update("delete from mrp_run where tenant_id=1 and run_no like 'TEST-MRP-%'");
        jdbc.update("delete from bom_item where tenant_id=1 and bom_id in (select id from bom where tenant_id=1 and bom_code like 'TEST-MRP-%')");
        jdbc.update("delete from bom where tenant_id=1 and bom_code like 'TEST-MRP-%'");
        jdbc.update("delete from inventory where tenant_id=1 and material_code='TEST-MRP-RM-001'");
    }

    @Test
    void persistedMrpCreatesShortageAndLineSideCall() {
        TenantContext.Identity identity = new TenantContext.Identity(1, "demo", "华东一厂", 1, "planner", "planner");
        TenantContext.run(identity, () -> {
            Map<String, Object> bom = new LinkedHashMap<>();
            bom.put("bomCode", "TEST-MRP-BOM-001"); bom.put("productCode", "TEST-MRP-FG-001"); bom.put("productName", "测试成品"); bom.put("status", "RELEASED");
            bom.put("items", List.of(Map.of("materialCode", "TEST-MRP-RM-001", "materialName", "测试原料", "quantity", 2, "unit", "件", "lossRate", 0)));
            platform.insertBom(bom);
            jdbc.update("insert into inventory(tenant_id,material_code,material_name,warehouse_code,location_code,batch_no,available_qty,locked_qty,reserved_qty,in_transit_qty,unit,safety_stock,stock_status) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    1, "TEST-MRP-RM-001", "测试原料", "WH-TEST", "A-01", "B-01", 3, 0, 0, 1, "件", 0, "AVAILABLE");

            Map<String, Object> result = mrp.run(Map.of("productCode", "TEST-MRP-FG-001", "planQty", 5, "priority", "URGENT"));
            assertEquals(1, ((Number) result.get("shortageCount")).intValue());
            assertEquals(6, ((Number) result.get("netShortageQty")).intValue());
            assertTrue(String.valueOf(result.get("runNo")).startsWith("MRP-"));

            Map<String, Object> shortage = mrp.listShortages("OPEN", "TEST-MRP-RM-001").get(0);
            Map<String, Object> requisition = mrp.createPurchaseRequisition(((Number) shortage.get("id")).longValue(), Map.of());
            assertEquals("REQUISITIONS", requisition.get("type"));
            shortage = mrp.listShortages("OPEN", "TEST-MRP-RM-001").get(0);
            Map<String, Object> call = mrp.createMaterialCall(((Number) shortage.get("id")).longValue(), Map.of("workOrderNo", "WO-TEST-001", "requestedQty", 6), "planner");
            assertEquals("DRAFT", call.get("status"));
            call = mrp.transitionMaterialCall(((Number) call.get("id")).longValue(), Map.of("status", "RELEASED"), "planner");
            call = mrp.transitionMaterialCall(((Number) call.get("id")).longValue(), Map.of("status", "IN_PICKING"), "planner");
            call = mrp.transitionMaterialCall(((Number) call.get("id")).longValue(), Map.of("status", "COMPLETED"), "planner");
            assertEquals("COMPLETED", call.get("status"));
            assertEquals("RESOLVED", mrp.listShortages(null, "TEST-MRP-RM-001").get(0).get("status"));
        });
    }

    @Test
    void asnCanBeCreatedAndSubmittedAgainstManualLines() {
        TenantContext.Identity identity = new TenantContext.Identity(1, "demo", "华东一厂", 1, "planner", "planner");
        TenantContext.run(identity, () -> {
            erp.createRecord("procurement", Map.of("type", "ORDERS", "no", "PO-TEST-001", "name", "测试原料采购订单", "partner", "测试供应商", "status", "ORDERED",
                    "lines", List.of(Map.of("materialCode", "TEST-MRP-RM-001", "materialName", "测试原料", "requestedQty", 10, "unit", "件"))));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("asnNo", "TEST-MRP-ASN-001"); payload.put("purchaseOrderNo", "PO-TEST-001"); payload.put("supplierName", "测试供应商"); payload.put("expectedArrival", "2026-09-01");
            Map<String, Object> asn = mrp.createAsn(payload, "planner");
            assertEquals("DRAFT", asn.get("status"));
            assertEquals(1, ((List<?>) asn.get("lines")).size());
            asn = mrp.transitionAsn(((Number) asn.get("id")).longValue(), Map.of("status", "SUBMITTED"), "planner");
            assertEquals("SUBMITTED", asn.get("status"));
            asn = mrp.transitionAsn(((Number) asn.get("id")).longValue(), Map.of("status", "CONFIRMED"), "planner");
            assertEquals("CONFIRMED", asn.get("status"));
            asn = mrp.transitionAsn(((Number) asn.get("id")).longValue(), Map.of("status", "RECEIVING"), "planner");
            long asnId = ((Number) asn.get("id")).longValue();
            TenantContext.run(new TenantContext.Identity(1, "demo", "华东一厂", 1, "warehouse", "warehouse"), () -> {
                Map<String, Object> received = mrp.transitionAsn(asnId, Map.of("status", "RECEIVED"), "warehouse");
                assertEquals("RECEIVED", received.get("status"));
            });
            assertEquals("RECEIVED", jdbc.queryForObject("select status from erp_business_record where tenant_id=1 and record_no='PO-TEST-001'", String.class));
        });
    }
}
