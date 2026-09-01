package com.polaris.bpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.bpm.mapper.BpmFormDefinitionMapper;
import com.polaris.bpm.model.entity.BpmFormDefinition;
import com.polaris.bpm.service.BpmFormDefinitionApplicationService;
import com.polaris.bpm.service.support.WorkflowPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BpmFormDefinitionApplicationServiceImpl implements BpmFormDefinitionApplicationService {
    private final BpmFormDefinitionMapper formMapper;
    private final ObjectMapper objectMapper;

    public BpmFormDefinitionApplicationServiceImpl(BpmFormDefinitionMapper formMapper, ObjectMapper objectMapper) {
        this.formMapper = formMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForms() {
        return formMapper.selectList(new LambdaQueryWrapper<BpmFormDefinition>()
                        .orderByDesc(BpmFormDefinition::getId))
                .stream().map(this::formRow).toList();
    }

    @Override
    @Transactional
    public void createForm(Map<String, Object> source, String actor) {
        Map<String, Object> payload = source == null ? Map.of() : source;
        String schema = String.valueOf(payload.getOrDefault("schemaJson", "{\"fields\":[]}"));
        validateSchema(schema);
        BpmFormDefinition form = new BpmFormDefinition();
        form.setFormCode(WorkflowPayload.text(payload.get("formCode"), payload.get("code"), "FORM-" + System.currentTimeMillis()));
        form.setFormName(WorkflowPayload.text(payload.get("formName"), payload.get("name"), "未命名表单"));
        form.setBusinessType(WorkflowPayload.text(payload.get("businessType"), null, "COMMON").toUpperCase());
        form.setSchemaJson(schema);
        form.setStatus(WorkflowPayload.text(payload.get("status"), null, "DRAFT").toUpperCase());
        form.setUpdatedBy(actor);
        formMapper.insert(form);
    }

    @Override
    @Transactional
    public void updateForm(String code, Map<String, Object> source, String actor) {
        Map<String, Object> payload = source == null ? Map.of() : source;
        String schema = String.valueOf(payload.getOrDefault("schemaJson", "{\"fields\":[]}"));
        validateSchema(schema);
        BpmFormDefinition form = formMapper.selectOne(new LambdaQueryWrapper<BpmFormDefinition>()
                .eq(BpmFormDefinition::getFormCode, code));
        if (form == null) {
            throw new IllegalArgumentException("表单不存在：" + code);
        }
        form.setFormName(WorkflowPayload.text(payload.get("formName"), payload.get("name"), "未命名表单"));
        form.setBusinessType(WorkflowPayload.text(payload.get("businessType"), null, "COMMON").toUpperCase());
        form.setSchemaJson(schema);
        form.setStatus(WorkflowPayload.text(payload.get("status"), null, "DRAFT").toUpperCase());
        form.setUpdatedBy(actor);
        form.setUpdatedAt(LocalDateTime.now());
        formMapper.updateById(form);
    }

    private void validateSchema(String schema) {
        try {
            JsonNode root = objectMapper.readTree(schema);
            if (!root.isObject() || !root.has("fields") || !root.get("fields").isArray()) {
                throw new IllegalArgumentException("表单 schema 必须包含 fields 数组");
            }
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("表单 schema 不是合法 JSON", ex);
        }
    }

    private Map<String, Object> formRow(BpmFormDefinition entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("form_code", entity.getFormCode());
        row.put("form_name", entity.getFormName());
        row.put("business_type", entity.getBusinessType());
        row.put("schema_json", entity.getSchemaJson());
        row.put("status", entity.getStatus());
        row.put("updated_by", entity.getUpdatedBy());
        row.put("created_at", entity.getCreatedAt());
        row.put("updated_at", entity.getUpdatedAt());
        return row;
    }
}
