package com.polaris.mes.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory")
public class Inventory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String materialCode;
    private String materialName;
    private String warehouseCode;
    private String locationCode;
    private String batchNo;
    private Integer availableQty;
    private Integer lockedQty;
    private Integer reservedQty;
    private Integer inTransitQty;
    private String unit;
    private Integer safetyStock;
    private String stockStatus;
    private LocalDate expiryDate;
    @Version
    private Long versionNo;
    private LocalDateTime updatedAt;
}
