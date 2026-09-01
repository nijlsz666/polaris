package com.polaris.bpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.bpm.mapper.BpmProcessInstanceMapper;
import com.polaris.bpm.mapper.BpmTaskActionMapper;
import com.polaris.bpm.model.entity.BpmProcessInstance;
import com.polaris.bpm.model.entity.BpmTaskAction;
import com.polaris.bpm.service.WorkflowTaskApplicationService;
import com.polaris.bpm.service.support.WorkOrderGateway;
import org.flowable.engine.HistoryService;
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
import java.util.Map;

@Service
public class WorkflowTaskApplicationServiceImpl implements WorkflowTaskApplicationService {
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RuntimeService runtimeService;
    private final BpmProcessInstanceMapper instanceMapper;
    private final BpmTaskActionMapper actionMapper;
    private final WorkOrderGateway workOrderGateway;

    public WorkflowTaskApplicationServiceImpl(TaskService taskService,
                                              HistoryService historyService,
                                              RuntimeService runtimeService,
                                              BpmProcessInstanceMapper instanceMapper,
                                              BpmTaskActionMapper actionMapper,
                                              WorkOrderGateway workOrderGateway) {
        this.taskService = taskService;
        this.historyService = historyService;
        this.runtimeService = runtimeService;
        this.instanceMapper = instanceMapper;
        this.actionMapper = actionMapper;
        this.workOrderGateway = workOrderGateway;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTasks(String scope, String actor) {
        if ("done".equalsIgnoreCase(scope)) {
            return doneTasks(actor);
        }
        String role = "admin".equals(actor) ? "admin" : actor;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Task task : taskService.createTaskQuery().orderByTaskCreateTime().desc().list()) {
            if (canOperate(task, actor, role)) {
                result.add(taskRow(task, "TODO", null));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> complete(String taskId, Map<String, Object> source, String actor) {
        Map<String, Object> payload = source == null ? Map.of() : source;
        Task task = getTask(taskId);
        assertCanOperate(task, actor);
        boolean approved = payload.get("approved") == null
                || Boolean.parseBoolean(String.valueOf(payload.get("approved")));
        String comment = nullable(payload.get("comment"));
        if (comment != null) {
            taskService.addComment(taskId, task.getProcessInstanceId(), comment);
        }
        taskService.complete(taskId, Map.of("approved", approved, "lastActor", actor));
        insertAction(taskId, task.getProcessInstanceId(), "COMPLETE", actor, comment);
        boolean finished = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId()).singleResult() == null;
        String status = finished ? (approved ? "APPROVED" : "REJECTED") : "RUNNING";
        updateInstanceStatus(task.getProcessInstanceId(), status);
        BpmProcessInstance instance = findEntity(task.getProcessInstanceId());
        if (finished && instance != null && "WORK_ORDER".equals(instance.getBusinessType())) {
            workOrderGateway.updateStatus(instance.getBusinessId(), approved ? "PLANNED" : "REJECTED");
        }
        return Map.of("taskId", taskId, "status", status, "approved", approved);
    }

    @Override
    @Transactional
    public void claim(String taskId, String actor) {
        Task task = getTask(taskId);
        assertCanOperate(task, actor);
        if (task.getAssignee() != null && !actor.equals(task.getAssignee())) {
            throw new IllegalArgumentException("任务已被其他人签收");
        }
        taskService.setAssignee(taskId, actor);
        insertAction(taskId, task.getProcessInstanceId(), "CLAIM", actor, null);
    }

    @Override
    @Transactional
    public void unclaim(String taskId, String actor) {
        Task task = getTask(taskId);
        if (!actor.equals(task.getAssignee())) {
            throw new IllegalArgumentException("只能退回自己签收的任务");
        }
        taskService.setAssignee(taskId, null);
        insertAction(taskId, task.getProcessInstanceId(), "UNCLAIM", actor, null);
    }

    @Override
    @Transactional
    public void delegate(String taskId, String targetUser, String actor) {
        String target = nullable(targetUser);
        if (target == null) {
            throw new IllegalArgumentException("转办目标用户不能为空");
        }
        Task task = getTask(taskId);
        assertCanOperate(task, actor);
        if (actor.equals(target)) {
            throw new IllegalArgumentException("不能将任务转办给自己");
        }
        taskService.setAssignee(taskId, target);
        insertAction(taskId, task.getProcessInstanceId(), "DELEGATE", actor, "转办给：" + target);
    }

    @Override
    @Transactional
    public void comment(String taskId, String comment, String actor) {
        String text = nullable(comment);
        if (text == null) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        if (text.length() > 500) {
            throw new IllegalArgumentException("评论内容不能超过 500 个字符");
        }
        Task task = getTask(taskId);
        assertCanOperate(task, actor);
        taskService.addComment(taskId, task.getProcessInstanceId(), text);
        insertAction(taskId, task.getProcessInstanceId(), "COMMENT", actor, text);
    }

    private List<Map<String, Object>> doneTasks(String actor) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BpmTaskAction action : actionMapper.selectList(new LambdaQueryWrapper<BpmTaskAction>()
                .eq(BpmTaskAction::getActor, actor)
                .eq(BpmTaskAction::getActionCode, "COMPLETE")
                .orderByDesc(BpmTaskAction::getCreatedAt))) {
            HistoricTaskInstance task = historyService.createHistoricTaskInstanceQuery()
                    .taskId(action.getFlowableTaskId()).singleResult();
            if (task != null) {
                rows.add(historicTaskRow(task, "DONE", action));
            }
        }
        return rows;
    }

