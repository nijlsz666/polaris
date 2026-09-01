package com.polaris.mes.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wh_warehouse")
public class WhWarehouse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String warehouseCode;
    private String warehouseName;
    private String warehouseType;
    private String ownerCode;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
