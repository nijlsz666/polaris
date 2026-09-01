package com.polaris.bpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.bpm.mapper.BpmProcessDesignMapper;
import com.polaris.bpm.model.entity.BpmProcessDesign;
import com.polaris.bpm.service.ProcessDefinitionApplicationService;
import com.polaris.bpm.service.support.BpmnDefinitionCompiler;
import com.polaris.bpm.service.support.ProcessDesignValidator;
import com.polaris.bpm.service.support.WorkflowPayload;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProcessDefinitionApplicationServiceImpl implements ProcessDefinitionApplicationService {
    private final RepositoryService repositoryService;
    private final BpmProcessDesignMapper designMapper;
    private final ObjectMapper objectMapper;
    private final ProcessDesignValidator validator;
    private final BpmnDefinitionCompiler compiler;

    public ProcessDefinitionApplicationServiceImpl(RepositoryService repositoryService,
                                                   BpmProcessDesignMapper designMapper,
                                                   ObjectMapper objectMapper,
                                                   ProcessDesignValidator validator,
                                                   BpmnDefinitionCompiler compiler) {
        this.repositoryService = repositoryService;
        this.designMapper = designMapper;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.compiler = compiler;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDefinitions() {
        Map<String, Map<String, Object>> byCode = new LinkedHashMap<>();
        for (ProcessDefinition definition : repositoryService.createProcessDefinitionQuery()
                .latestVersion().orderByProcessDefinitionKey().asc().list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.getId());
            row.put("process_code", definition.getKey());
            row.put("process_name", definition.getName());
            row.put("version", definition.getVersion());
            row.put("deployment_id", definition.getDeploymentId());
            row.put("status", definition.isSuspended() ? "SUSPENDED" : "PUBLISHED");
            byCode.put(definition.getKey(), row);
        }
        for (Map<String, Object> design : listDesignRows()) {
            String code = String.valueOf(design.get("process_code"));
            Map<String, Object> row = byCode.computeIfAbsent(code, ignored -> new LinkedHashMap<>());
            row.putIfAbsent("id", "draft:" + code);
            row.put("process_code", code);
            row.put("process_name", design.get("process_name"));
            row.put("description", design.get("description"));
            row.put("category", design.get("category"));
            row.put("process_type", design.getOrDefault("process_type", "APPROVAL"));
            row.put("trigger_type", design.getOrDefault("trigger_type", "MANUAL"));
            row.put("version", design.getOrDefault("version", row.getOrDefault("version", 1)));
            row.put("updated_by", design.get("updated_by"));
            row.put("updated_at", design.get("updated_at"));
            if ("DRAFT".equals(String.valueOf(design.get("status")))) {
                row.put("status", "DRAFT");
            }
        }
        return new ArrayList<>(byCode.values());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDesigns() {
        return listDesignRows();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDesign(String code) {
        Map<String, Object> row = findDesignRow(code);
        if (row == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>(row);
        String json = String.valueOf(row.getOrDefault("design_json", "{}"));
        try {
            result.put("design", objectMapper.readValue(json, Map.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("流程草稿数据损坏：" + code, ex);
        }
        result.remove("design_json");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> saveDesign(String code, Map<String, Object> source, String actor, String status) {
        Map<String, Object> payload = source == null ? Map.of() : source;
        String processCode = WorkflowPayload.processCode(code, payload.get("processCode"));
        String name = WorkflowPayload.text(payload.get("processName"), payload.get("name"), "未命名流程");
        String description = WorkflowPayload.nullable(payload.get("description"));
        String category = WorkflowPayload.nullable(payload.get("category"));
        String processType = WorkflowPayload.normalizedType(payload.get("processType"), payload.get("process"), "processType", "APPROVAL");
        String triggerType = WorkflowPayload.normalizedType(payload.get("triggerType"), payload.get("process"), "triggerType", "MANUAL");
        int version = WorkflowPayload.number(payload.get("version"), 1);
        String designJson;
        try {
            designJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("流程定义数据无法保存", ex);
        }
        upsertDesign(processCode, name, description, category, processType, triggerType,
                version, designJson, status, actor);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("process_code", processCode);
        result.put("process_name", name);
        result.put("version", version);
        result.put("status", status);
        result.put("process_type", processType);
        result.put("trigger_type", triggerType);
        result.put("updated_by", actor);
        result.put("design", payload);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> publishDesign(String code, Map<String, Object> source, String actor) {
        Map<String, Object> payload = source == null ? Map.of() : source;
        String processCode = WorkflowPayload.processCode(code, payload.get("processCode"));
        String name = WorkflowPayload.text(payload.get("processName"), payload.get("name"), "未命名流程");
        List<String> errors = validator.validate(payload, true);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("流程不能发布：" + String.join("；", errors));
        }
        int version = nextPublishedVersion(processCode, WorkflowPayload.number(payload.get("version"), 1));
        Map<String, Object> versionedPayload = new LinkedHashMap<>(payload);
        versionedPayload.put("version", version);
        Map<String, Object> result = new LinkedHashMap<>(saveDesign(processCode, versionedPayload, actor, "PUBLISHED"));
        Deployment deployment = repositoryService.createDeployment()
                .name(name + " · 设计器发布")
                .addString(processCode + ".bpmn20.xml", compiler.compile(processCode, name, versionedPayload))
                .deploy();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId()).singleResult();
        if (definition == null) {
            throw new IllegalArgumentException("流程定义部署失败");
        }
        result.put("definition_id", definition.getId());
        result.put("engine_version", definition.getVersion());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> validateDesignPayload(Map<String, Object> payload) {
        List<String> errors = validator.validate(payload == null ? Map.of() : payload, false);
        return Map.of("valid", errors.isEmpty(), "errors", errors);
    }

    @Override
    @Transactional
    public Map<String, Object> createDefinition(Map<String, Object> source) {
        Map<String, Object> payload = source == null ? Map.of() : source;
        String code = WorkflowPayload.processCode(payload.get("processCode"), payload.get("code"));
        String name = WorkflowPayload.text(payload.get("processName"), payload.get("name"), "未命名审批流程");
        String group = WorkflowPayload.text(payload.get("candidateGroup"), null, "planner");
        Deployment deployment = repositoryService.createDeployment()
                .name(name + " · " + java.util.UUID.randomUUID().toString().substring(0, 8))
                .addString(code + ".bpmn20.xml", compiler.simple(code, name, group))
                .deploy();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(code).deploymentId(deployment.getId()).singleResult();
        if (definition == null) {
            throw new IllegalArgumentException("流程定义部署失败");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", definition.getId());
        result.put("process_code", definition.getKey());
        result.put("process_name", definition.getName());
        result.put("version", definition.getVersion());
        result.put("status", "PUBLISHED");
        return result;
    }

    @Override
    @Transactional
    public void toggleDefinition(String id, boolean suspended) {
        if (repositoryService.createProcessDefinitionQuery().processDefinitionId(id).singleResult() == null) {
            throw new IllegalArgumentException("流程定义不存在");
        }
        if (suspended) {
            repositoryService.suspendProcessDefinitionById(id);
        } else {
            repositoryService.activateProcessDefinitionById(id);
        }
    }

    private List<Map<String, Object>> listDesignRows() {
        return designMapper.selectList(new LambdaQueryWrapper<BpmProcessDesign>()
                        .orderByDesc(BpmProcessDesign::getUpdatedAt))
                .stream().map(this::designRowWithoutJson).toList();
    }

    private Map<String, Object> findDesignRow(String code) {
        BpmProcessDesign design = designMapper.selectOne(new LambdaQueryWrapper<BpmProcessDesign>()
                .eq(BpmProcessDesign::getProcessCode, code));
        return design == null ? null : designRow(design);
    }

    private void upsertDesign(String code, String name, String description, String category,
                              String processType, String triggerType, int version,
                              String designJson, String status, String actor) {
        BpmProcessDesign design = designMapper.selectOne(new LambdaQueryWrapper<BpmProcessDesign>()
                .eq(BpmProcessDesign::getProcessCode, code));
        if (design == null) {
            design = new BpmProcessDesign();
            design.setProcessCode(code);
            design.setProcessName(name);
            design.setDescription(description);
            design.setCategory(category);
            design.setProcessType(processType);
            design.setTriggerType(triggerType);
            design.setVersion(version);
            design.setDesignJson(designJson);
            design.setStatus(status);
            design.setUpdatedBy(actor);
            designMapper.insert(design);
            return;
        }
        design.setProcessName(name);
        design.setDescription(description);
        design.setCategory(category);
        design.setProcessType(processType);
        design.setTriggerType(triggerType);
        design.setVersion(version);
        design.setDesignJson(designJson);
        design.setStatus(status);
        design.setUpdatedBy(actor);
        design.setUpdatedAt(LocalDateTime.now());
        designMapper.updateById(design);
    }

    private int nextPublishedVersion(String code, int requested) {
        ProcessDefinition existingEngine = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(code).latestVersion().singleResult();
        int engineNext = existingEngine == null ? 1 : existingEngine.getVersion() + 1;
        Map<String, Object> existing = findDesignRow(code);
        int storedNext = existing == null ? 1 : WorkflowPayload.number(existing.get("version"), 0)
                + ("PUBLISHED".equals(String.valueOf(existing.get("status"))) ? 1 : 0);
        return Math.max(1, Math.max(requested, Math.max(engineNext, storedNext)));
    }

    private Map<String, Object> designRowWithoutJson(BpmProcessDesign entity) {
        Map<String, Object> row = designRow(entity);
        row.remove("design_json");
        return row;
    }

    private Map<String, Object> designRow(BpmProcessDesign entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("process_code", entity.getProcessCode());
        row.put("process_name", entity.getProcessName());
        row.put("description", entity.getDescription());
        row.put("category", entity.getCategory());
        row.put("process_type", entity.getProcessType());
        row.put("trigger_type", entity.getTriggerType());
        row.put("version", entity.getVersion());
        row.put("design_json", entity.getDesignJson());
        row.put("status", entity.getStatus());
        row.put("updated_by", entity.getUpdatedBy());
        row.put("created_at", entity.getCreatedAt());
        row.put("updated_at", entity.getUpdatedAt());
        return row;
    }
}
