package com.polaris.mes.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wh_location")
public class WhLocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String warehouseCode;
    private String areaCode;
    private String locationCode;
    private String locationName;
    private String locationType;
    private Integer capacityQty;
    private String status;
    private LocalDateTime createdAt;
}
