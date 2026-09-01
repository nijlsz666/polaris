package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.QualityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
class QualityFlowIntegrationTests {
    private static final String MATERIAL = "TEST-QM-001";
    private static final TenantContext.Identity QUALITY_USER =
            new TenantContext.Identity(1, "demo", "华东一厂", 1, "quality", "quality");

    @Autowired JdbcTemplate jdbc;
    @Autowired QualityService quality;

    @BeforeEach
    void cleanQualityFixtures() {
        jdbc.update("delete from qm_ipqc_record where tenant_id=1 and ipqc_no like 'TEST-IPQC-%'");
        jdbc.update("delete from qm_avl_entry where tenant_id=1 and material_code=?", MATERIAL);
        jdbc.update("delete from qm_supplier_evaluation where tenant_id=1 and evaluation_no like 'TEST-SUP-EVAL-%'");
        jdbc.update("delete from qm_corrective_action where tenant_id=1 and nc_id in (select id from qm_nonconformance where tenant_id=1 and material_code=?)", MATERIAL);
        jdbc.update("delete from qm_inspection_result where tenant_id=1 and lot_id in (select id from qm_inspection_lot where tenant_id=1 and material_code=?)", MATERIAL);
        jdbc.update("delete from qm_nonconformance where tenant_id=1 and material_code=?", MATERIAL);
        jdbc.update("delete from qm_inspection_lot where tenant_id=1 and material_code=?", MATERIAL);
        jdbc.update("delete from qm_inspection_plan_item where tenant_id=1 and plan_id in (select id from qm_inspection_plan where tenant_id=1 and material_code=?)", MATERIAL);
        jdbc.update("delete from qm_inspection_plan where tenant_id=1 and material_code=?", MATERIAL);
        jdbc.update("delete from wh_batch where tenant_id=1 and material_code=?", MATERIAL);
    }

    @Test
    void passedIncomingLotUpdatesBatchQualityStatus() {
        TenantContext.run(QUALITY_USER, () -> {
            jdbc.update("insert into wh_batch(tenant_id, material_code, batch_no, quality_status, batch_status) values(?,?,?,?,?)",
                    1, MATERIAL, "B-QM-PASS", "PENDING", "ACTIVE");

            Map<String, Object> plan = quality.savePlan(plan("TEST-QM-PLAN-PASS", "来料电机检验", "RELEASED",
                    item("VOLTAGE", "额定电压", "QUANTITATIVE", "9-11V", 9, 11)), 0, "quality");
            assertEquals("RELEASED", plan.get("status"));

            Map<String, Object> lot = quality.createLot(lot("B-QM-PASS"), "quality");
            Map<String, Object> started = quality.startLot(idOf(lot), "quality");
            long itemId = idOf(itemsOf(started).get(0), "item_id");
            quality.saveResults(idOf(lot), Map.of("items", List.of(Map.of("itemId", itemId, "resultValue", "10"))), "quality");

            Map<String, Object> completed = quality.completeLot(idOf(lot), "quality");
            assertEquals("PASSED", completed.get("status"));
            assertEquals("PASSED", jdbc.queryForObject("select quality_status from wh_batch where tenant_id=1 and material_code=? and batch_no=?", String.class, MATERIAL, "B-QM-PASS"));
        });
    }

