package com.polaris.bpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.bpm.mapper.BpmProcessInstanceMapper;
import com.polaris.bpm.mapper.BpmTaskActionMapper;
import com.polaris.bpm.model.entity.BpmProcessInstance;
import com.polaris.bpm.model.entity.BpmTaskAction;
import com.polaris.bpm.service.ProcessDefinitionApplicationService;
import com.polaris.bpm.service.WorkflowMetricsApplicationService;
import com.polaris.bpm.service.WorkflowTaskApplicationService;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WorkflowMetricsApplicationServiceImpl implements WorkflowMetricsApplicationService {
    private final RepositoryService repositoryService;
    private final BpmProcessInstanceMapper instanceMapper;
    private final BpmTaskActionMapper actionMapper;
    private final WorkflowTaskApplicationService taskService;

    public WorkflowMetricsApplicationServiceImpl(RepositoryService repositoryService,
                                                 BpmProcessInstanceMapper instanceMapper,
                                                 BpmTaskActionMapper actionMapper,
                                                 WorkflowTaskApplicationService taskService) {
        this.repositoryService = repositoryService;
        this.instanceMapper = instanceMapper;
        this.actionMapper = actionMapper;
        this.taskService = taskService;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> overview(String actor) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("definitionCount", repositoryService.createProcessDefinitionQuery().latestVersion().count());
        result.put("runningCount", countByStatus("RUNNING"));
        result.put("approvedCount", countByStatus("APPROVED"));
        result.put("pendingCount", taskService.listTasks("todo", actor).size());
        result.put("todayActionCount", countActionsToday());
        result.put("health", "UP");
        return result;
    }

    private long countByStatus(String status) {
        return instanceMapper.selectCount(new LambdaQueryWrapper<BpmProcessInstance>()
                .eq(BpmProcessInstance::getStatus, status));
    }

    private long countActionsToday() {
        LocalDate today = LocalDate.now();
        return actionMapper.selectCount(new LambdaQueryWrapper<BpmTaskAction>()
                .eq(BpmTaskAction::getActionCode, "COMPLETE")
                .ge(BpmTaskAction::getCreatedAt, today.atStartOfDay())
                .lt(BpmTaskAction::getCreatedAt, today.plusDays(1).atStartOfDay()));
    }
}