    private Task getTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || findEntity(task.getProcessInstanceId()) == null) {
            throw new IllegalArgumentException("待办任务不存在或已处理");
        }
        return task;
    }

    private void assertCanOperate(Task task, String actor) {
        if (!canOperate(task, actor, "admin".equals(actor) ? "admin" : actor)) {
            throw new IllegalArgumentException("当前用户没有处理该任务的权限");
        }
    }

    private boolean canOperate(Task task, String actor, String role) {
        if ("admin".equals(role) || actor.equals(task.getAssignee())) {
            return true;
        }
        if (task.getAssignee() != null) {
            return false;
        }
        return candidateGroups(task.getId()).contains(role);
    }

    private List<String> candidateGroups(String taskId) {
        return taskService.getIdentityLinksForTask(taskId).stream()
                .map(IdentityLink::getGroupId).filter(java.util.Objects::nonNull).toList();
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
        row.put("candidateGroup", candidateGroups(task.getId()).stream().findFirst().orElse(null));
        row.put("startTime", task.getCreateTime());
        row.put("endTime", task.getDueDate());
        row.put("status", status);
        if (action != null) {
            row.put("comment", action.get("comment_text"));
            row.put("actionTime", action.get("created_at"));
        }
        return row;
    }

    private Map<String, Object> historicTaskRow(HistoricTaskInstance task, String status, BpmTaskAction action) {
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
        row.put("endTime", task.getEndTime());
        row.put("status", status);
        row.put("comment", action.getCommentText());
        row.put("actionTime", action.getCreatedAt());
        return row;
    }

    private BpmProcessInstance findEntity(String instanceId) {
        return instanceMapper.selectOne(new LambdaQueryWrapper<BpmProcessInstance>()
                .eq(BpmProcessInstance::getFlowableInstanceId, instanceId));
    }

    private void updateInstanceStatus(String instanceId, String status) {
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

    private void insertAction(String taskId, String instanceId, String action, String actor, String comment) {
        BpmTaskAction entity = new BpmTaskAction();
        entity.setFlowableTaskId(taskId);
        entity.setFlowableInstanceId(instanceId);
        entity.setActionCode(action);
        entity.setActor(actor);
        entity.setCommentText(comment);
        actionMapper.insert(entity);
    }

    private static String nullable(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }
}
