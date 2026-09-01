package com.polaris.bpm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("bpm_task_action")
public class BpmTaskAction {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("flowable_task_id")
    private String flowableTaskId;
    @TableField("flowable_instance_id")
    private String flowableInstanceId;
    @TableField("action_code")
    private String actionCode;
    private String actor;
    @TableField("comment_text")
    private String commentText;
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFlowableTaskId() { return flowableTaskId; }
    public void setFlowableTaskId(String value) { this.flowableTaskId = value; }
    public String getFlowableInstanceId() { return flowableInstanceId; }
    public void setFlowableInstanceId(String value) { this.flowableInstanceId = value; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String value) { this.actionCode = value; }
    public String getActor() { return actor; }
    public void setActor(String value) { this.actor = value; }
    public String getCommentText() { return commentText; }
    public void setCommentText(String value) { this.commentText = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
}
