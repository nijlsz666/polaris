package com.polaris.mes.service;

import java.util.List;
import java.util.Map;

public interface BpmApplicationService {
    Map<String, Object> overview();
    List<Map<String, Object>> listDefinitions();
    List<Map<String, Object>> listBindings();
    Map<String, Object> bindProcess(String businessFunction, Map<String, Object> payload, String actor);
    Map<String, Object> createDefinition(Map<String, Object> payload, String actor);
    void toggleDefinition(String definitionId, boolean suspended);
    List<Map<String, Object>> listForms();
    void createForm(Map<String, Object> payload, String actor);
    void updateForm(String code, Map<String, Object> payload, String actor);
    Map<String, Object> startWorkOrderApproval(long id, String actor);
    Map<String, Object> startProcess(Map<String, Object> payload, String actor);
    List<Map<String, Object>> listTasks(String scope, String actor);
    List<Map<String, Object>> listInstances(String status, String starter);
    List<Map<String, Object>> listInstancesByBusiness(String businessType, String businessId);
    Map<String, Object> instanceDetail(String instanceId);
    Map<String, Object> completeTask(String taskId, Map<String, Object> payload, String actor);
    void claimTask(String taskId, String actor);
    void unclaimTask(String taskId, String actor);
    void cancelInstance(String instanceId, String actor);
}
