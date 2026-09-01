package com.polaris.bpm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Idempotent technical event ledger emitted by the Flowable engine. */
@TableName("bpm_event_log")
public class BpmEventLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("event_key")
    private String eventKey;
    @TableField("event_type")
    private String eventType;
    @TableField("process_instance_id")
    private String processInstanceId;
    @TableField("task_id")
    private String taskId;
    @TableField("activity_id")
    private String activityId;
    @TableField("payload_json")
    private String payloadJson;
    private Boolean processed;
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String value) { eventKey = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String value) { processInstanceId = value; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { taskId = value; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String value) { activityId = value; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String value) { payloadJson = value; }
    public Boolean getProcessed() { return processed; }
    public void setProcessed(Boolean value) { processed = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