    @Test
    void failedLotCreatesNcrAndClosesCorrectiveAction() {
        TenantContext.run(QUALITY_USER, () -> {
            jdbc.update("insert into wh_batch(tenant_id, material_code, batch_no, quality_status, batch_status) values(?,?,?,?,?)",
                    1, MATERIAL, "B-QM-FAIL", "PENDING", "ACTIVE");

            quality.savePlan(plan("TEST-QM-PLAN-FAIL", "来料外观检验", "RELEASED",
                    item("APPEARANCE", "外观", "QUALITATIVE", "无裂纹", null, null)), 0, "quality");
            Map<String, Object> lot = quality.createLot(lot("B-QM-FAIL"), "quality");
            Map<String, Object> started = quality.startLot(idOf(lot), "quality");
            long itemId = idOf(itemsOf(started).get(0), "item_id");
            Map<String, Object> afterResult = quality.saveResults(idOf(lot), Map.of("items", List.of(Map.of("itemId", itemId, "resultText", "FAIL"))), "quality");
            assertEquals("FAIL", itemsOf(afterResult).get(0).get("result_status"));

            Map<String, Object> completed = quality.completeLot(idOf(lot), "quality");
            assertEquals("FAILED", completed.get("status"));
            assertEquals("FAILED", jdbc.queryForObject("select quality_status from wh_batch where tenant_id=1 and material_code=? and batch_no=?", String.class, MATERIAL, "B-QM-FAIL"));

            List<Map<String, Object>> ncs = quality.listNonconformances(null, String.valueOf(lot.get("lot_no")));
            assertFalse(ncs.isEmpty());
            long ncId = idOf(ncs.get(0));
            assertEquals("CONTAINED", quality.updateDisposition(ncId, Map.of("disposition", "HOLD", "containmentAction", "隔离待处置"), "quality").get("status"));
            Map<String, Object> action = quality.createAction(ncId, Map.of("actionDescription", "复核供应商来料并更换批次", "ownerCode", "quality"));
            quality.completeAction(idOf(action), "quality");
            Map<String, Object> closed = quality.closeNonconformance(ncId, Map.of("rootCause", "供应商包装破损", "correctiveAction", "增加来料包装检验和供应商整改"), "quality");
            assertEquals("CLOSED", closed.get("status"));
        });
    }

    @Test
    void supplierEvaluationAvlAndIpqcCloseTheQualityDomainGaps() {
        TenantContext.run(QUALITY_USER, () -> {
            Map<String, Object> evaluation = quality.saveSupplierEvaluation(new LinkedHashMap<>(Map.of(
                    "evaluationNo", "TEST-SUP-EVAL-001", "supplierCode", "TEST-SUP-001", "supplierName", "测试供应商",
                    "evaluationPeriod", "2026-Q3", "deliveryScore", 96, "qualityScore", 94,
                    "serviceScore", 90, "priceScore", 88, "evaluatedAt", "2026-08-21")), 0, "quality");
            assertEquals("A", evaluation.get("grade"));
            assertEquals("SUBMITTED", quality.submitSupplierEvaluation(idOf(evaluation), "quality").get("status"));

            Map<String, Object> avl = quality.saveAvl(new LinkedHashMap<>(Map.of(
                    "materialCode", MATERIAL, "materialName", "测试物料", "supplierCode", "TEST-SUP-001",
                    "supplierName", "测试供应商", "supplierPartNo", "TEST-PART-001")), 0, "quality");
            assertEquals("PENDING", avl.get("approval_status"));
            assertEquals("APPROVED", quality.updateAvlStatus(idOf(avl), Map.of("approvalStatus", "APPROVED"), "quality").get("approval_status"));

            Map<String, Object> ipqc = quality.createIpqc(Map.of(
                    "ipqcNo", "TEST-IPQC-001", "lineCode", "LINE-TEST", "processName", "测试装配",
                    "workOrderNo", "WO-TEST-001", "sampleQty", 5), "quality");
            Map<String, Object> result = quality.saveIpqcResult(idOf(ipqc), Map.of(
                    "inspectedQty", 5, "defectQty", 0, "firstPieceStatus", "PASS"), "quality");
            assertEquals("PASSED", result.get("status"));
        });
    }

    private Map<String, Object> plan(String code, String name, String status, Map<String, Object> item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planCode", code);
        payload.put("planName", name);
        payload.put("inspectionType", "INCOMING");
        payload.put("materialCode", MATERIAL);
        payload.put("status", status);
        payload.put("items", List.of(item));
        return payload;
    }

    private Map<String, Object> item(String code, String name, String resultType, String standard, Integer lower, Integer upper) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("characteristicCode", code);
        item.put("characteristicName", name);
        item.put("resultType", resultType);
        item.put("standardText", standard);
        item.put("requiredFlag", true);
        if (lower != null) item.put("lowerLimit", lower);
        if (upper != null) item.put("upperLimit", upper);
        return item;
    }

    private Map<String, Object> lot(String batchNo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inspectionType", "INCOMING");
        payload.put("materialCode", MATERIAL);
        payload.put("materialName", "测试电机");
        payload.put("batchNo", batchNo);
        payload.put("sampleQty", 1);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itemsOf(Map<String, Object> row) {
        return (List<Map<String, Object>>) row.getOrDefault("items", new ArrayList<>());
    }

    private long idOf(Map<String, Object> row) { return idOf(row, "id"); }

    private long idOf(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
}
