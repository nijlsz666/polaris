package com.polaris.mes.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("wh_batch")
public class WhBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String materialCode;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String supplierCode;
    private String qualityStatus;
    private String batchStatus;
    private String remark;
    private LocalDateTime createdAt;
}
