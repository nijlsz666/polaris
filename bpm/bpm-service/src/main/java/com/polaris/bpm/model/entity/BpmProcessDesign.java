package com.polaris.bpm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("bpm_process_design")
public class BpmProcessDesign {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("process_code")
    private String processCode;
    @TableField("process_name")
    private String processName;
    private String description;
    private String category;
    @TableField("process_type")
    private String processType;
    @TableField("trigger_type")
    private String triggerType;
    private Integer version;
    @TableField("design_json")
    private String designJson;
    private String status;
    @TableField("updated_by")
    private String updatedBy;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProcessCode() { return processCode; }
    public void setProcessCode(String value) { this.processCode = value; }
    public String getProcessName() { return processName; }
    public void setProcessName(String value) { this.processName = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { this.category = value; }
    public String getProcessType() { return processType; }
    public void setProcessType(String value) { this.processType = value; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String value) { this.triggerType = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version = value; }
    public String getDesignJson() { return designJson; }
    public void setDesignJson(String value) { this.designJson = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String value) { this.updatedBy = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
}
