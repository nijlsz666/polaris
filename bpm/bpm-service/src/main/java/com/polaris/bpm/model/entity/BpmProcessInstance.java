package com.polaris.bpm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("bpm_process_instance")
public class BpmProcessInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("flowable_instance_id")
    private String flowableInstanceId;
    @TableField("flowable_definition_id")
    private String flowableDefinitionId;
    @TableField("process_code")
    private String processCode;
    @TableField("business_type")
    private String businessType;
    @TableField("business_id")
    private String businessId;
    @TableField("business_key")
    private String businessKey;
    private String title;
    private String starter;
    private String status;
    @TableField("started_at")
    private LocalDateTime startedAt;
    @TableField("ended_at")
    private LocalDateTime endedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFlowableInstanceId() { return flowableInstanceId; }
    public void setFlowableInstanceId(String value) { this.flowableInstanceId = value; }
    public String getFlowableDefinitionId() { return flowableDefinitionId; }
    public void setFlowableDefinitionId(String value) { this.flowableDefinitionId = value; }
    public String getProcessCode() { return processCode; }
    public void setProcessCode(String value) { this.processCode = value; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String value) { this.businessType = value; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String value) { this.businessId = value; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String value) { this.businessKey = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getStarter() { return starter; }
    public void setStarter(String value) { this.starter = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { this.startedAt = value; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime value) { this.endedAt = value; }
}
