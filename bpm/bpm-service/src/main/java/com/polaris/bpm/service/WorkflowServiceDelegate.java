package com.polaris.bpm.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.polaris.bpm.common.BpmBusinessException;
import com.polaris.bpm.mapper.WorkOrderMapper;
import com.polaris.bpm.model.entity.WorkOrder;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Safe default delegate used by service nodes created in the visual designer.
 * Business-specific integrations can replace this behavior by registering a
 * dedicated service key in the process design without changing the editor.
 */
@Component
public class WorkflowServiceDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(WorkflowServiceDelegate.class);
    private final WorkOrderMapper workOrderMapper;
    private Expression serviceKey;

    public WorkflowServiceDelegate(WorkOrderMapper workOrderMapper) { this.workOrderMapper = workOrderMapper; }

    @Override
    public void execute(DelegateExecution execution) {
        String processCode = execution.getProcessDefinitionId();
        String key = serviceKey == null ? execution.getCurrentActivityId() : String.valueOf(serviceKey.getValue(execution));
        log.info("Workflow business action executed: process={}, serviceKey={}", processCode, key);
        execution.setVariable("lastServiceNode", execution.getCurrentActivityId());
        execution.setVariable("lastServiceKey", key);
        execution.setVariable("serviceExecuted", true);
        if ("workOrder.updateStatus".equals(key) && execution.getVariable("businessId") != null) {
            String targetStatus = execution.getVariable("targetStatus") == null ? "PLANNED" : String.valueOf(execution.getVariable("targetStatus"));
            updateWorkOrderStatus(String.valueOf(execution.getVariable("businessId")), targetStatus);
        }
        if ("business.setStatus".equals(key)) {
            String targetStatus = execution.getVariable("targetStatus") == null ? "COMPLETED" : String.valueOf(execution.getVariable("targetStatus"));
            execution.setVariable("businessStatus", targetStatus);
        }
    }

    private void updateWorkOrderStatus(String id, String status) {
        try {
            workOrderMapper.update(null, new LambdaUpdateWrapper<WorkOrder>()
                    .eq(WorkOrder::getId, Long.valueOf(id))
                    .eq(WorkOrder::getDeleted, 0)
                    .set(WorkOrder::getStatus, status)
                    .set(WorkOrder::getUpdatedAt, LocalDateTime.now()));
        } catch (NumberFormatException ex) {
            throw new BpmBusinessException("工单 ID 必须是数字", ex);
        } catch (DataAccessException ex) {
            throw new BpmBusinessException("工单状态回写失败，请检查工单数据库连接", ex);
        }
    }
}
