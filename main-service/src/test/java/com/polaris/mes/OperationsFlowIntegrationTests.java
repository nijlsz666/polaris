package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.OperationsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Business-level regression tests for the floor control tower. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:operations_flow;MODE=LEGACY;DB_CLOSE_DELAY=-1")
class OperationsFlowIntegrationTests {
    private static final TenantContext.Identity TENANT_ONE = new TenantContext.Identity(1, "demo", "华东一厂", 1, "operator", "operator");
    private static final TenantContext.Identity TENANT_TWO = new TenantContext.Identity(2, "south-plant", "南方二厂", 2, "operator", "operator");

    @Autowired JdbcTemplate jdbc;
    @Autowired OperationsService operations;

    @BeforeEach
    void cleanOperations() {
        jdbc.update("delete from mfg_exception_action");
        jdbc.update("delete from mfg_exception");
        jdbc.update("delete from mfg_downtime_event");
        jdbc.update("delete from mfg_equipment");
        jdbc.update("delete from platform_notification");
        TenantContext.run(TENANT_ONE, () -> operations.saveEquipment(Map.of("equipmentCode", "ASM-01", "equipmentName", "一线总装工位", "workCenter", "装配一线")));
    }

    @Test
    void downtimeCreatesEquipmentExceptionAndCloseRequiresCompletedAction() {
        Map<String, Object> started = TenantContext.run(TENANT_ONE, () -> operations.startDowntime(
                Map.of("equipmentCode", "ASM-01", "reasonCode", "MATERIAL_WAIT", "reasonName", "待料", "severity", "HIGH"), "operator"));

        assertEquals("OPEN", started.get("status"));
        assertEquals("DOWN", TenantContext.run(TENANT_ONE, () -> operations.listEquipment("DOWN", null).get(0).get("status")));
        Map<String, Object> exception = TenantContext.run(TENANT_ONE, () -> operations.listExceptions(null, null, null).get(0));
        long exceptionId = ((Number) exception.get("id")).longValue();
        TenantContext.run(TENANT_ONE, () -> operations.createAction(exceptionId, Map.of("actionDescription", "确认替代物料并恢复生产"), "operator"));

        TenantContext.run(TENANT_ONE, () -> operations.transitionException(exceptionId, Map.of("status", "ACKNOWLEDGED"), "operator"));
        TenantContext.run(TENANT_ONE, () -> operations.transitionException(exceptionId, Map.of("status", "CONTAINED"), "operator"));
        TenantContext.run(TENANT_ONE, () -> operations.transitionException(exceptionId, Map.of("status", "RESOLVED"), "operator"));
        assertThrows(IllegalArgumentException.class, () -> TenantContext.run(TENANT_ONE, () -> operations.transitionException(exceptionId, Map.of("status", "CLOSED"), "operator")));

        Object actionIdValue = TenantContext.run(TENANT_ONE, () -> operations.listActions(exceptionId).get(0).get("id"));
        long actionId = ((Number) actionIdValue).longValue();
        TenantContext.run(TENANT_ONE, () -> operations.completeAction(actionId, "operator"));
        assertEquals("CLOSED", TenantContext.run(TENANT_ONE, () -> operations.transitionException(exceptionId, Map.of("status", "CLOSED"), "operator").get("status")));

        long downtimeId = ((Number) started.get("id")).longValue();
        TenantContext.run(TENANT_ONE, () -> operations.resumeDowntime(downtimeId, "operator"));
        assertEquals("RUNNING", TenantContext.run(TENANT_ONE, () -> operations.listEquipment(null, "ASM-01").get(0).get("status")));
    }

    @Test
    void idempotencyKeyReturnsTheSameException() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "工单扫码失败");
        payload.put("description", "现场终端无法读取工单条码");
        payload.put("idempotencyKey", "PDA-EXCEPTION-001");
        Map<String, Object> first = TenantContext.run(TENANT_ONE, () -> operations.createException(payload, "operator"));
        Map<String, Object> second = TenantContext.run(TENANT_ONE, () -> operations.createException(payload, "operator"));
        assertEquals(first.get("id"), second.get("id"));
        assertEquals(1, TenantContext.run(TENANT_ONE, () -> operations.listExceptions(null, null, null).size()));
    }

    @Test
    void exceptionAndEquipmentAreTenantIsolated() {
        TenantContext.run(TENANT_TWO, () -> operations.saveEquipment(Map.of("equipmentCode", "ASM-01", "equipmentName", "南方总装工位")));
        assertEquals(1, TenantContext.run(TENANT_ONE, () -> operations.listEquipment(null, null).size()));
        assertEquals(1, TenantContext.run(TENANT_TWO, () -> operations.listEquipment(null, null).size()));
        TenantContext.run(TENANT_TWO, () -> operations.createException(Map.of("title", "南方异常", "description", "仅属于南方租户"), "operator"));
        assertEquals(0, TenantContext.run(TENANT_ONE, () -> operations.listExceptions(null, null, "南方异常").size()));
        assertNotEquals(0, TenantContext.run(TENANT_TWO, () -> operations.listExceptions(null, null, "南方异常").size()));
    }
}
