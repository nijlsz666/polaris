package com.polaris.bpm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("bpm_idempotency_record")
public class BpmIdempotencyRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("request_key")
    private String requestKey;
    @TableField("operation_name")
    private String operationName;
    @TableField("response_json")
    private String responseJson;
    private String status;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { this.requestKey = value; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String value) { this.operationName = value; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String value) { this.responseJson = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
}
