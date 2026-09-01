package com.polaris.bpm.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.polaris.bpm.common.BpmBusinessException;
import com.polaris.bpm.mapper.WorkOrderMapper;
import com.polaris.bpm.model.entity.WorkOrder;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Anti-corruption layer around the manufacturing service's shared work order table. */
@Component
public class WorkOrderGateway {
    private final WorkOrderMapper mapper;

    public WorkOrderGateway(WorkOrderMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> find(long id) {
        try {
            WorkOrder order = mapper.selectOne(new LambdaQueryWrapper<WorkOrder>()
                    .select(WorkOrder::getId, WorkOrder::getOrderNo, WorkOrder::getProductCode,
                            WorkOrder::getProductName, WorkOrder::getPlanQty, WorkOrder::getStatus)
                    .eq(WorkOrder::getId, id)
                    .eq(WorkOrder::getDeleted, 0));
            if (order == null) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", order.getId());
            result.put("order_no", order.getOrderNo());
            result.put("product_code", order.getProductCode());
            result.put("product_name", order.getProductName());
            result.put("plan_qty", order.getPlanQty());
            result.put("status", order.getStatus());
            return result;
        } catch (DataAccessException ex) {
            throw new BpmBusinessException("工单表不可用，请确认 BPM 与工单系统使用同一个数据库", ex);
        }
    }

    public void updateStatus(String id, String status) {
        try {
            Long workOrderId = Long.valueOf(id);
            int affected = mapper.update(null, new LambdaUpdateWrapper<WorkOrder>()
                    .eq(WorkOrder::getId, workOrderId)
                    .eq(WorkOrder::getDeleted, 0)
                    .set(WorkOrder::getStatus, status)
                    .set(WorkOrder::getUpdatedAt, LocalDateTime.now()));
            if (affected == 0) {
                throw new BpmBusinessException("工单不存在或已删除：" + id);
            }
        } catch (NumberFormatException ex) {
            throw new BpmBusinessException("工单 ID 必须是数字", ex);
        } catch (DataAccessException ex) {
            throw new BpmBusinessException("工单状态回写失败，请检查工单数据库连接", ex);
        }
    }
}
