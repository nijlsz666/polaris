package com.polaris.bpm.service;

import java.util.List;
import java.util.Map;

public interface WorkflowTaskApplicationService {
    List<Map<String, Object>> listTasks(String scope, String actor);
    Map<String, Object> complete(String taskId, Map<String, Object> payload, String actor);
    void claim(String taskId, String actor);
    void unclaim(String taskId, String actor);
    void delegate(String taskId, String targetUser, String actor);
    void comment(String taskId, String comment, String actor);
}
