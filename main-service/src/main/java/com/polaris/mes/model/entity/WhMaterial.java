package com.polaris.mes.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wh_material")
public class WhMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String materialCode;
    private String materialName;
    private String materialType;
    private String unit;
    private Boolean lotControl;
    private Boolean serialControl;
    private Integer shelfLifeDays;
    private Integer safetyStock;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
