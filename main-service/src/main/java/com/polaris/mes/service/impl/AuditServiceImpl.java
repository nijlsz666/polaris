package com.polaris.mes.service.impl;

import com.polaris.mes.service.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.polaris.mes.common.TenantContext;

import java.util.LinkedHashMap;
import java.util.List;

import java.util.Map;

@Service
public class AuditServiceImpl implements AuditService {
    private final JdbcTemplate jdbc;

    public AuditServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String actor, String actionCode, String resourceType, String requestUri) {
        jdbc.update("insert into audit_log(tenant_id, actor, action_code, resource_type, request_uri) values(?,?,?,?,?)",
                tenantId(), actor, actionCode, resourceType, requestUri);
    }

    @Override
    public Map<String, Object> page(String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 100));
        String term = keyword == null ? "" : keyword.trim();
        String where = term.isBlank() ? "" : " and (actor like ? or action_code like ? or request_uri like ?)";
        String like = "%" + term + "%";
        Object[] pageArgs = term.isBlank()
                ? new Object[]{tenantId(), safeSize, (safePage - 1) * safeSize}
                : new Object[]{tenantId(), like, like, like, safeSize, (safePage - 1) * safeSize};
        Long total = term.isBlank()
                ? jdbc.queryForObject("select count(*) from audit_log where tenant_id=?", Long.class, tenantId())
                : jdbc.queryForObject("select count(*) from audit_log where tenant_id=?" + where, Long.class, tenantId(), like, like, like);
        String sql = "select id, actor, action_code, resource_type, resource_id, request_uri, created_at " +
                "from audit_log where tenant_id=?" + where + " order by created_at desc, id desc limit ? offset ?";
        List<Map<String, Object>> items = jdbc.queryForList(sql, pageArgs);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0 : total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private long tenantId() {
        return TenantContext.require().tenantId();
    }
}
