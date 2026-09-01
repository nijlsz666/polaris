package com.polaris.mes.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("barcode")
public class Barcode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String barcode;
    private String barcodeType;
    private String materialCode;
    private String batchNo;
    private String status;
    private String sourceDocNo;
    private String warehouseCode;
    private String locationCode;
    private Integer printedCount;
    private LocalDateTime voidedAt;
    private LocalDateTime printedAt;
    private LocalDateTime createdAt;
}
