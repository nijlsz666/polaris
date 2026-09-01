package com.polaris.bpm.service.impl;

import com.polaris.bpm.annotation.AuditOperation;
import com.polaris.bpm.annotation.Idempotent;
import com.polaris.bpm.service.BpmFormDefinitionApplicationService;
import com.polaris.bpm.service.BpmService;
import com.polaris.bpm.service.ProcessDefinitionApplicationService;
import com.polaris.bpm.service.ProcessInstanceApplicationService;
import com.polaris.bpm.service.WorkflowMetricsApplicationService;
import com.polaris.bpm.service.WorkflowTaskApplicationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Backward-compatible application facade.
 *
 * <p>The public contract is intentionally stable for the current web client. Each
 * use case is implemented by a focused application service, so new workflow
 * capabilities do not grow this facade into another god class.</p>
 */
@Service
public class BpmServiceImpl implements BpmService {
    private final ProcessDefinitionApplicationService processDefinitions;
    private final BpmFormDefinitionApplicationService forms;
    private final ProcessInstanceApplicationService instances;
    private final WorkflowTaskApplicationService tasks;
    private final WorkflowMetricsApplicationService metrics;

    public BpmServiceImpl(ProcessDefinitionApplicationService processDefinitions,
                          BpmFormDefinitionApplicationService forms,
                          ProcessInstanceApplicationService instances,
                          WorkflowTaskApplicationService tasks,
                          WorkflowMetricsApplicationService metrics) {
        this.processDefinitions = processDefinitions;
        this.forms = forms;
        this.instances = instances;
        this.tasks = tasks;
        this.metrics = metrics;
    }

    @Override
    public Map<String, Object> overview(String actor) {
        return metrics.overview(actor);
    }

    @Override
    public List<Map<String, Object>> listDefinitions() {
        return processDefinitions.listDefinitions();
    }

    @Override
    public List<Map<String, Object>> listDesigns() {
        return processDefinitions.listDesigns();
    }

    @Override
    public Map<String, Object> getDesign(String code) {
        return processDefinitions.getDesign(code);
    }

    @Override
    @AuditOperation(action = "SAVE_DESIGN", resource = "PROCESS_DESIGN")
    public Map<String, Object> saveDesign(String code, Map<String, Object> payload, String actor, String status) {
        return processDefinitions.saveDesign(code, payload, actor, status);
    }

    @Override
    @Idempotent
    @AuditOperation(action = "PUBLISH_DESIGN", resource = "PROCESS_DEFINITION")
    public Map<String, Object> publishDesign(String code, Map<String, Object> payload, String actor) {
        return processDefinitions.publishDesign(code, payload, actor);
    }

    @Override
    public Map<String, Object> validateDesignPayload(Map<String, Object> payload) {
        return processDefinitions.validateDesignPayload(payload);
    }

    @Override
    @AuditOperation(action = "CREATE_DEFINITION", resource = "PROCESS_DEFINITION")
    public Map<String, Object> createDefinition(Map<String, Object> payload) {
        return processDefinitions.createDefinition(payload);
    }

    @Override
    @AuditOperation(action = "TOGGLE_DEFINITION", resource = "PROCESS_DEFINITION")
    public void toggleDefinition(String id, boolean suspended) {
        processDefinitions.toggleDefinition(id, suspended);
    }

    @Override
    public List<Map<String, Object>> listForms() {
        return forms.listForms();
    }

    @Override
    @AuditOperation(action = "CREATE_FORM", resource = "FORM")
    public void createForm(Map<String, Object> payload, String actor) {
        forms.createForm(payload, actor);
    }

    @Override
    @AuditOperation(action = "UPDATE_FORM", resource = "FORM")
    public void updateForm(String code, Map<String, Object> payload, String actor) {
        forms.updateForm(code, payload, actor);
    }

    @Override
    @Idempotent
    @AuditOperation(action = "START_PROCESS", resource = "PROCESS_INSTANCE")
    public Map<String, Object> start(Map<String, Object> payload, String actor) {
        return instances.start(payload, actor);
    }

    @Override
    public List<Map<String, Object>> listTasks(String scope, String actor) {
        return tasks.listTasks(scope, actor);
    }

    @Override
    public List<Map<String, Object>> listInstances(String status, String starter) {
        return instances.listInstances(status, starter);
    }

    @Override
    public Map<String, Object> detail(String instanceId) {
        return instances.detail(instanceId);
    }

    @Override
    @Idempotent
    @AuditOperation(action = "COMPLETE_TASK", resource = "TASK")
    public Map<String, Object> complete(String taskId, Map<String, Object> payload, String actor) {
        return tasks.complete(taskId, payload, actor);
    }

    @Override
    @AuditOperation(action = "CLAIM_TASK", resource = "TASK")
    public void claim(String taskId, String actor) {
        tasks.claim(taskId, actor);
    }

    @Override
    @AuditOperation(action = "UNCLAIM_TASK", resource = "TASK")
    public void unclaim(String taskId, String actor) {
        tasks.unclaim(taskId, actor);
    }

    @Override
    @AuditOperation(action = "DELEGATE_TASK", resource = "TASK")
    public void delegate(String taskId, String targetUser, String actor) {
        tasks.delegate(taskId, targetUser, actor);
    }

    @Override
    @AuditOperation(action = "COMMENT_TASK", resource = "TASK")
    public void comment(String taskId, String comment, String actor) {
        tasks.comment(taskId, comment, actor);
    }

    @Override
    @AuditOperation(action = "CANCEL_INSTANCE", resource = "PROCESS_INSTANCE")
    public void cancel(String instanceId, String actor) {
        instances.cancel(instanceId, actor);
    }

    @Override
    @AuditOperation(action = "SUSPEND_INSTANCE", resource = "PROCESS_INSTANCE")
    public void suspend(String instanceId, String actor) {
        instances.suspend(instanceId, actor);
    }

    @Override
    @AuditOperation(action = "RESUME_INSTANCE", resource = "PROCESS_INSTANCE")
    public void resume(String instanceId, String actor) {
        instances.resume(instanceId, actor);
    }
}
