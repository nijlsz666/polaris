package com.polaris.bpm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("bpm_form_definition")
public class BpmFormDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("form_code")
    private String formCode;
    @TableField("form_name")
    private String formName;
    @TableField("business_type")
    private String businessType;
    @TableField("schema_json")
    private String schemaJson;
    private String status;
    @TableField("updated_by")
    private String updatedBy;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFormCode() { return formCode; }
    public void setFormCode(String value) { this.formCode = value; }
    public String getFormName() { return formName; }
    public void setFormName(String value) { this.formName = value; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String value) { this.businessType = value; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String value) { this.schemaJson = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String value) { this.updatedBy = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
}
