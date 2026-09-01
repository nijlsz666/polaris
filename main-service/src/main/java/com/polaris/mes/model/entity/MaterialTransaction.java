package com.polaris.mes.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("material_transaction")
public class MaterialTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String transactionNo;
    private String transactionType;
    private String materialCode;
    private String materialName;
    private String warehouseCode;
    private String locationCode;
    private String batchNo;
    private Integer quantity;
    private String unit;
    private String operatorName;
    private String sourceDocNo;
    private String documentNo;
    private String fromWarehouseCode;
    private String fromLocationCode;
    private String toWarehouseCode;
    private String toLocationCode;
    private String reasonCode;
    private String idempotencyKey;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
}
