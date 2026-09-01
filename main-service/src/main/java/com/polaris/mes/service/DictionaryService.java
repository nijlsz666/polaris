package com.polaris.mes.service;

import com.polaris.mes.common.RequestContext;
import com.polaris.mes.common.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class DictionaryService {
    private final JdbcTemplate jdbc;

    public DictionaryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String type, String locale) {
        return list(type, locale, false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String type, String locale, boolean includeDisabled) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("字典类型不能为空");
        String requestedLocale = locale == null || locale.isBlank() ? "zh-CN" : locale;
        String sql = "select id, dict_type, parent_id, dict_code, dict_label, dict_value, locale, sort_no, status, metadata_json from sys_dictionary where tenant_id=? and dict_type=? "
                + (includeDisabled ? "" : "and status=1 ") + "and locale in (?, '*') order by sort_no, id";
        return jdbc.queryForList(sql, tenantId(), type.trim().toUpperCase(Locale.ROOT), requestedLocale)
                .stream().map(this::normalize).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> types(String locale) {
        return jdbc.queryForList("select distinct dict_type from sys_dictionary where tenant_id=? and status=1 and locale in (?, '*') order by dict_type", tenantId(), locale == null || locale.isBlank() ? "zh-CN" : locale)
                .stream().map(row -> Map.of("type", value(row, "dict_type"))).toList();
    }

    public Map<String, Object> save(Map<String, Object> payload) {
        RequestContext.requireRole("admin");
        String type = required(payload, "type", "字典类型").toUpperCase(Locale.ROOT);
        String code = required(payload, "code", "字典编码");
        String label = required(payload, "label", "字典标签");
        String value = String.valueOf(payload.getOrDefault("value", code));
        String locale = String.valueOf(payload.getOrDefault("locale", "zh-CN"));
        int sortNo = payload.get("sortNo") instanceof Number number ? number.intValue() : 99;
        long parentId = payload.get("parentId") instanceof Number number ? number.longValue() : 0L;
        try {
            jdbc.update("insert into sys_dictionary(tenant_id,dict_type,parent_id,dict_code,dict_label,dict_value,locale,sort_no,status,metadata_json) values(?,?,?,?,?,?,?,?,1,?)",
                    tenantId(), type, parentId, code, label, value, locale, sortNo, payload.get("metadataJson"));
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("字典项已存在：" + type + " / " + code + " / " + locale); }
        return jdbc.queryForMap("select id, dict_type, dict_code, dict_label, dict_value, locale, sort_no, status, metadata_json from sys_dictionary where tenant_id=? and dict_type=? and dict_code=? and locale=?", tenantId(), type, code, locale);
    }

    public Map<String, Object> update(long id, Map<String, Object> payload) {
        RequestContext.requireRole("admin");
        String type = required(payload, "type", "字典类型").toUpperCase(Locale.ROOT);
        String code = required(payload, "code", "字典编码");
        String label = required(payload, "label", "字典标签");
        String value = String.valueOf(payload.getOrDefault("value", code));
        String locale = String.valueOf(payload.getOrDefault("locale", "zh-CN"));
        int sortNo = payload.get("sortNo") instanceof Number number ? number.intValue() : 99;
        long parentId = payload.get("parentId") instanceof Number number ? number.longValue() : 0L;
        int status = payload.get("status") instanceof Number number ? number.intValue() : 1;
        try {
            int updated = jdbc.update("update sys_dictionary set dict_type=?, parent_id=?, dict_code=?, dict_label=?, dict_value=?, locale=?, sort_no=?, status=?, metadata_json=?, updated_at=current_timestamp where tenant_id=? and id=?",
                    type, parentId, code, label, value, locale, sortNo, status, payload.get("metadataJson"), tenantId(), id);
            if (updated == 0) throw new IllegalArgumentException("字典项不存在");
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("字典项已存在：" + type + " / " + code + " / " + locale); }
        return jdbc.queryForMap("select id, dict_type, parent_id, dict_code, dict_label, dict_value, locale, sort_no, status, metadata_json from sys_dictionary where tenant_id=? and id=?", tenantId(), id);
    }

    public void delete(long id) {
        RequestContext.requireRole("admin");
        Number children = jdbc.queryForObject("select count(*) from sys_dictionary where tenant_id=? and parent_id=? and status=1", Number.class, tenantId(), id);
        if (children != null && children.intValue() > 0) throw new IllegalArgumentException("请先删除或移动子字典项");
        if (jdbc.update("delete from sys_dictionary where tenant_id=? and id=?", tenantId(), id) == 0) throw new IllegalArgumentException("字典项不存在");
    }

    private Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value(row, "id"));
        result.put("type", value(row, "dict_type"));
        result.put("parentId", value(row, "parent_id"));
        result.put("code", value(row, "dict_code"));
        result.put("label", value(row, "dict_label"));
        result.put("value", value(row, "dict_value"));
        result.put("locale", value(row, "locale"));
        result.put("sortNo", value(row, "sort_no"));
        result.put("status", value(row, "status"));
        result.put("metadata", value(row, "metadata_json"));
        return result;
    }

    private static Object value(Map<String, Object> row, String key) { return row.containsKey(key) ? row.get(key) : row.get(key.toUpperCase(Locale.ROOT)); }

    private long tenantId() { return TenantContext.require().tenantId(); }
    private static String required(Map<String, Object> payload, String key, String label) { String value = payload.get(key) == null ? "" : String.valueOf(payload.get(key)).trim(); if (value.isBlank()) throw new IllegalArgumentException(label + "不能为空"); return value; }
}
