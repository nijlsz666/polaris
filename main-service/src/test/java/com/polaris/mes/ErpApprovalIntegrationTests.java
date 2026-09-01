package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.BpmService;
import com.polaris.mes.service.ErpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class ErpApprovalIntegrationTests {
    @Autowired ErpService erp;
    @Autowired BpmService bpm;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from erp_business_record where tenant_id=1 and record_no='TEST-APPROVAL-001'");
    }

    @AfterEach
    void cleanAfter() {
        jdbc.update("delete from erp_business_record where tenant_id=1 and record_no='TEST-APPROVAL-001'");
    }

    @Test
    void erpRecordApprovalWritesBackToTheBusinessLedger() {
        TenantContext.Identity planner = new TenantContext.Identity(1, "demo", "华东一厂", 1, "planner", "planner");
        Map<String, Object> record = TenantContext.run(planner, () -> erp.createRecord("sales", Map.of("type", "orders", "no", "TEST-APPROVAL-001", "name", "审批集成测试订单", "amount", "1000")));
        Map<String, Object> instance = TenantContext.run(planner, () -> bpm.startProcess(Map.of("processCode", "workOrderApproval", "businessType", "ERP_RECORD", "businessId", String.valueOf(record.get("id"))), "planner"));
        Map<String, Object> plannerTask = TenantContext.run(planner, () -> bpm.listTasks("todo", "planner").stream().filter(row -> String.valueOf(row.get("instanceId")).equals(instance.get("flowable_instance_id"))).findFirst().orElseThrow());
        TenantContext.run(planner, () -> bpm.completeTask(String.valueOf(plannerTask.get("taskId")), Map.of("approved", true, "comment", "计划可执行"), "planner"));
        Map<String, Object> saved = TenantContext.run(planner, () -> erp.listRecords("sales", "orders", "TEST-APPROVAL-001", null).get(0));
        assertEquals("CONFIRMED", saved.get("status"));
    }
}
