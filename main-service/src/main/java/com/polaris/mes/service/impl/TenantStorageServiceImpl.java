package com.polaris.mes.service.impl;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.TenantStorageService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class TenantStorageServiceImpl implements TenantStorageService {
    private static final long BYTES_PER_GB = 1024L * 1024 * 1024;
    private static final long DEFAULT_STARTER_QUOTA_BYTES = 10L * BYTES_PER_GB;
    private final JdbcTemplate jdbc;

    public TenantStorageServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(long tenantId) {
        Map<String, Object> row = one("select tenant_id, quota_bytes, used_bytes, quota_bytes-used_bytes as remaining_bytes, " +
                "warning_threshold_percent, unit_price_per_gb_month, updated_at from tenant_storage_account where tenant_id=?", tenantId);
        if (row == null) return storageRow(tenantId, 0, 0, 10, BigDecimal.ZERO, null);
        return storageRow(tenantId, longNumber(row.get("quota_bytes"), 0), longNumber(row.get("used_bytes"), 0),
                intNumber(row.get("warning_threshold_percent"), 10), decimal(row.get("unit_price_per_gb_month"), BigDecimal.ZERO), row.get("updated_at"));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> storage(long tenantId) {
        Map<String, Object> result = new LinkedHashMap<>(snapshot(tenantId));
        result.put("ledger", normalizedRows(jdbc.queryForList(
                "select id, action_type, change_bytes, consumed_bytes, quota_after_bytes, used_after_bytes, description, created_by, created_at " +
                        "from tenant_storage_ledger where tenant_id=? order by created_at desc, id desc limit 200", tenantId)));
        return result;
    }

    @Override
    public Map<String, Object> allocate(long tenantId, Map<String, Object> payload) {
        customerTenant(tenantId);
        ensureAccount(tenantId);
        long quotaBytes = nonNegativeLong(payload.get("quotaBytes"), "存储配额");
        int warning = intNumber(payload.get("warningThresholdPercent"), 10);
        if (warning < 0 || warning > 100) throw new IllegalArgumentException("预警阈值必须在 0-100 之间");
        BigDecimal unitPrice = nonNegativeDecimal(payload.get("unitPricePerGbMonth"), "存储单价");
        Map<String, Object> before = snapshot(tenantId);
        long usedBytes = longNumber(before.get("used_bytes"), 0);
        if (quotaBytes < usedBytes) {
            throw new IllegalArgumentException("新的存储配额不能小于已使用存储 " + formatBytes(usedBytes));
        }
        long oldQuota = longNumber(before.get("quota_bytes"), 0);
        jdbc.update("update tenant_storage_account set quota_bytes=?, warning_threshold_percent=?, unit_price_per_gb_month=?, updated_at=current_timestamp where tenant_id=?",
                quotaBytes, warning, unitPrice, tenantId);
        jdbc.update("insert into tenant_storage_ledger(tenant_id, action_type, change_bytes, consumed_bytes, quota_after_bytes, used_after_bytes, description, created_by) values(?,?,?,?,?,?,?,?)",
                tenantId, "ALLOCATE", quotaBytes - oldQuota, 0, quotaBytes, usedBytes,
                stringOr(payload.get("reason"), "平台管理员调整存储配额与单价"), actor());
        return storage(tenantId);
    }

    /** Synchronizes the actual occupied bytes from a file/object storage adapter. */
    @Override
    public Map<String, Object> recordUsage(long tenantId, Map<String, Object> payload) {
        customerTenant(tenantId);
        ensureAccount(tenantId);
        long usedBytes = nonNegativeLong(payload.get("usedBytes"), "存储已用量");
        Map<String, Object> before = snapshot(tenantId);
        long quotaBytes = longNumber(before.get("quota_bytes"), 0);
        if (usedBytes > quotaBytes) {
            throw new IllegalArgumentException("存储已用量不能超过配额 " + formatBytes(quotaBytes));
        }
        long oldUsedBytes = longNumber(before.get("used_bytes"), 0);
        long delta = usedBytes - oldUsedBytes;
        if (delta == 0) return storage(tenantId);
        jdbc.update("update tenant_storage_account set used_bytes=?, updated_at=current_timestamp where tenant_id=?", usedBytes, tenantId);
        jdbc.update("insert into tenant_storage_ledger(tenant_id, action_type, change_bytes, consumed_bytes, quota_after_bytes, used_after_bytes, description, created_by) values(?,?,?,?,?,?,?,?)",
                tenantId, delta > 0 ? "CONSUME" : "RELEASE", delta, Math.max(delta, 0), quotaBytes, usedBytes,
                stringOr(payload.get("reason"), delta > 0 ? "同步实际存储占用" : "同步存储释放"), actor());
        return storage(tenantId);
    }

    private void ensureAccount(long tenantId) {
        try {
            jdbc.update("insert into tenant_storage_account(tenant_id, quota_bytes) values(?, ?)", tenantId, DEFAULT_STARTER_QUOTA_BYTES);
        } catch (DataAccessException ignored) {
            // Idempotent account creation; the unique key handles concurrent initialization.
        }
    }

    private Map<String, Object> customerTenant(long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, tenant_code, tenant_name, tenant_type, status from sys_tenant where id=? and tenant_type='CUSTOMER'", tenantId);
        if (rows.isEmpty()) throw new IllegalArgumentException("客户租户不存在");
        return rows.get(0);
    }

    private Map<String, Object> storageRow(long tenantId, long quota, long used, int warning, BigDecimal unitPrice, Object updatedAt) {
        Map<String, Object> result = new LinkedHashMap<>();
        long remaining = Math.max(0, quota - used);
        BigDecimal monthlyCharge = BigDecimal.valueOf(used)
                .divide(BigDecimal.valueOf(BYTES_PER_GB), 8, RoundingMode.HALF_UP)
                .multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        result.put("tenant_id", tenantId);
        result.put("quota_bytes", quota);
        result.put("used_bytes", used);
        result.put("remaining_bytes", remaining);
        result.put("warning_threshold_percent", warning);
        result.put("unit_price_per_gb_month", unitPrice);
        result.put("estimated_monthly_charge", monthlyCharge);
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
        catch (NumberFormatException ex) { throw new IllegalArgumentException(label + "必须是整数"); }
        if (result < 0) throw new IllegalArgumentException(label + "不能小于 0");
        return result;
    }

    private static BigDecimal nonNegativeDecimal(Object value, String label) {
        BigDecimal result = decimal(value, null);
        if (result == null) throw new IllegalArgumentException(label + "必须是数字");
        if (result.signum() < 0) throw new IllegalArgumentException(label + "不能小于 0");
        return result.setScale(4, RoundingMode.HALF_UP);
    }

    private static long longNumber(Object value, long fallback) {
        try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static int intNumber(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static BigDecimal decimal(Object value, BigDecimal fallback) {
        try { return value == null ? fallback : new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static String stringOr(Object value, String fallback) {
        String text = value == null ? null : String.valueOf(value).trim();
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String formatBytes(long bytes) {
        if (bytes >= BYTES_PER_GB) return String.format(Locale.ROOT, "%.2f GB", bytes / (double) BYTES_PER_GB);
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
        return rows.stream().map(TenantStorageServiceImpl::normalizedRow).toList();
    }
}
