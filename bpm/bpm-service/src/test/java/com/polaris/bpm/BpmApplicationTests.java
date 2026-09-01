package com.polaris.bpm;

import com.polaris.bpm.service.BpmService;
import com.polaris.bpm.mapper.BpmAuditLogMapper;
import com.polaris.bpm.mapper.BpmEventLogMapper;
import com.polaris.bpm.mapper.BpmIdempotencyRecordMapper;
import com.polaris.bpm.model.entity.BpmEventLog;
import com.polaris.bpm.model.entity.BpmAuditLog;
import com.polaris.bpm.model.entity.BpmIdempotencyRecord;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BpmApplicationTests {
    @Autowired
    private BpmService service;

    @Autowired
    private BpmAuditLogMapper auditLogMapper;

    @Autowired
    private BpmIdempotencyRecordMapper idempotencyRecordMapper;

    @Autowired
    private BpmEventLogMapper eventLogMapper;

    @Test
    void contextLoads() {}

    @Test
    void visualDesignCanBeSavedAndDeployed() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("processCode", "visualApprovalTest");
        payload.put("processName", "可视化发布测试");
        payload.put("version", 1);
        payload.put("nodes", List.of(
                Map.of("id", "start_1", "type", "start", "label", "开始"),
                Map.of("id", "task_1", "type", "userTask", "label", "审核", "candidateGroups", "planner"),
                Map.of("id", "service_1", "type", "serviceTask", "label", "回写状态"),
                Map.of("id", "end_1", "type", "end", "label", "结束")
        ));
        payload.put("edges", List.of(
                Map.of("id", "e1", "source", "start_1", "target", "task_1"),
                Map.of("id", "e2", "source", "task_1", "target", "service_1"),
                Map.of("id", "e3", "source", "service_1", "target", "end_1")
        ));
        Map<String, Object> result = service.publishDesign("visualApprovalTest", payload, "admin");
        assertEquals("PUBLISHED", result.get("status"));
        assertNotNull(result.get("definition_id"));
        assertNotNull(service.getDesign("visualApprovalTest"));
    }

    @Test
    void businessFlowExecutesServiceActionBeforeHumanTask() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("processCode", "businessLifecycleTest");
        payload.put("processName", "业务生命周期测试");
        payload.put("processType", "BUSINESS");
        payload.put("triggerType", "MANUAL");
        payload.put("nodes", List.of(
                Map.of("id", "start", "type", "start", "label", "开始"),
                Map.of("id", "advance", "type", "serviceTask", "label", "推进到生产", "serviceKey", "business.setStatus"),
                Map.of("id", "review", "type", "userTask", "label", "业务确认", "candidateGroups", "operations"),
                Map.of("id", "end", "type", "end", "label", "完成")
        ));
        payload.put("edges", List.of(
                Map.of("id", "e1", "source", "start", "target", "advance"),
                Map.of("id", "e2", "source", "advance", "target", "review"),
                Map.of("id", "e3", "source", "review", "target", "end")
        ));

        Map<String, Object> published = service.publishDesign("businessLifecycleTest", payload, "admin");
        assertEquals("BUSINESS", published.get("process_type"));
        Map<String, Object> instance = service.start(Map.of(
                "processCode", "businessLifecycleTest",
                "businessType", "COMMON",
                "title", "业务生命周期实例"
        ), "admin");
        assertNotNull(instance.get("flowable_instance_id"));
        assertEquals(1, service.listTasks("todo", "operations").size());
    }

    @Test
    void publishRejectsBrokenGraphOnServer() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("processCode", "brokenGraphTest");
        payload.put("processName", "不完整流程");
        payload.put("nodes", List.of(Map.of("id", "start", "type", "start", "label", "开始")));
        payload.put("edges", List.of());
        assertThrows(IllegalArgumentException.class, () -> service.publishDesign("brokenGraphTest", payload, "admin"));
    }

    @Test
    void commandIsIdempotentAndAudited() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("processCode", "idempotentPublishTest");
        payload.put("processName", "幂等发布测试");
        payload.put("nodes", List.of(
                Map.of("id", "start", "type", "start", "label", "开始"),
                Map.of("id", "review", "type", "userTask", "label", "审核", "candidateGroups", "planner"),
                Map.of("id", "end", "type", "end", "label", "结束")
        ));
        payload.put("edges", List.of(
                Map.of("id", "start-review", "source", "start", "target", "review"),
                Map.of("id", "review-end", "source", "review", "target", "end")
        ));

        Map<String, Object> first = service.publishDesign("idempotentPublishTest", payload, "admin");
        Map<String, Object> replay = service.publishDesign("idempotentPublishTest", payload, "admin");

        assertEquals(first.get("definition_id"), replay.get("definition_id"));
        assertTrue(idempotencyRecordMapper.selectCount(new LambdaQueryWrapper<BpmIdempotencyRecord>()) > 0);
        assertNotNull(auditLogMapper.selectList(new LambdaQueryWrapper<BpmAuditLog>()
                .eq(BpmAuditLog::getActionCode, "PUBLISH_DESIGN")).stream().findFirst().orElse(null));
    }

    @Test
    void flowableLifecycleListenerRecordsEngineFacts() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("processCode", "listenerLifecycleTest");
        payload.put("processName", "引擎生命周期监听测试");
        payload.put("nodes", List.of(
                Map.of("id", "start", "type", "start", "label", "开始"),
                Map.of("id", "review", "type", "userTask", "label", "审核", "candidateGroups", "planner"),
                Map.of("id", "end", "type", "end", "label", "结束")
        ));
        payload.put("edges", List.of(
                Map.of("id", "start-review", "source", "start", "target", "review"),
                Map.of("id", "review-end", "source", "review", "target", "end")
        ));

        service.publishDesign("listenerLifecycleTest", payload, "admin");
        Map<String, Object> instance = service.start(Map.of(
                "processCode", "listenerLifecycleTest",
                "businessType", "COMMON",
                "title", "生命周期监听实例"
        ), "admin");
        String instanceId = String.valueOf(instance.get("flowable_instance_id"));

        assertTrue(eventLogMapper.selectCount(new LambdaQueryWrapper<BpmEventLog>()
                .eq(BpmEventLog::getProcessInstanceId, instanceId)
                .eq(BpmEventLog::getEventType, "PROCESS_STARTED")) > 0);
        assertTrue(eventLogMapper.selectCount(new LambdaQueryWrapper<BpmEventLog>()
                .eq(BpmEventLog::getProcessInstanceId, instanceId)
                .eq(BpmEventLog::getEventType, "TASK_CREATED")) > 0);

        service.cancel(instanceId, "admin");
        assertTrue(eventLogMapper.selectCount(new LambdaQueryWrapper<BpmEventLog>()
                .eq(BpmEventLog::getProcessInstanceId, instanceId)
                .eq(BpmEventLog::getEventType, "PROCESS_CANCELLED")) > 0);
    }
}
