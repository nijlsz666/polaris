package com.polaris.mes.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wh_document")
public class WhDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String documentNo;
    private String documentType;
    private String status;
    private String sourceDocNo;
    private String warehouseCode;
    private String fromWarehouseCode;
    private String toWarehouseCode;
    private String operatorName;
    private String remark;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
