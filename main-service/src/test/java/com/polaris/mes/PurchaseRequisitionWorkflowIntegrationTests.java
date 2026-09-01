package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.BpmService;
import com.polaris.mes.service.ErpService;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PurchaseRequisitionWorkflowIntegrationTests {
    private static final String RECORD_NO = "TEST-PURCHASE-WORKFLOW-001";

    @Autowired ErpService erp;
    @Autowired BpmService bpm;
    @Autowired JdbcTemplate jdbc;

    private final TenantContext.Identity planner = new TenantContext.Identity(1, "demo", "华东一厂", 1, "planner", "planner");
    private final TenantContext.Identity approver = new TenantContext.Identity(1, "demo", "华东一厂", 2, "admin", "admin");

    @BeforeEach
    void cleanBefore() { cleanRecord(); }

    @AfterEach
    void cleanAfter() { cleanRecord(); }

    @Test
    void draftCanBeSubmittedListedAsInitiatedAndApprovedFromTodo() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "requisitions");
        payload.put("no", RECORD_NO);
        payload.put("name", "工作流测试采购申请");
        payload.put("departmentCode", "RND");
        payload.put("requesterCode", "planner");
        payload.put("lines", List.of(Map.of(
                "materialCode", "TEST-RM-001", "materialName", "测试物料", "unit", "件",
                "requestedQty", 2, "unitPrice", 18, "taxRate", 13, "requiredDate", "2026-09-01")));

        Map<String, Object> draft = TenantContext.run(planner, () -> erp.saveDraft("procurement", payload));
        assertEquals("DRAFT", draft.get("status"));

        List<Map<String, Object>> drafts = TenantContext.run(planner,
                () -> erp.listRecords("procurement", "requisitions", null, null, "drafts", "planner"));
        assertTrue(drafts.stream().anyMatch(row -> RECORD_NO.equals(row.get("no"))));

        Map<String, Object> instance = TenantContext.run(planner, () -> bpm.startProcess(Map.of(
                "processCode", "purchaseRequisitionApproval", "businessType", "ERP_RECORD",
                "businessId", String.valueOf(draft.get("id")), "title", "工作流测试采购申请审批",
                "variables", Map.of("purchaseHighRisk", false, "purchaseManagementRisk", false)), "planner"));
        String instanceId = String.valueOf(instance.get("flowable_instance_id"));

        Map<String, Object> submitted = TenantContext.run(planner, () -> erp.detailRecord("procurement", ((Number) draft.get("id")).longValue()));
        assertEquals("REVIEW", submitted.get("status"));
        assertTrue(TenantContext.run(planner, () -> bpm.listInstances(null, "planner")).stream()
                .anyMatch(row -> instanceId.equals(String.valueOf(row.get("flowable_instance_id")))));

        Map<String, Object> firstTask = todoFor(instanceId);
        TenantContext.run(approver, () -> bpm.completeTask(String.valueOf(firstTask.get("taskId")), Map.of("approved", true), "admin"));
        Map<String, Object> secondTask = todoFor(instanceId);
        TenantContext.run(approver, () -> bpm.completeTask(String.valueOf(secondTask.get("taskId")), Map.of("approved", true), "admin"));

        Map<String, Object> approved = TenantContext.run(planner, () -> erp.detailRecord("procurement", ((Number) draft.get("id")).longValue()));
        assertEquals("APPROVED", approved.get("status"));
        assertFalse(TenantContext.run(approver, () -> bpm.listTasks("todo", "admin")).stream()
                .anyMatch(row -> instanceId.equals(String.valueOf(row.get("instanceId")))));
    }

    private Map<String, Object> todoFor(String instanceId) {
        return TenantContext.run(approver, () -> bpm.listTasks("todo", "admin").stream()
                .filter(row -> instanceId.equals(String.valueOf(row.get("instanceId"))))
                .findFirst().orElseThrow());
    }

    private void cleanRecord() {
        jdbc.update("delete from erp_business_record_line where record_id in (select id from erp_business_record where tenant_id=1 and record_no=?)", RECORD_NO);
        jdbc.update("delete from erp_business_record where tenant_id=1 and record_no=?", RECORD_NO);
    }
}
