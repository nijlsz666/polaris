package com.polaris.mes.service.impl;

import com.polaris.mes.service.BpmApplicationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Adapter boundary kept separate so the controller is independent of Flowable orchestration details. */
@Service
public class BpmApplicationServiceImpl implements BpmApplicationService {
    private final com.polaris.mes.service.BpmService delegate;
    public BpmApplicationServiceImpl(com.polaris.mes.service.BpmService delegate) { this.delegate = delegate; }
    @Override public Map<String, Object> overview() { return delegate.overview(); }
    @Override public List<Map<String, Object>> listDefinitions() { return delegate.listDefinitions(); }
    @Override public List<Map<String, Object>> listBindings() { return delegate.listBindings(); }
    @Override public Map<String, Object> bindProcess(String businessFunction, Map<String, Object> payload, String actor) { return delegate.bindProcess(businessFunction, payload, actor); }
    @Override public Map<String, Object> createDefinition(Map<String, Object> payload, String actor) { return delegate.createDefinition(payload, actor); }
    @Override public void toggleDefinition(String definitionId, boolean suspended) { delegate.toggleDefinition(definitionId, suspended); }
    @Override public List<Map<String, Object>> listForms() { return delegate.listForms(); }
    @Override public void createForm(Map<String, Object> payload, String actor) { delegate.createForm(payload, actor); }
    @Override public void updateForm(String code, Map<String, Object> payload, String actor) { delegate.updateForm(code, payload, actor); }
    @Override public Map<String, Object> startWorkOrderApproval(long id, String actor) { return delegate.startWorkOrderApproval(id, actor); }
    @Override public Map<String, Object> startProcess(Map<String, Object> payload, String actor) { return delegate.startProcess(payload, actor); }
    @Override public List<Map<String, Object>> listTasks(String scope, String actor) { return delegate.listTasks(scope, actor); }
    @Override public List<Map<String, Object>> listInstances(String status, String starter) { return delegate.listInstances(status, starter); }
    @Override public List<Map<String, Object>> listInstancesByBusiness(String businessType, String businessId) { return delegate.listInstancesByBusiness(businessType, businessId); }
    @Override public Map<String, Object> instanceDetail(String instanceId) { return delegate.instanceDetail(instanceId); }
    @Override public Map<String, Object> completeTask(String taskId, Map<String, Object> payload, String actor) { return delegate.completeTask(taskId, payload, actor); }
    @Override public void claimTask(String taskId, String actor) { delegate.claimTask(taskId, actor); }
    @Override public void unclaimTask(String taskId, String actor) { delegate.unclaimTask(taskId, actor); }
    @Override public void cancelInstance(String instanceId, String actor) { delegate.cancelInstance(instanceId, actor); }
}
