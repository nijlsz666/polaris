package com.polaris.mes.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wh_storage_area")
public class WhStorageArea {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String warehouseCode;
    private String areaCode;
    private String areaName;
    private String areaType;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
}
