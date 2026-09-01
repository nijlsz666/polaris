package com.polaris.mes.service;

import com.polaris.mes.common.TenantContext;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class BpmService {
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final JdbcTemplate jdbc;

    public BpmService(RepositoryService repositoryService, RuntimeService runtimeService, TaskService taskService,
                      HistoryService historyService, JdbcTemplate jdbc) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.jdbc = jdbc;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("definitionCount", dataListDefinitions().size());
        result.put("runningCount", dataCountInstances("RUNNING"));
        result.put("approvedCount", dataCountInstances("APPROVED"));
        result.put("pendingCount", listTasks("todo", TenantContext.require().username()).size());
        result.put("todayActionCount", dataCountActionsToday());
        return result;
    }

    public List<Map<String, Object>> listDefinitions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> stored : dataListDefinitions()) {
            Map<String, Object> row = new LinkedHashMap<>(stored);
            ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(String.valueOf(stored.get("flowable_definition_id"))).singleResult();
            if (definition != null) {
                row.put("id", definition.getId());
                row.put("process_code", definition.getKey());
                row.put("process_name", definition.getName());
                row.put("version", definition.getVersion());
                row.put("status", definition.isSuspended() ? "SUSPENDED" : "PUBLISHED");
            }
            result.add(row);
        }
        for (ProcessDefinition definition : repositoryService.createProcessDefinitionQuery().latestVersion().orderByProcessDefinitionKey().asc().list()) {
            boolean known = result.stream().anyMatch(row -> definition.getKey().equals(String.valueOf(row.get("process_code"))));
            if (!known) {
                Map<String, Object> row = definitionMap(definition);
                row.put("category", "自定义流程");
                row.put("description", "由流程中心设计器创建的可执行流程");
                row.put("process_type", "BUSINESS");
                row.put("trigger_type", "MANUAL");
                result.add(row);
            }
        }
        return result;
    }

    public List<Map<String, Object>> listBindings() {
        return jdbc.queryForList("select business_function, process_code, updated_by, updated_at from bpm_process_binding where tenant_id=? order by business_function", dataTenantId());
    }

    public Map<String, Object> bindProcess(String businessFunction, Map<String, Object> payload, String actor) {
        String function = nullable(businessFunction);
        String code = text(payload.get("processCode"), payload.get("process_code"), null);
        if (function == null || code == null) throw new IllegalArgumentException("业务功能和流程编码不能为空");
        Map<String, Object> definition = dataFindDefinition(code);
        ProcessDefinition engineDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionKey(code).latestVersion().singleResult();
        if (definition == null && engineDefinition == null) throw new IllegalArgumentException("未找到流程定义：" + code);
        if (definition != null && !"PUBLISHED".equals(String.valueOf(definition.get("status")))) throw new IllegalArgumentException("只能绑定已发布的流程定义");
        if (definition == null && engineDefinition.isSuspended()) throw new IllegalArgumentException("只能绑定已发布的流程定义");
        int updated = jdbc.update("update bpm_process_binding set process_code=?, updated_by=?, updated_at=current_timestamp where tenant_id=? and business_function=?", code, actor, dataTenantId(), function.toLowerCase(Locale.ROOT));
        if (updated == 0) jdbc.update("insert into bpm_process_binding(tenant_id, business_function, process_code, updated_by) values(?,?,?,?)", dataTenantId(), function.toLowerCase(Locale.ROOT), code, actor);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("business_function", function.toLowerCase(Locale.ROOT));
        result.put("process_code", code);
        result.put("updated_by", actor);
        return result;
    }

    public Map<String, Object> createDefinition(Map<String, Object> payload, String actor) {
        String code = processCode(payload.get("processCode"), payload.get("code"));
        String name = text(payload.get("processName"), payload.get("name"), "未命名审批流程");
        String group = text(payload.get("candidateGroup"), null, "planner");
        String category = text(payload.get("category"), null, "通用审批");
        String description = nullable(payload.get("description"));
        Deployment deployment = repositoryService.createDeployment()
                .name(name + " · " + UUID.randomUUID().toString().substring(0, 8))
                .disableSchemaValidation()
                .addString(code + ".bpmn20.xml", simpleBpmn(code, name, group)).deploy();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(code).deploymentId(deployment.getId()).singleResult();
        if (definition == null) throw new IllegalArgumentException("流程定义部署失败");
        dataInsertDefinition(code, name, category, description, definition.getId(), deployment.getId(), definition.getVersion(), "PUBLISHED", actor);
        return definitionMap(definition);
    }

    public void toggleDefinition(String definitionId, boolean suspended) {
        if (dataFindDefinitionByFlowableId(definitionId) == null) throw new IllegalArgumentException("流程定义不存在");
        if (repositoryService.createProcessDefinitionQuery().processDefinitionId(definitionId).singleResult() == null) {
            throw new IllegalArgumentException("流程定义不存在");
        }
        if (suspended) repositoryService.suspendProcessDefinitionById(definitionId);
        else repositoryService.activateProcessDefinitionById(definitionId);
        dataUpdateDefinitionStatus(definitionId, suspended ? "SUSPENDED" : "PUBLISHED");
    }

    public List<Map<String, Object>> listForms() { return dataListForms(); }

    public void createForm(Map<String, Object> payload, String actor) {
        dataInsertForm(text(payload.get("formCode"), payload.get("code"), "FORM-" + System.currentTimeMillis()),
                text(payload.get("formName"), payload.get("name"), "未命名表单"),
                text(payload.get("businessType"), null, "COMMON"),
                String.valueOf(payload.getOrDefault("schemaJson", "{\"fields\":[]}")),
                text(payload.get("status"), null, "DRAFT"), actor);
    }

    public void updateForm(String code, Map<String, Object> payload, String actor) {
        dataUpdateForm(code, text(payload.get("formName"), payload.get("name"), "未命名表单"),
                text(payload.get("businessType"), null, "COMMON"),
                String.valueOf(payload.getOrDefault("schemaJson", "{\"fields\":[]}")),
                text(payload.get("status"), null, "DRAFT"), actor);
    }

    public Map<String, Object> startWorkOrderApproval(long id, String actor) {
        return startProcess(Map.of("processCode", "workOrderApproval", "businessType", "WORK_ORDER", "businessId", String.valueOf(id)), actor);
    }

    public Map<String, Object> startProcess(Map<String, Object> payload, String actor) {
        String code = text(payload.get("processCode"), null, "workOrderApproval");
        String type = text(payload.get("businessType"), null, "COMMON").toUpperCase(Locale.ROOT);
        String businessId = nullable(payload.get("businessId"));
        String title = text(payload.get("title"), null, type + " 审批申请");
        if ("WORK_ORDER".equals(type)) {
            if (businessId == null) throw new IllegalArgumentException("工单 ID 不能为空");
            Map<String, Object> order;
            try { order = dataFindWorkOrder(Long.parseLong(businessId)); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("工单 ID 必须是数字"); }
            if (order == null) throw new IllegalArgumentException("工单不存在或已删除");
            title = text(payload.get("title"), null, "工单 " + order.get("order_no") + " 发布审批");
        }
        if ("ERP_RECORD".equals(type)) {
            if (businessId == null) throw new IllegalArgumentException("ERP 单据 ID 不能为空");
            Map<String, Object> record;
            try { record = dataFindErpRecord(Long.parseLong(businessId)); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("ERP 单据 ID 必须是数字"); }
            if (record == null) throw new IllegalArgumentException("ERP 单据不存在或已删除");
            String recordStatus = String.valueOf(record.get("status"));
            if (!"REVIEW".equals(recordStatus) && !("PROCUREMENT".equals(String.valueOf(record.get("domain"))) && "DRAFT".equals(recordStatus))) {
                throw new IllegalArgumentException("只有采购草稿或待评审单据可以发起审批");
            }
            if (dataFindRunningErpInstance(businessId) != null) throw new IllegalArgumentException("该单据已有审批流程在处理中");
            title = text(payload.get("title"), null, "ERP 单据 " + record.get("record_no") + " 审批");
        }
        Map<String, Object> stored = dataFindDefinition(code);
        String definitionId = stored == null ? null : String.valueOf(stored.get("flowable_definition_id"));
        ProcessDefinition definition = definitionId == null
                ? repositoryService.createProcessDefinitionQuery().processDefinitionKey(code).latestVersion().singleResult()
                : repositoryService.createProcessDefinitionQuery().processDefinitionId(definitionId).singleResult();
        if (definition == null) throw new IllegalArgumentException("流程定义不可用：" + code);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("starter", actor); variables.put("businessType", type); variables.put("businessId", businessId); variables.put("title", title);
        Object supplied = payload.get("variables");
        if (supplied instanceof Map<?, ?> map) map.forEach((key, value) -> variables.put(String.valueOf(key), value));
        if ("ERP_RECORD".equals(type)) dataSubmitErpRecord(businessId);
        var instance = runtimeService.startProcessInstanceById(definition.getId(),
                text(payload.get("businessKey"), null, type + ":" + text(businessId, null, "NA") + ":" + System.currentTimeMillis()), variables);
        dataInsertInstance(instance.getId(), definition.getId(), code, type, businessId,
                instance.getBusinessKey(), title, actor);
        if ("WORK_ORDER".equals(type)) dataUpdateWorkOrderStatus(businessId, "PENDING_APPROVAL");
        return dataFindInstance(instance.getId());
    }

    public List<Map<String, Object>> listTasks(String scope, String actor) {
        if ("done".equalsIgnoreCase(scope)) return doneTasks(actor);
        String role = TenantContext.require().roleCode();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Task task : taskService.createTaskQuery().orderByTaskCreateTime().desc().list()) {
            if (dataFindInstance(task.getProcessInstanceId()) != null && canOperate(task, actor, role)) {
                result.add(taskMap(task, "TODO", null));
            }
        }
        return result;
    }

    public List<Map<String, Object>> listInstances(String status, String starter) {
        return dataListInstances(nullable(status), nullable(starter));
    }

    public List<Map<String, Object>> listInstancesByBusiness(String businessType, String businessId) {
        return jdbc.queryForList("select id, flowable_instance_id, flowable_definition_id, process_code, business_type, business_id, business_key, title, starter, status, started_at, ended_at from bpm_process_instance where tenant_id=? and business_type=? and business_id=? order by started_at desc", dataTenantId(), businessType, businessId);
    }

    public Map<String, Object> instanceDetail(String instanceId) {
        Map<String, Object> instance = dataFindInstance(instanceId);
        if (instance == null) throw new IllegalArgumentException("流程实例不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance", instance);
        result.put("tasks", taskService.createTaskQuery().processInstanceId(instanceId).list().stream().map(task -> taskMap(task, "TODO", null)).toList());
        List<Map<String, Object>> history = new ArrayList<>();
        for (HistoricTaskInstance task : historyService.createHistoricTaskInstanceQuery().processInstanceId(instanceId).orderByHistoricTaskInstanceStartTime().asc().list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", task.getId()); row.put("name", task.getName()); row.put("assignee", task.getAssignee());
            row.put("startTime", task.getCreateTime()); row.put("endTime", task.getEndTime()); history.add(row);
        }
        result.put("history", history); result.put("actions", dataListActions(instanceId));
        return result;
    }

    public Map<String, Object> completeTask(String taskId, Map<String, Object> payload, String actor) {
        Task task = getTask(taskId); assertCanOperate(task, actor);
        boolean approved = payload.get("approved") == null || Boolean.parseBoolean(String.valueOf(payload.get("approved")));
        String comment = nullable(payload.get("comment"));
        if (comment != null) taskService.addComment(taskId, task.getProcessInstanceId(), comment);
        taskService.complete(taskId, Map.of("approved", approved, "lastActor", actor));
        dataInsertAction(taskId, task.getProcessInstanceId(), "COMPLETE", actor, comment);
        boolean finished = runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult() == null;
        String status = finished ? (approved ? "APPROVED" : "REJECTED") : "RUNNING";
        dataUpdateInstanceStatus(task.getProcessInstanceId(), status);
        Map<String, Object> instance = dataFindInstance(task.getProcessInstanceId());
        if (finished && instance != null && "WORK_ORDER".equals(String.valueOf(instance.get("business_type")))) {
            dataUpdateWorkOrderStatus(String.valueOf(instance.get("business_id")), approved ? "PLANNED" : "REJECTED");
        }
        if (finished && instance != null && "ERP_RECORD".equals(String.valueOf(instance.get("business_type")))) {
            Map<String, Object> record = dataFindErpRecord(Long.parseLong(String.valueOf(instance.get("business_id"))));
            if (record != null) dataUpdateErpRecordStatus(String.valueOf(instance.get("business_id")), approved ? erpApprovedStatus(String.valueOf(record.get("domain"))) : "REVIEW");
        }
        return Map.of("taskId", taskId, "status", status, "approved", approved);
    }

    public void claimTask(String taskId, String actor) {
        Task task = getTask(taskId); assertCanOperate(task, actor);
        if (task.getAssignee() != null && !actor.equals(task.getAssignee())) throw new IllegalArgumentException("任务已被其他人签收");
        taskService.setAssignee(taskId, actor); dataInsertAction(taskId, task.getProcessInstanceId(), "CLAIM", actor, null);
    }

    public void unclaimTask(String taskId, String actor) {
        Task task = getTask(taskId);
        if (!actor.equals(task.getAssignee())) throw new IllegalArgumentException("只能退回自己签收的任务");
        taskService.setAssignee(taskId, null); dataInsertAction(taskId, task.getProcessInstanceId(), "UNCLAIM", actor, null);
    }

    public void cancelInstance(String instanceId, String actor) {
        Map<String, Object> instance = dataFindInstance(instanceId);
        if (instance == null) throw new IllegalArgumentException("流程实例不存在");
        if (!actor.equals(String.valueOf(instance.get("starter"))) && !"admin".equals(TenantContext.require().roleCode())) {
            throw new IllegalArgumentException("只有发起人或管理员可以撤回流程");
        }
        if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult() != null) {
            runtimeService.deleteProcessInstance(instanceId, "业务撤回：" + actor);
        }
        dataUpdateInstanceStatus(instanceId, "CANCELLED");
        if ("WORK_ORDER".equals(String.valueOf(instance.get("business_type")))) dataUpdateWorkOrderStatus(String.valueOf(instance.get("business_id")), "PLANNED");
    }

    public void ensureTenantProcessDefinition() {
        ensureDefinition("workOrderApproval", "工单发布审批", "planner", "制造审批", "工单发布审批流程", simpleBpmn("workOrderApproval", "工单发布审批", "planner"));
        ensureDefinition("purchaseRequisitionApproval", "采购申请审批", "planner", "采购审批", "采购申请 AI 创建与人工确认后的审批流程", purchaseBpmn());
    }

    private void ensureDefinition(String code, String name, String group, String category, String description, String bpmn) {
        if (dataFindDefinition(code) != null) return;
        Deployment deployment = repositoryService.createDeployment()
                .name(name + " · " + TenantContext.require().tenantCode())
                .disableSchemaValidation()
                .addString(code + ".bpmn20.xml", bpmn).deploy();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
        if (definition == null) throw new IllegalStateException("默认审批流程部署失败：" + code);
        dataInsertDefinition(code, definition.getName(), category, description,
                definition.getId(), deployment.getId(), definition.getVersion(), "PUBLISHED", "system");
    }

    private List<Map<String, Object>> doneTasks(String actor) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> action : dataListCompletedActions(actor)) {
            HistoricTaskInstance task = historyService.createHistoricTaskInstanceQuery().taskId(String.valueOf(action.get("flowable_task_id"))).singleResult();
            if (task != null) rows.add(taskMap(task, "DONE", action));
        }
        return rows;
    }

    private Task getTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || dataFindInstance(task.getProcessInstanceId()) == null) throw new IllegalArgumentException("待办任务不存在或已处理");
        return task;
    }

    private void assertCanOperate(Task task, String actor) {
        if (!canOperate(task, actor, TenantContext.require().roleCode())) throw new IllegalArgumentException("当前用户没有处理该任务的权限");
    }

    private boolean canOperate(Task task, String actor, String role) {
        if ("admin".equals(role) || actor.equals(task.getAssignee())) return true;
        if (task.getAssignee() != null) return false;
        return candidateGroups(task.getId()).contains(role);
    }

    private List<String> candidateGroups(String taskId) {
        return taskService.getIdentityLinksForTask(taskId).stream().map(IdentityLink::getGroupId).filter(Objects::nonNull).toList();
    }

    private Map<String, Object> taskMap(Task task, String status, Map<String, Object> action) {
        Map<String, Object> instance = dataFindInstance(task.getProcessInstanceId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskId", task.getId()); row.put("instanceId", task.getProcessInstanceId());
        row.put("processCode", instance == null ? null : instance.get("process_code"));
        row.put("businessType", instance == null ? null : instance.get("business_type"));
        row.put("businessId", instance == null ? null : instance.get("business_id"));
        row.put("title", instance == null ? task.getName() : instance.get("title")); row.put("name", task.getName());
        row.put("assignee", task.getAssignee()); row.put("candidateGroup", candidateGroups(task.getId()).stream().findFirst().orElse(null));
        row.put("startTime", task.getCreateTime()); row.put("endTime", task.getDueDate()); row.put("status", status);
        if (action != null) { row.put("comment", action.get("comment_text")); row.put("actionTime", action.get("created_at")); }
        return row;
    }

    private Map<String, Object> taskMap(HistoricTaskInstance task, String status, Map<String, Object> action) {
        Map<String, Object> instance = dataFindInstance(task.getProcessInstanceId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskId", task.getId()); row.put("instanceId", task.getProcessInstanceId());
        row.put("processCode", instance == null ? null : instance.get("process_code"));
        row.put("businessType", instance == null ? null : instance.get("business_type"));
        row.put("businessId", instance == null ? null : instance.get("business_id"));
        row.put("title", instance == null ? task.getName() : instance.get("title")); row.put("name", task.getName());
        row.put("assignee", task.getAssignee()); row.put("startTime", task.getCreateTime()); row.put("endTime", task.getEndTime()); row.put("status", status);
        if (action != null) { row.put("comment", action.get("comment_text")); row.put("actionTime", action.get("created_at")); }
        return row;
    }

    private static Map<String, Object> definitionMap(ProcessDefinition definition) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", definition.getId()); row.put("process_code", definition.getKey()); row.put("process_name", definition.getName());
        row.put("version", definition.getVersion()); row.put("deployment_id", definition.getDeploymentId()); row.put("status", definition.isSuspended() ? "SUSPENDED" : "PUBLISHED");
        return row;
    }

    private static String processCode(Object primary, Object secondary) {
        String raw = text(primary, secondary, "approval_" + System.currentTimeMillis()).replaceAll("[^A-Za-z0-9_]", "_");
        return raw.isBlank() || Character.isDigit(raw.charAt(0)) ? "process_" + raw : raw;
    }

    private static String simpleBpmn(String code, String name, String group) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"Polaris.BPM\"><process id=\"" + xml(code) + "\" name=\"" + xml(name) + "\" isExecutable=\"true\"><startEvent id=\"start\"/><sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"approval\"/><userTask id=\"approval\" name=\"" + xml(name) + "\" flowable:candidateGroups=\"" + xml(group) + "\"/><sequenceFlow id=\"f2\" sourceRef=\"approval\" targetRef=\"end\"/><endEvent id=\"end\"/></process></definitions>";
    }

    private static String purchaseBpmn() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" targetNamespace=\"Polaris.BPM\">"
                + "<process id=\"purchaseRequisitionApproval\" name=\"采购申请审批\" isExecutable=\"true\">"
                + "<startEvent id=\"start\"/><sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"dept\"/>"
                + "<userTask id=\"dept\" name=\"部门负责人审批\" flowable:candidateGroups=\"planner\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"dept\" targetRef=\"deptGateway\"/>"
                + "<exclusiveGateway id=\"deptGateway\" default=\"deptReject\"/>"
                + "<sequenceFlow id=\"deptApprove\" sourceRef=\"deptGateway\" targetRef=\"procurement\"><conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${approved == true}]]></conditionExpression></sequenceFlow>"
                + "<sequenceFlow id=\"deptReject\" sourceRef=\"deptGateway\" targetRef=\"rejectEnd\"/>"
                + "<userTask id=\"procurement\" name=\"采购经理审批\" flowable:candidateGroups=\"planner\"/>"
                + "<sequenceFlow id=\"f3\" sourceRef=\"procurement\" targetRef=\"procurementGateway\"/>"
                + "<exclusiveGateway id=\"procurementGateway\" default=\"procurementReject\"/>"
                + "<sequenceFlow id=\"procurementApprove\" sourceRef=\"procurementGateway\" targetRef=\"riskGateway\"><conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${approved == true}]]></conditionExpression></sequenceFlow>"
                + "<sequenceFlow id=\"procurementReject\" sourceRef=\"procurementGateway\" targetRef=\"rejectEnd\"/>"
                + "<exclusiveGateway id=\"riskGateway\" default=\"normalEnd\"/>"
                + "<sequenceFlow id=\"managementRisk\" sourceRef=\"riskGateway\" targetRef=\"management\"><conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${purchaseManagementRisk == true}]]></conditionExpression></sequenceFlow>"
                + "<sequenceFlow id=\"financeRisk\" sourceRef=\"riskGateway\" targetRef=\"finance\"><conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[${purchaseHighRisk == true}]]></conditionExpression></sequenceFlow>"
                + "<sequenceFlow id=\"normalEnd\" sourceRef=\"riskGateway\" targetRef=\"end\"/>"
                + "<userTask id=\"finance\" name=\"财务审批\" flowable:candidateGroups=\"admin\"/><sequenceFlow id=\"f4\" sourceRef=\"finance\" targetRef=\"end\"/>"
                + "<userTask id=\"management\" name=\"管理者审批\" flowable:candidateGroups=\"admin\"/><sequenceFlow id=\"f5\" sourceRef=\"management\" targetRef=\"end\"/>"
                + "<endEvent id=\"end\"/><endEvent id=\"rejectEnd\"/></process></definitions>";
    }

    private static String xml(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private static String nullable(Object value) { if (value == null) return null; String text = String.valueOf(value); return text.isBlank() ? null : text; }
    private static String text(Object primary, Object secondary, String fallback) { String value = nullable(primary); if (value != null) return value; value = nullable(secondary); return value == null ? fallback : value; }

    @PostConstruct
    public void dataEnsureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS bpm_process_definition (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, " +
                "process_code VARCHAR(100) NOT NULL, process_name VARCHAR(150) NOT NULL, " +
                "category VARCHAR(60) NOT NULL DEFAULT '通用审批', description VARCHAR(255), " +
                "flowable_definition_id VARCHAR(150) NOT NULL, flowable_deployment_id VARCHAR(150), " +
                "version INT NOT NULL DEFAULT 1, status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED', " +
                "created_by VARCHAR(64) NOT NULL DEFAULT 'admin', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS bpm_process_binding (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, " +
                "business_function VARCHAR(80) NOT NULL, process_code VARCHAR(100) NOT NULL, " +
                "updated_by VARCHAR(64) NOT NULL DEFAULT 'admin', updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, business_function))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS bpm_form_definition (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, form_code VARCHAR(100) NOT NULL, " +
                "form_name VARCHAR(150) NOT NULL, business_type VARCHAR(60) NOT NULL DEFAULT 'COMMON', " +
                "schema_json TEXT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED', " +
                "updated_by VARCHAR(64) NOT NULL DEFAULT 'admin', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, form_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS bpm_process_instance (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, flowable_instance_id VARCHAR(150) NOT NULL UNIQUE, " +
                "flowable_definition_id VARCHAR(150) NOT NULL, process_code VARCHAR(100) NOT NULL, business_type VARCHAR(60) NOT NULL, " +
                "business_id VARCHAR(100), business_key VARCHAR(180), title VARCHAR(200) NOT NULL, starter VARCHAR(64) NOT NULL, " +
                "status VARCHAR(30) NOT NULL DEFAULT 'RUNNING', started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, ended_at TIMESTAMP NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS bpm_task_action (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, flowable_task_id VARCHAR(150) NOT NULL, " +
                "flowable_instance_id VARCHAR(150) NOT NULL, action_code VARCHAR(30) NOT NULL, actor VARCHAR(64) NOT NULL, " +
                "comment_text VARCHAR(500), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        // MySQL does not support CREATE INDEX IF NOT EXISTS. Duplicate-index
        // errors are intentionally ignored so this remains safe on restarts.
        dataEnsureIndex("idx_bpm_def_tenant_code", "bpm_process_definition(tenant_id, process_code)");
        dataEnsureIndex("idx_bpm_def_status", "bpm_process_definition(tenant_id, status)");
        dataEnsureIndex("idx_bpm_instance_status", "bpm_process_instance(tenant_id, status)");
        dataEnsureIndex("idx_bpm_instance_starter", "bpm_process_instance(tenant_id, starter)");
        dataEnsureIndex("idx_bpm_instance_business", "bpm_process_instance(tenant_id, business_type, business_id)");
        dataEnsureIndex("idx_bpm_action_instance", "bpm_task_action(tenant_id, flowable_instance_id, created_at)");
    }

    private void dataEnsureIndex(String name, String target) {
        try { jdbc.execute("CREATE INDEX " + name + " ON " + target); }
        catch (DataAccessException ignored) { /* already exists or is supplied by an older schema */ }
    }

    public List<Map<String, Object>> dataListDefinitions() {
        return jdbc.queryForList("select id, process_code, process_name, category, description, flowable_definition_id, flowable_deployment_id, version, status, created_by, created_at, updated_at from bpm_process_definition where tenant_id=? order by process_code, version desc", dataTenantId());
    }

    public Map<String, Object> dataFindDefinition(String processCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, process_code, process_name, category, description, flowable_definition_id, flowable_deployment_id, version, status, created_by, created_at, updated_at from bpm_process_definition where tenant_id=? and process_code=? order by version desc, id desc limit 1", dataTenantId(), processCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> dataFindDefinitionByFlowableId(String definitionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, process_code, process_name, category, description, flowable_definition_id, flowable_deployment_id, version, status, created_by, created_at, updated_at from bpm_process_definition where tenant_id=? and flowable_definition_id=? limit 1", dataTenantId(), definitionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void dataInsertDefinition(String code, String name, String category, String description, String definitionId, String deploymentId, int version, String status, String actor) {
        jdbc.update("insert into bpm_process_definition(tenant_id, process_code, process_name, category, description, flowable_definition_id, flowable_deployment_id, version, status, created_by) values(?,?,?,?,?,?,?,?,?,?)", dataTenantId(), code, name, category, description, definitionId, deploymentId, version, status, actor);
    }

    public void dataUpdateDefinitionStatus(String definitionId, String status) {
        jdbc.update("update bpm_process_definition set status=?, updated_at=current_timestamp where tenant_id=? and flowable_definition_id=?", status, dataTenantId(), definitionId);
    }

    public List<Map<String, Object>> dataListForms() {
        return jdbc.queryForList("select id, form_code, form_name, business_type, schema_json, status, updated_by, created_at, updated_at from bpm_form_definition where tenant_id=? order by id desc", dataTenantId());
    }

    public void dataInsertForm(String code, String name, String type, String schema, String status, String actor) {
        jdbc.update("insert into bpm_form_definition(tenant_id, form_code, form_name, business_type, schema_json, status, updated_by) values(?,?,?,?,?,?,?)", dataTenantId(), code, name, type, schema, status, actor);
    }

    public void dataUpdateForm(String code, String name, String type, String schema, String status, String actor) {
        if (jdbc.update("update bpm_form_definition set form_name=?, business_type=?, schema_json=?, status=?, updated_by=?, updated_at=current_timestamp where tenant_id=? and form_code=?", name, type, schema, status, actor, dataTenantId(), code) == 0) {
            throw new IllegalArgumentException("表单不存在：" + code);
        }
    }

    public void dataInsertInstance(String instanceId, String definitionId, String code, String type, String businessId, String businessKey, String title, String starter) {
        jdbc.update("insert into bpm_process_instance(tenant_id, flowable_instance_id, flowable_definition_id, process_code, business_type, business_id, business_key, title, starter, status) values(?,?,?,?,?,?,?,?,?,?)", dataTenantId(), instanceId, definitionId, code, type, businessId, businessKey, title, starter, "RUNNING");
    }

    public Map<String, Object> dataFindInstance(String instanceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, flowable_instance_id, flowable_definition_id, process_code, business_type, business_id, business_key, title, starter, status, started_at, ended_at from bpm_process_instance where tenant_id=? and flowable_instance_id=? limit 1", dataTenantId(), instanceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> dataListInstances(String status, String starter) {
        StringBuilder sql = new StringBuilder("select id, flowable_instance_id, flowable_definition_id, process_code, business_type, business_id, title, starter, status, started_at, ended_at from bpm_process_instance where tenant_id=?");
        if (status != null && !status.isBlank()) sql.append(" and status=?");
        if (starter != null && !starter.isBlank()) sql.append(" and starter=?");
        sql.append(" order by started_at desc");
        if (status != null && !status.isBlank() && starter != null && !starter.isBlank()) return jdbc.queryForList(sql.toString(), dataTenantId(), status, starter);
        if (status != null && !status.isBlank()) return jdbc.queryForList(sql.toString(), dataTenantId(), status);
        if (starter != null && !starter.isBlank()) return jdbc.queryForList(sql.toString(), dataTenantId(), starter);
        return jdbc.queryForList(sql.toString(), dataTenantId());
    }

    public void dataUpdateInstanceStatus(String instanceId, String status) {
        jdbc.update("update bpm_process_instance set status=?, ended_at=case when ? in ('APPROVED','REJECTED','CANCELLED') then current_timestamp else ended_at end where tenant_id=? and flowable_instance_id=?", status, status, dataTenantId(), instanceId);
    }

    public void dataInsertAction(String taskId, String instanceId, String action, String actor, String comment) {
        jdbc.update("insert into bpm_task_action(tenant_id, flowable_task_id, flowable_instance_id, action_code, actor, comment_text) values(?,?,?,?,?,?)", dataTenantId(), taskId, instanceId, action, actor, comment);
    }

    public List<Map<String, Object>> dataListActions(String instanceId) {
        return jdbc.queryForList("select id, flowable_task_id, flowable_instance_id, action_code, actor, comment_text, created_at from bpm_task_action where tenant_id=? and flowable_instance_id=? order by created_at asc", dataTenantId(), instanceId);
    }

    public List<Map<String, Object>> dataListCompletedActions(String actor) {
        return jdbc.queryForList("select id, flowable_task_id, flowable_instance_id, action_code, actor, comment_text, created_at from bpm_task_action where tenant_id=? and actor=? and action_code='COMPLETE' order by created_at desc", dataTenantId(), actor);
    }

    public long dataCountInstances(String status) {
        Long count = jdbc.queryForObject("select count(*) from bpm_process_instance where tenant_id=? and status=?", Long.class, dataTenantId(), status);
        return count == null ? 0 : count;
    }

    public long dataCountActionsToday() {
        Long count = jdbc.queryForObject("select count(*) from bpm_task_action where tenant_id=? and action_code='COMPLETE' and date(created_at)=current_date", Long.class, dataTenantId());
        return count == null ? 0 : count;
    }

    public Map<String, Object> dataFindWorkOrder(long id) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("select id, order_no, product_code, product_name, plan_qty, status from work_order where tenant_id=? and id=? and deleted=0 limit 1", dataTenantId(), id);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("工单表不可用，请确认 BPM 与工单系统使用同一个数据库");
        }
    }

    public void dataUpdateWorkOrderStatus(String id, String status) {
        try {
            jdbc.update("update work_order set status=?, updated_at=current_timestamp where tenant_id=? and id=? and deleted=0", status, dataTenantId(), id);
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("工单状态回写失败，请检查工单数据库连接");
        }
    }

    public Map<String, Object> dataFindErpRecord(long id) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("select id, domain, record_type, record_no, record_name, status from erp_business_record where tenant_id=? and id=? limit 1", dataTenantId(), id);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("ERP 业务单据表不可用，请确认审批与 ERP 使用同一个数据库");
        }
    }

    public Map<String, Object> dataFindRunningErpInstance(String businessId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, flowable_instance_id, status from bpm_process_instance where tenant_id=? and business_type='ERP_RECORD' and business_id=? and status='RUNNING' order by started_at desc limit 1", dataTenantId(), businessId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void dataSubmitErpRecord(String id) {
        int updated = jdbc.update("update erp_business_record set status='REVIEW', updated_at=current_timestamp where tenant_id=? and id=? and status='DRAFT'", dataTenantId(), id);
        if (updated == 0) {
            Map<String, Object> record;
            try { record = dataFindErpRecord(Long.parseLong(id)); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("ERP 单据 ID 必须是数字"); }
            if (record == null) throw new IllegalArgumentException("ERP 单据不存在或已删除");
            if (!"REVIEW".equals(String.valueOf(record.get("status")))) throw new IllegalArgumentException("采购申请状态已变化，请刷新后重试");
        }
    }

    public void dataUpdateErpRecordStatus(String id, String status) {
        try {
            jdbc.update("update erp_business_record set status=?, updated_at=current_timestamp where tenant_id=? and id=? and status='REVIEW'", status, dataTenantId(), id);
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("ERP 单据状态回写失败，请检查审批数据库连接");
        }
    }

    private static String erpApprovedStatus(String domain) {
        return switch (domain == null ? "" : domain.toUpperCase(Locale.ROOT)) {
            case "MASTER" -> "ACTIVE";
            case "FINANCE" -> "INVOICED";
            case "PROCUREMENT" -> "APPROVED";
            default -> "CONFIRMED";
        };
    }

    private long dataTenantId() { return TenantContext.require().tenantId(); }
}
