package com.polaris.bpm.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.bpm.mapper.BpmEventLogMapper;
import com.polaris.bpm.mapper.BpmProcessInstanceMapper;
import com.polaris.bpm.model.entity.BpmEventLog;
import com.polaris.bpm.model.entity.BpmProcessInstance;
import com.polaris.bpm.service.support.WorkOrderGateway;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.engine.delegate.event.FlowableCancelledEvent;
import org.flowable.engine.delegate.event.FlowableProcessStartedEvent;
import org.flowable.task.api.Task;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine-level listener for projection synchronization and technical event auditing.
 *
 * <p>Business commands remain responsible for user intent (approval, claim and
 * comment). This listener is responsible for facts emitted by Flowable itself,
 * so timer jobs, service tasks, REST calls and future integrations all follow the
 * same lifecycle path.</p>
 */
@Component
public class FlowableLifecycleEventListener extends AbstractFlowableEngineEventListener {
    private final BpmEventLogMapper eventLogMapper;
    private final BpmProcessInstanceMapper instanceMapper;
    private final WorkOrderGateway workOrderGateway;
    private final ObjectMapper objectMapper;

    public FlowableLifecycleEventListener(BpmEventLogMapper eventLogMapper,
                                          BpmProcessInstanceMapper instanceMapper,
                                          WorkOrderGateway workOrderGateway,
                                          ObjectMapper objectMapper) {
        super(EnumSet.of(
                FlowableEngineEventType.TASK_CREATED,
                FlowableEngineEventType.TASK_ASSIGNED,
                FlowableEngineEventType.TASK_COMPLETED,
                FlowableEngineEventType.PROCESS_STARTED,
                FlowableEngineEventType.PROCESS_COMPLETED,
                FlowableEngineEventType.PROCESS_CANCELLED));
        this.eventLogMapper = eventLogMapper;
        this.instanceMapper = instanceMapper;
        this.workOrderGateway = workOrderGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void taskCreated(FlowableEngineEntityEvent event) {
        record(event, "TASK_CREATED", taskPayload(event));
    }

    @Override
    protected void taskAssigned(FlowableEngineEntityEvent event) {
        record(event, "TASK_ASSIGNED", taskPayload(event));
    }

    @Override
    protected void taskCompleted(FlowableEngineEntityEvent event) {
        record(event, "TASK_COMPLETED", taskPayload(event));
    }

    @Override
    protected void processStarted(FlowableProcessStartedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("variables", event.getVariables());
        record(event, "PROCESS_STARTED", payload);
    }

    @Override
    protected void processCompleted(FlowableEngineEntityEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String status = approved(event) ? "APPROVED" : "REJECTED";
        payload.put("status", status);
        record(event, "PROCESS_COMPLETED", payload);
        synchronizeInstance(event.getProcessInstanceId(), status);
    }

    @Override
    protected void processCancelled(FlowableCancelledEvent event) {
        record(event, "PROCESS_CANCELLED", Map.of("reason", "engine cancellation"));
        synchronizeInstance(event.getProcessInstanceId(), "CANCELLED");
    }

    @Override
    public boolean isFailOnException() {
        return true;
    }

    private Map<String, Object> taskPayload(FlowableEngineEntityEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (event.getEntity() instanceof Task task) {
            payload.put("taskId", task.getId());
            payload.put("name", task.getName());
            payload.put("assignee", task.getAssignee());
            payload.put("processDefinitionId", task.getProcessDefinitionId());
        }
        return payload;
    }

    private boolean approved(FlowableEngineEntityEvent event) {
        try {
            DelegateExecution execution = getExecution(event);
            Object value = execution == null ? null : execution.getVariable("approved");
            return value == null || Boolean.parseBoolean(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    @Transactional
    protected void record(org.flowable.common.engine.api.delegate.event.FlowableEvent event,
                          String eventType, Map<String, Object> payload) {
        String processInstanceId = processInstanceId(event);
        String taskId = event instanceof FlowableEngineEntityEvent entityEvent && entityEvent.getEntity() instanceof Task task
                ? task.getId() : null;
        String activityId = event instanceof org.flowable.engine.delegate.event.FlowableActivityEvent activityEvent
                ? activityEvent.getActivityId() : null;
        String eventKey = eventType + ":" + safe(processInstanceId) + ":" + safe(taskId) + ":" + safe(activityId);
        if (eventLogMapper.selectOne(new LambdaQueryWrapper<BpmEventLog>()
                .eq(BpmEventLog::getEventKey, eventKey)) != null) {
            return;
        }
        BpmEventLog log = new BpmEventLog();
        log.setEventKey(eventKey);
        log.setEventType(eventType);
        log.setProcessInstanceId(processInstanceId);
        log.setTaskId(taskId);
        log.setActivityId(activityId);
        log.setPayloadJson(json(payload));
        log.setProcessed(Boolean.TRUE);
        try {
            eventLogMapper.insert(log);
        } catch (DuplicateKeyException ignored) {
            // A retried Flowable command is expected to converge on the first event row.
        }
    }

    private void synchronizeInstance(String instanceId, String status) {
        if (instanceId == null) {
            return;
        }
        BpmProcessInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<BpmProcessInstance>()
                .eq(BpmProcessInstance::getFlowableInstanceId, instanceId));
        if (instance == null) {
            return;
        }
        instance.setStatus(status);
        if (List.of("APPROVED", "REJECTED", "CANCELLED").contains(status)) {
            instance.setEndedAt(LocalDateTime.now());
        }
        instanceMapper.update(null, new LambdaUpdateWrapper<BpmProcessInstance>()
                .eq(BpmProcessInstance::getId, instance.getId())
                .set(BpmProcessInstance::getStatus, status)
                .set(List.of("APPROVED", "REJECTED", "CANCELLED").contains(status),
                        BpmProcessInstance::getEndedAt, instance.getEndedAt()));
        if ("WORK_ORDER".equals(instance.getBusinessType()) && instance.getBusinessId() != null) {
            workOrderGateway.updateStatus(instance.getBusinessId(), "APPROVED".equals(status) ? "PLANNED" : "REJECTED".equals(status) ? "REJECTED" : "PLANNED");
        }
    }

    private String processInstanceId(org.flowable.common.engine.api.delegate.event.FlowableEvent event) {
        if (event instanceof org.flowable.common.engine.api.delegate.event.FlowableEngineEvent engineEvent) {
            return engineEvent.getProcessInstanceId();
        }
        return null;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }
}
