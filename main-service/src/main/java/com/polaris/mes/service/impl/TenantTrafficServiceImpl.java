package com.polaris.mes.service.impl;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.TenantTrafficService;
import com.polaris.mes.service.TrafficLimitExceededException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class TenantTrafficServiceImpl implements TenantTrafficService {
    private static final long DEFAULT_STARTER_QUOTA_BYTES = 1024L * 1024 * 1024;
    private final JdbcTemplate jdbc;

    public TenantTrafficServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isBillableTenant(long tenantId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_tenant where id=? and tenant_type='CUSTOMER' and status=1",
                Integer.class, tenantId);
        if (count != null && count > 0) ensureAccount(tenantId);
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireAvailable(long tenantId) {
        Map<String, Object> row = one("select quota_bytes-used_bytes as remaining_bytes from tenant_traffic_account where tenant_id=?", tenantId);
        if (row == null || longNumber(row.get("remaining_bytes"), 0) <= 0) {
            throw new TrafficLimitExceededException();
        }
    }

    /**
     * Atomically charges the real request/response body bytes. The balance
     * predicate prevents concurrent requests from driving a tenant negative.
     */
    @Override
    public Map<String, Object> consume(long tenantId, long bytes) {
        if (bytes <= 0) return snapshot(tenantId);
        ensureAccount(tenantId);
        int updated = jdbc.update(
                "update tenant_traffic_account set used_bytes=used_bytes+?, updated_at=current_timestamp " +
                        "where tenant_id=? and quota_bytes-used_bytes>=?",
                bytes, tenantId, bytes);
        if (updated == 0) {
            Map<String, Object> row = one("select quota_bytes, used_bytes, quota_bytes-used_bytes as remaining_bytes from tenant_traffic_account where tenant_id=?", tenantId);
            long remaining = row == null ? 0 : longNumber(row.get("remaining_bytes"), 0);
            if (remaining <= 0) throw new TrafficLimitExceededException();
            throw new TrafficLimitExceededException("本次请求需要 " + formatBytes(bytes) + "，当前仅剩 " + formatBytes(remaining) + "，请联系总管理员分配流量");
        }
        Map<String, Object> current = snapshot(tenantId);
        jdbc.update("insert into tenant_traffic_ledger(tenant_id, action_type, change_bytes, consumed_bytes, quota_after_bytes, used_after_bytes, description, created_by) values(?,?,?,?,?,?,?,?)",
                tenantId, "CONSUME", 0, bytes, longNumber(current.get("quota_bytes"), 0), longNumber(current.get("used_bytes"), 0),
                "接口实际请求/响应流量消耗", actor());
        return current;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(long tenantId) {
        Map<String, Object> row = one("select tenant_id, quota_bytes, used_bytes, quota_bytes-used_bytes as remaining_bytes, warning_threshold_percent, updated_at from tenant_traffic_account where tenant_id=?", tenantId);
        if (row == null) {
            return trafficRow(tenantId, 0, 0, 10, null);
        }
        return trafficRow(tenantId, longNumber(row.get("quota_bytes"), 0), longNumber(row.get("used_bytes"), 0),
                intNumber(row.get("warning_threshold_percent"), 10), row.get("updated_at"));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> traffic(long tenantId) {
        Map<String, Object> result = new LinkedHashMap<>(snapshot(tenantId));
        result.put("ledger", normalizedRows(jdbc.queryForList(
                "select id, action_type, change_bytes, consumed_bytes, quota_after_bytes, used_after_bytes, description, created_by, created_at " +
                        "from tenant_traffic_ledger where tenant_id=? order by created_at desc, id desc limit 200", tenantId)));
        return result;
    }

    @Override
    public Map<String, Object> allocate(long tenantId, Map<String, Object> payload) {
        customerTenant(tenantId);
        ensureAccount(tenantId);
        long quotaBytes = nonNegativeLong(payload.get("quotaBytes"), "流量配额");
        int warning = intNumber(payload.get("warningThresholdPercent"), 10);
        if (warning < 0 || warning > 100) throw new IllegalArgumentException("预警阈值必须在 0-100 之间");
        Map<String, Object> before = snapshot(tenantId);
        long usedBytes = longNumber(before.get("used_bytes"), 0);
        if (quotaBytes < usedBytes) {
            throw new IllegalArgumentException("新的流量配额不能小于已使用流量 " + formatBytes(usedBytes));
        }
        long oldQuota = longNumber(before.get("quota_bytes"), 0);
        jdbc.update("update tenant_traffic_account set quota_bytes=?, warning_threshold_percent=?, updated_at=current_timestamp where tenant_id=?",
                quotaBytes, warning, tenantId);
        jdbc.update("insert into tenant_traffic_ledger(tenant_id, action_type, change_bytes, consumed_bytes, quota_after_bytes, used_after_bytes, description, created_by) values(?,?,?,?,?,?,?,?)",
                tenantId, "ALLOCATE", quotaBytes - oldQuota, 0, quotaBytes, usedBytes,
                stringOr(payload.get("reason"), "平台管理员调整流量配额"), actor());
        return traffic(tenantId);
    }

    private void ensureAccount(long tenantId) {
        try {
            jdbc.update("insert into tenant_traffic_account(tenant_id, quota_bytes) values(?, ?)", tenantId, DEFAULT_STARTER_QUOTA_BYTES);
        } catch (DataAccessException ignored) {
            // Idempotent account creation; the unique key handles concurrent initialization.
        }
    }

    private Map<String, Object> customerTenant(long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, tenant_code, tenant_name, tenant_type, status from sys_tenant where id=? and tenant_type='CUSTOMER'", tenantId);
        if (rows.isEmpty()) throw new IllegalArgumentException("客户租户不存在");
        return rows.get(0);
    }

    private Map<String, Object> trafficRow(long tenantId, long quota, long used, int warning, Object updatedAt) {
        Map<String, Object> result = new LinkedHashMap<>();
        long remaining = Math.max(0, quota - used);
        result.put("tenant_id", tenantId);
        result.put("quota_bytes", quota);
        result.put("used_bytes", used);
        result.put("remaining_bytes", remaining);
        result.put("warning_threshold_percent", warning);
        result.put("used_percent", quota <= 0 ? 100 : Math.min(100, (used * 100.0) / quota));
        result.put("exhausted", remaining <= 0);
        result.put("low_balance", quota > 0 && remaining * 100.0 / quota <= warning);
        result.put("updated_at", updatedAt);
        return result;
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : normalizedRow(rows.get(0));
    }

    private String actor() {
        TenantContext.Identity identity = TenantContext.current();
        return identity == null ? "system" : identity.username();
    }

    private static long nonNegativeLong(Object value, String label) {
        long result;
        try { result = value == null ? -1 : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(label + "必须是整数" ); }
        if (result < 0) throw new IllegalArgumentException(label + "不能小于 0");
        return result;
    }

    private static long longNumber(Object value, long fallback) {
        try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static int intNumber(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static String stringOr(Object value, String fallback) {
        String text = value == null ? null : String.valueOf(value).trim();
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.2f GB", bytes / (1024d * 1024 * 1024));
        if (bytes >= 1024L * 1024) return String.format(Locale.ROOT, "%.2f MB", bytes / (1024d * 1024));
        if (bytes >= 1024L) return String.format(Locale.ROOT, "%.2f KB", bytes / 1024d);
        return bytes + " B";
    }

    private static Map<String, Object> normalizedRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(String.valueOf(key).toLowerCase(Locale.ROOT), value));
        return normalized;
    }

    private static List<Map<String, Object>> normalizedRows(List<Map<String, Object>> rows) {
        return rows.stream().map(TenantTrafficServiceImpl::normalizedRow).toList();
    }
}
