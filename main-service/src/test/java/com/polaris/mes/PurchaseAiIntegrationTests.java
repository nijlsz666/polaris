package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.BpmService;
import com.polaris.mes.service.PurchaseAiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PurchaseAiIntegrationTests {
    @Autowired PurchaseAiService purchaseAi;
    @Autowired BpmService bpm;
    @Autowired JdbcTemplate jdbc;

    private final TenantContext.Identity planner = new TenantContext.Identity(1, "demo", "华东一厂", 1, "planner", "planner");

    @BeforeEach
    void seedMaterial() {
        try {
            jdbc.update("insert into wh_material(tenant_id,material_code,material_name,material_type,unit,status) values(?,?,?,?,?,'ACTIVE')",
                    1, "RM-MOTOR-001", "无刷电机", "RAW", "件");
        } catch (RuntimeException ignored) {
            // The canonical test database may already contain the material.
        }
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from erp_business_record_line where record_id in (select id from erp_business_record where tenant_id=1 and record_no like 'PRO-%')");
        jdbc.update("delete from erp_business_record where tenant_id=1 and record_no like 'PRO-%'");
        jdbc.update("delete from ai_generation_field_audit where tenant_id=1 and session_id like 'aig-%'");
        jdbc.update("delete from ai_generation_session where tenant_id=1 and session_id like 'aig-%'");
        jdbc.update("delete from ai_purchase_submission where tenant_id=1 and idempotency_key like 'purchase-ai-test-%'");
    }

    @Test
    void aiDraftConfirmationCreatesLinkedApprovalAndIsIdempotent() {
        Map<String, Object> parsed = TenantContext.run(planner, () -> purchaseAi.parse(Map.of(
                "input", "为研发部采购 2 个 RM-MOTOR-001 无刷电机，用于 9 月试产，预计单价 18 元，2026-09-01 前到货。")));
        assertEquals("CREATE_PURCHASE_REQUISITION", parsed.get("intent"));
        Map<String, Object> draft = map(parsed.get("draft"));

        Map<String, Object> validation = TenantContext.run(planner, () -> purchaseAi.validate(Map.of("draft", draft)));
        assertTrue(list(validation.get("errors")).isEmpty(), () -> String.valueOf(validation.get("errors")));
        assertEquals("36.00", String.valueOf(map(validation.get("calculation")).get("total")));

        Map<String, Object> usdDraft = new LinkedHashMap<>(draft);
        Map<String, Object> usdRequisition = new LinkedHashMap<>(map(draft.get("requisition")));
        usdRequisition.put("currency", "USD");
        usdDraft.put("requisition", usdRequisition);
        Map<String, Object> usdValidation = TenantContext.run(planner, () -> purchaseAi.validate(Map.of("draft", usdDraft)));
        assertEquals(3, list(usdValidation.get("route")).size());

        Map<String, Object> confirmed = TenantContext.run(planner, () -> purchaseAi.confirm(Map.of(
                "sessionId", parsed.get("sessionId"), "draft", draft, "routeConfirmation", true), "purchase-ai-test-001"));
        assertEquals("SUBMITTED", confirmed.get("status"));
        assertFalse(Boolean.TRUE.equals(confirmed.get("idempotent")));
        assertEquals(2, list(confirmed.get("route")).size());

        Map<String, Object> repeated = TenantContext.run(planner, () -> purchaseAi.confirm(Map.of(
                "sessionId", parsed.get("sessionId"), "draft", draft, "routeConfirmation", true), "purchase-ai-test-001"));
        assertTrue(Boolean.TRUE.equals(repeated.get("idempotent")));
        assertEquals(confirmed.get("recordId"), repeated.get("recordId"));

        String instanceId = String.valueOf(map(confirmed.get("processInstance")).get("flowable_instance_id"));
        Map<String, Object> firstTask = TenantContext.run(planner, () -> bpm.listTasks("todo", "planner").stream()
                .filter(item -> instanceId.equals(String.valueOf(item.get("instanceId")))).findFirst().orElseThrow());
        TenantContext.run(planner, () -> bpm.completeTask(String.valueOf(firstTask.get("taskId")), Map.of("approved", true), "planner"));
        Map<String, Object> secondTask = TenantContext.run(planner, () -> bpm.listTasks("todo", "planner").stream()
                .filter(item -> instanceId.equals(String.valueOf(item.get("instanceId")))).findFirst().orElseThrow());
        TenantContext.run(planner, () -> bpm.completeTask(String.valueOf(secondTask.get("taskId")), Map.of("approved", true), "planner"));

        Map<String, Object> saved = TenantContext.run(planner, () -> purchaseAi.context());
        assertEquals("planner", saved.get("requesterCode"));
        Map<String, Object> record = TenantContext.run(planner, () -> jdbc.queryForMap("select status from erp_business_record where tenant_id=1 and id=?", confirmed.get("recordId")));
        assertEquals("APPROVED", record.get("status"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }

    private static List<?> list(Object value) { return value instanceof List<?> result ? result : List.of(); }
}
