package com.polaris.bpm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("bpm_audit_log")
public class BpmAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String actor;
    @TableField("action_code")
    private String actionCode;
    @TableField("resource_type")
    private String resourceType;
    private String operation;
    @TableField("request_summary")
    private String requestSummary;
    private Boolean success;
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getActor() { return actor; }
    public void setActor(String value) { this.actor = value; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String value) { this.actionCode = value; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String value) { this.resourceType = value; }
    public String getOperation() { return operation; }
    public void setOperation(String value) { this.operation = value; }
    public String getRequestSummary() { return requestSummary; }
    public void setRequestSummary(String value) { this.requestSummary = value; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean value) { this.success = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
}
