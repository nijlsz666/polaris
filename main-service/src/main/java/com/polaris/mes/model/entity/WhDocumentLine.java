package com.polaris.mes.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wh_document_line")
public class WhDocumentLine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long documentId;
    private Integer lineNo;
    private String materialCode;
    private String materialName;
    private String unit;
    private Integer plannedQty;
    private Integer actualQty;
    private String batchNo;
    private String fromLocationCode;
    private String toLocationCode;
    private String workOrderNo;
    private String qualityStatus;
    private LocalDateTime createdAt;
}
