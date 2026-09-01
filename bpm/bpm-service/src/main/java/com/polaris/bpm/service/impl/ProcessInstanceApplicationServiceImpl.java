package com.polaris.bpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.bpm.common.BpmBusinessException;
import com.polaris.bpm.mapper.BpmProcessInstanceMapper;
import com.polaris.bpm.mapper.BpmTaskActionMapper;
import com.polaris.bpm.model.entity.BpmProcessInstance;
import com.polaris.bpm.model.entity.BpmTaskAction;
import com.polaris.bpm.service.ProcessInstanceApplicationService;
import com.polaris.bpm.service.support.WorkOrderGateway;
import com.polaris.bpm.service.support.WorkflowPayload;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProcessInstanceApplicationServiceImpl implements ProcessInstanceApplicationService {
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final BpmProcessInstanceMapper instanceMapper;
    private final BpmTaskActionMapper actionMapper;
    private final WorkOrderGateway workOrderGateway;

    public ProcessInstanceApplicationServiceImpl(RepositoryService repositoryService,
                                                 RuntimeService runtimeService,
                                                 TaskService taskService,
                                                 HistoryService historyService,
                                                 BpmProcessInstanceMapper instanceMapper,
                                                 BpmTaskActionMapper actionMapper,
                                                 WorkOrderGateway workOrderGateway) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.instanceMapper = instanceMapper;
        this.actionMapper = actionMapper;
        this.workOrderGateway = workOrderGateway;
    }

    @Override
    @Transactional
    public Map<String, Object> start(Map<String, Object> source, String actor) {
        Map<String, Object> payload = source == null ? Map.of() : source;
        String code = WorkflowPayload.text(payload.get("processCode"), null, "workOrderApproval");
        String type = WorkflowPayload.text(payload.get("businessType"), null, "COMMON").toUpperCase(Locale.ROOT);
        String businessId = WorkflowPayload.nullable(payload.get("businessId"));
        String title = WorkflowPayload.text(payload.get("title"), null, type + " 审批申请");
        if ("WORK_ORDER".equals(type)) {
            if (businessId == null) {
                throw new IllegalArgumentException("工单 ID 不能为空");
            }
            Map<String, Object> order;
            try {
                order = workOrderGateway.find(Long.parseLong(businessId));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("工单 ID 必须是数字", ex);
            }
            if (order == null) {
                throw new IllegalArgumentException("工单不存在或已删除");
            }
            title = WorkflowPayload.text(payload.get("title"), null, "工单 " + order.get("order_no") + " 发布审批");
        }
        var definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(code).latestVersion().singleResult();
        if (definition == null) {
            throw new IllegalArgumentException("未找到流程定义：" + code);
        }
        String businessKey = WorkflowPayload.text(payload.get("businessKey"), null,
                type + ":" + WorkflowPayload.text(businessId, null, "NA") + ":" + System.currentTimeMillis());
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("starter", actor);
        variables.put("businessType", type);
        variables.put("businessId", businessId);
        variables.put("title", title);
        Object supplied = payload.get("variables");
        if (supplied instanceof Map<?, ?> map) {
            map.forEach((key, value) -> variables.put(String.valueOf(key), value));
        }
        var processInstance = runtimeService.startProcessInstanceByKey(code, businessKey, variables);
        insertInstance(processInstance.getId(), definition.getId(), code, type, businessId, businessKey, title, actor);
        if ("WORK_ORDER".equals(type)) {
            workOrderGateway.updateStatus(businessId, "PENDING_APPROVAL");
        }
        return findInstanceRow(processInstance.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listInstances(String status, String starter) {
        LambdaQueryWrapper<BpmProcessInstance> query = new LambdaQueryWrapper<>();
        query.eq(status != null && !status.isBlank(), BpmProcessInstance::getStatus, status)
                .eq(starter != null && !starter.isBlank(), BpmProcessInstance::getStarter, starter)
                .orderByDesc(BpmProcessInstance::getStartedAt);
        return instanceMapper.selectList(query).stream().map(this::instanceRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> detail(String instanceId) {
        Map<String, Object> instance = findInstanceRow(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("流程实例不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance", instance);
        result.put("tasks", taskService.createTaskQuery().processInstanceId(instanceId).list()
                .stream().map(task -> taskRow(task, "TODO", null)).toList());
        List<Map<String, Object>> history = new ArrayList<>();
        for (HistoricTaskInstance task : historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(instanceId).orderByHistoricTaskInstanceStartTime().asc().list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", task.getId());
            row.put("name", task.getName());
            row.put("assignee", task.getAssignee());
            row.put("startTime", task.getCreateTime());
            row.put("endTime", task.getEndTime());
            history.add(row);
        }
        result.put("history", history);
        result.put("actions", actionMapper.selectList(new LambdaQueryWrapper<BpmTaskAction>()
                        .eq(BpmTaskAction::getFlowableInstanceId, instanceId)
                        .orderByAsc(BpmTaskAction::getCreatedAt))
                .stream().map(this::actionRow).toList());
        return result;
    }

    @Override
    @Transactional
    public void cancel(String instanceId, String actor) {
        Map<String, Object> instance = findInstanceRow(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("流程实例不存在");
        }
        if (!actor.equals(String.valueOf(instance.get("starter"))) && !actor.equals("admin")) {
            throw new IllegalArgumentException("只有发起人或管理员可以撤回流程");
        }
        if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult() != null) {
            runtimeService.deleteProcessInstance(instanceId, "业务撤回：" + actor);
        }
        updateInstanceStatus(instanceId, "CANCELLED");
        if ("WORK_ORDER".equals(String.valueOf(instance.get("business_type")))) {
            workOrderGateway.updateStatus(String.valueOf(instance.get("business_id")), "PLANNED");
        }
    }

    @Override
    @Transactional
    public void suspend(String instanceId, String actor) {
        BpmProcessInstance instance = requireInstance(instanceId);
        assertInstanceOperator(instance, actor);
        if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult() == null) {
            throw new IllegalArgumentException("流程实例已结束，不能挂起");
        }
        runtimeService.suspendProcessInstanceById(instanceId);
        updateInstanceStatus(instanceId, "SUSPENDED");
    }

    @Override
    @Transactional
    public void resume(String instanceId, String actor) {
        BpmProcessInstance instance = requireInstance(instanceId);
        assertInstanceOperator(instance, actor);
        runtimeService.activateProcessInstanceById(instanceId);
        updateInstanceStatus(instanceId, "RUNNING");
    }

    public BpmProcessInstance findEntity(String instanceId) {
        return instanceMapper.selectOne(new LambdaQueryWrapper<BpmProcessInstance>()
                .eq(BpmProcessInstance::getFlowableInstanceId, instanceId));
    }

    public void updateInstanceStatus(String instanceId, String status) {
        BpmProcessInstance instance = findEntity(instanceId);
        if (instance == null) {
            return;
        }
        instance.setStatus(status);
        if (List.of("APPROVED", "REJECTED", "CANCELLED").contains(status)) {
            instance.setEndedAt(LocalDateTime.now());
        }
        instanceMapper.updateById(instance);
    }

    private BpmProcessInstance requireInstance(String instanceId) {
        BpmProcessInstance instance = findEntity(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("流程实例不存在");
        }
        return instance;
    }

    private void assertInstanceOperator(BpmProcessInstance instance, String actor) {
        if (!"admin".equals(actor) && !actor.equals(instance.getStarter())) {
            throw new IllegalArgumentException("只有发起人或管理员可以管理流程实例");
        }
    }

    private void insertInstance(String instanceId, String definitionId, String code, String type,
                                String businessId, String businessKey, String title, String starter) {
        BpmProcessInstance instance = new BpmProcessInstance();
        instance.setFlowableInstanceId(instanceId);
        instance.setFlowableDefinitionId(definitionId);
        instance.setProcessCode(code);
        instance.setBusinessType(type);
        instance.setBusinessId(businessId);
        instance.setBusinessKey(businessKey);
        instance.setTitle(title);
        instance.setStarter(starter);
        instance.setStatus("RUNNING");
        instanceMapper.insert(instance);
    }

    private Map<String, Object> findInstanceRow(String instanceId) {
        BpmProcessInstance instance = findEntity(instanceId);
        return instance == null ? null : instanceRow(instance);
    }

    private Map<String, Object> instanceRow(BpmProcessInstance entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("flowable_instance_id", entity.getFlowableInstanceId());
        row.put("flowable_definition_id", entity.getFlowableDefinitionId());
        row.put("process_code", entity.getProcessCode());
        row.put("business_type", entity.getBusinessType());
        row.put("business_id", entity.getBusinessId());
        row.put("business_key", entity.getBusinessKey());
        row.put("title", entity.getTitle());
        row.put("starter", entity.getStarter());
        row.put("status", entity.getStatus());
        row.put("started_at", entity.getStartedAt());
        row.put("ended_at", entity.getEndedAt());
        return row;
    }

    private Map<String, Object> taskRow(Task task, String status, Map<String, Object> action) {
        BpmProcessInstance instance = findEntity(task.getProcessInstanceId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskId", task.getId());
        row.put("instanceId", task.getProcessInstanceId());
        row.put("processCode", instance == null ? null : instance.getProcessCode());
        row.put("businessType", instance == null ? null : instance.getBusinessType());
        row.put("businessId", instance == null ? null : instance.getBusinessId());
        row.put("title", instance == null ? task.getName() : instance.getTitle());
        row.put("name", task.getName());
        row.put("assignee", task.getAssignee());
        row.put("candidateGroup", taskService.getIdentityLinksForTask(task.getId()).stream()
                .map(IdentityLink::getGroupId).filter(java.util.Objects::nonNull).findFirst().orElse(null));
        row.put("startTime", task.getCreateTime());
        row.put("endTime", task.getDueDate());
        row.put("status", status);
        if (action != null) {
            row.put("comment", action.get("comment_text"));
            row.put("actionTime", action.get("created_at"));
        }
        return row;
    }

    private Map<String, Object> actionRow(BpmTaskAction entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("flowable_task_id", entity.getFlowableTaskId());
        row.put("flowable_instance_id", entity.getFlowableInstanceId());
        row.put("action_code", entity.getActionCode());
        row.put("actor", entity.getActor());
        row.put("comment_text", entity.getCommentText());
        row.put("created_at", entity.getCreatedAt());
        return row;
    }
}
