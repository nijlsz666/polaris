package com.polaris.bpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.bpm.mapper.BpmEventLogMapper;
import com.polaris.bpm.model.entity.BpmEventLog;
import com.polaris.bpm.service.WorkflowEventQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowEventQueryServiceImpl implements WorkflowEventQueryService {
    private final BpmEventLogMapper eventLogMapper;

    public WorkflowEventQueryServiceImpl(BpmEventLogMapper eventLogMapper) {
        this.eventLogMapper = eventLogMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String instanceId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 500));
        return eventLogMapper.selectList(new LambdaQueryWrapper<BpmEventLog>()
                        .eq(instanceId != null && !instanceId.isBlank(), BpmEventLog::getProcessInstanceId, instanceId)
                        .orderByDesc(BpmEventLog::getCreatedAt)
                        .last("limit " + limit))
                .stream().map(this::row).toList();
    }

    private Map<String, Object> row(BpmEventLog entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("eventKey", entity.getEventKey());
        row.put("eventType", entity.getEventType());
        row.put("processInstanceId", entity.getProcessInstanceId());
        row.put("taskId", entity.getTaskId());
        row.put("activityId", entity.getActivityId());
        row.put("payloadJson", entity.getPayloadJson());
        row.put("processed", entity.getProcessed());
        row.put("createdAt", entity.getCreatedAt());
        return row;
    }
}
