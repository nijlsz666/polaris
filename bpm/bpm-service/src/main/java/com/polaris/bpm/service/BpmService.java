package com.polaris.bpm.service;

import java.util.List;
import java.util.Map;

/** Application service contract consumed by controllers and bootstrap jobs. */
public interface BpmService {
    Map<String, Object> overview(String actor);
    List<Map<String, Object>> listDefinitions();
    List<Map<String, Object>> listDesigns();
    Map<String, Object> getDesign(String code);
    Map<String, Object> saveDesign(String code, Map<String, Object> payload, String actor, String status);
    Map<String, Object> publishDesign(String code, Map<String, Object> payload, String actor);
    Map<String, Object> validateDesignPayload(Map<String, Object> payload);
    Map<String, Object> createDefinition(Map<String, Object> payload);
    void toggleDefinition(String id, boolean suspended);
    List<Map<String, Object>> listForms();
    void createForm(Map<String, Object> payload, String actor);
    void updateForm(String code, Map<String, Object> payload, String actor);
    Map<String, Object> start(Map<String, Object> payload, String actor);
    List<Map<String, Object>> listTasks(String scope, String actor);
    List<Map<String, Object>> listInstances(String status, String starter);
    Map<String, Object> detail(String instanceId);
    Map<String, Object> complete(String taskId, Map<String, Object> payload, String actor);
    void claim(String taskId, String actor);
    void unclaim(String taskId, String actor);
    void delegate(String taskId, String targetUser, String actor);
    void comment(String taskId, String comment, String actor);
    void cancel(String instanceId, String actor);
    void suspend(String instanceId, String actor);
    void resume(String instanceId, String actor);
}
