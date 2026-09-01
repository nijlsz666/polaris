package com.polaris.mes.service.impl;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.security.PasswordHasher;
import com.polaris.mes.service.PlatformAdminService;
import com.polaris.mes.service.PlatformService;
import com.polaris.mes.service.TenantStorageService;
import com.polaris.mes.service.TenantTrafficService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class PlatformAdminServiceImpl implements PlatformAdminService {
    private final JdbcTemplate jdbc;
    private final PlatformService platform;
    private final PasswordHasher passwordHasher;
    private final TenantTrafficService traffic;
    private final TenantStorageService storage;

    public PlatformAdminServiceImpl(JdbcTemplate jdbc, PlatformService platform, PasswordHasher passwordHasher, TenantTrafficService traffic, TenantStorageService storage) {
        this.jdbc = jdbc;
        this.platform = platform;
        this.passwordHasher = passwordHasher;
        this.traffic = traffic;
        this.storage = storage;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerTenants", scalar("select count(*) from sys_tenant where tenant_type='CUSTOMER'"));
        data.put("activeTenants", scalar("select count(*) from sys_tenant where tenant_type='CUSTOMER' and status=1"));
        data.put("enabledFeatures", scalar("select count(*) from tenant_feature_grant where status=1"));
        data.put("openTickets", scalar("select count(*) from platform_service_ticket where status in ('OPEN','IN_PROGRESS')"));
        data.put("billingTotal", scalar("select coalesce(sum(total_paid),0) from tenant_billing_account"));
        data.put("pointsIssued", scalar("select coalesce(sum(total_earned),0) from tenant_points_account"));
        data.put("trafficQuotaBytes", scalar("select coalesce(sum(quota_bytes),0) from tenant_traffic_account a join sys_tenant t on t.id=a.tenant_id and t.tenant_type='CUSTOMER'"));
        data.put("trafficUsedBytes", scalar("select coalesce(sum(used_bytes),0) from tenant_traffic_account a join sys_tenant t on t.id=a.tenant_id and t.tenant_type='CUSTOMER'"));
        data.put("storageQuotaBytes", scalar("select coalesce(sum(quota_bytes),0) from tenant_storage_account a join sys_tenant t on t.id=a.tenant_id and t.tenant_type='CUSTOMER'"));
        data.put("storageUsedBytes", scalar("select coalesce(sum(used_bytes),0) from tenant_storage_account a join sys_tenant t on t.id=a.tenant_id and t.tenant_type='CUSTOMER'"));
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTenants(String keyword) {
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        String where = like == null ? "" : " and (t.tenant_code like ? or t.tenant_name like ? or coalesce(t.contact_name,'') like ?)";
        Object[] args = like == null ? new Object[]{} : new Object[]{like, like, like};
        String sql = "select t.id, t.tenant_code, t.tenant_name, t.tenant_type, t.plan_code, t.contact_name, t.contact_email, " +
                "t.trial_ends_at, t.max_users, t.status, t.created_at, " +
                "(select count(*) from sys_user u where u.tenant_id=t.id) as user_count, " +
                "(select count(*) from tenant_feature_grant g where g.tenant_id=t.id and g.status=1) as feature_count, " +
                "coalesce(b.balance,0) as balance, coalesce(p.balance,0) as points, " +
                "coalesce(ta.quota_bytes,0) as traffic_quota_bytes, coalesce(ta.used_bytes,0) as traffic_used_bytes, " +
                "greatest(coalesce(ta.quota_bytes,0)-coalesce(ta.used_bytes,0),0) as traffic_remaining_bytes, " +
                "coalesce(sa.quota_bytes,0) as storage_quota_bytes, coalesce(sa.used_bytes,0) as storage_used_bytes, " +
                "greatest(coalesce(sa.quota_bytes,0)-coalesce(sa.used_bytes,0),0) as storage_remaining_bytes " +
                "from sys_tenant t left join tenant_billing_account b on b.tenant_id=t.id " +
                "left join tenant_points_account p on p.tenant_id=t.id left join tenant_traffic_account ta on ta.tenant_id=t.id " +
                "left join tenant_storage_account sa on sa.tenant_id=t.id " +
                "where t.tenant_type='CUSTOMER'" + where +
                " order by t.created_at desc, t.id desc";
        return normalizedRows(jdbc.queryForList(sql, args));
    }

    @Override
    @Transactional
    public Map<String, Object> createTenant(Map<String, Object> payload) {
        String password = required(payload, "password", "管理员密码");
        if (password.length() < 8) throw new IllegalArgumentException("管理员密码至少需要 8 位");
        Map<String, Object> created = platform.registerTenant(payload, passwordHasher.hash(password));
        return tenant(((Number) created.get("tenantId")).longValue());
    }

    @Override
    public Map<String, Object> updateTenant(long tenantId, Map<String, Object> payload) {
        customerTenant(tenantId);
        String name = required(payload, "tenantName", "企业名称");
        String email = string(payload.get("contactEmail"));
        if (email != null && !email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("联系邮箱格式不正确");
        }
        int updated = jdbc.update("update sys_tenant set tenant_name=?, plan_code=?, contact_name=?, contact_email=?, max_users=?, status=?, updated_at=current_timestamp where id=? and tenant_type='CUSTOMER'",
                name, stringOr(payload.get("planCode"), "STARTER"), string(payload.get("contactName")), email,
                Math.max(1, number(payload.get("maxUsers"), 10)), number(payload.get("status"), 1), tenantId);
        if (updated == 0) throw new IllegalArgumentException("租户不存在");
        return tenant(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFeatures() {
        return normalizedRows(jdbc.queryForList("select id, feature_code, feature_name, category, description, sort_no, status from platform_feature where status=1 order by sort_no, id"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTenantFeatures(long tenantId) {
        customerTenant(tenantId);
        return normalizedRows(jdbc.queryForList("select f.feature_code, f.feature_name, f.category, f.description, " +
                "case when g.id is null then 0 else g.status end as granted_status, " +
                "g.quota_json, g.expires_at, g.granted_by, g.updated_at " +
                "from platform_feature f left join tenant_feature_grant g on g.feature_code=f.feature_code and g.tenant_id=? " +
                "where f.status=1 order by f.sort_no, f.id", tenantId));
    }

    @Override
    public List<Map<String, Object>> updateTenantFeatures(long tenantId, List<Map<String, Object>> grants) {
        customerTenant(tenantId);
        jdbc.update("delete from tenant_feature_grant where tenant_id=?", tenantId);
        if (grants != null) for (Map<String, Object> grant : grants) {
            String code = required(grant, "featureCode", "功能编码").toUpperCase(Locale.ROOT);
            if (jdbc.queryForObject("select count(*) from platform_feature where feature_code=? and status=1", Integer.class, code) == 0) {
                throw new IllegalArgumentException("功能不存在：" + code);
            }
            jdbc.update("insert into tenant_feature_grant(tenant_id, feature_code, status, quota_json, expires_at, granted_by) values(?,?,?,?,?,?)",
                    tenantId, code, number(grant.get("status"), 1), stringOr(grant.get("quotaJson"), "{}"),
                    string(grant.get("expiresAt")), actor());
        }
        return listTenantFeatures(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> billing(long tenantId) {
        customerTenant(tenantId);
        ensureAccounts(tenantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("account", normalizedRow(jdbc.queryForMap("select tenant_id, currency_code, balance, total_paid, total_consumed, updated_at from tenant_billing_account where tenant_id=?", tenantId)));
        result.put("records", normalizedRows(jdbc.queryForList("select id, record_type, amount, status, description, period_start, period_end, created_by, created_at from tenant_billing_record where tenant_id=? order by created_at desc, id desc", tenantId)));
        return result;
    }

    @Override
    public Map<String, Object> createBillingRecord(Map<String, Object> payload) {
        long tenantId = longNumber(payload.get("tenantId"), 0);
        customerTenant(tenantId);
        BigDecimal amount = decimal(payload.get("amount"), BigDecimal.ZERO);
        if (amount.signum() <= 0) throw new IllegalArgumentException("计费金额必须大于 0");
        String type = stringOr(payload.get("recordType"), "CHARGE").toUpperCase(Locale.ROOT);
        if (!List.of("PAYMENT", "CHARGE", "STORAGE", "REFUND", "ADJUSTMENT").contains(type)) throw new IllegalArgumentException("计费类型不正确");
        ensureAccounts(tenantId);
        boolean charge = type.equals("CHARGE") || type.equals("STORAGE");
        BigDecimal delta = charge ? amount.negate() : amount;
        BigDecimal current = jdbc.queryForObject("select balance from tenant_billing_account where tenant_id=?", BigDecimal.class, tenantId);
        if (current.add(delta).signum() < 0) throw new IllegalArgumentException("账户余额不足，不能记账");
        jdbc.update("insert into tenant_billing_record(tenant_id, record_type, amount, status, description, period_start, period_end, created_by) values(?,?,?,?,?,?,?,?)",
                tenantId, type, amount, stringOr(payload.get("status"), "COMPLETED"), required(payload, "description", "计费说明"),
                string(payload.get("periodStart")), string(payload.get("periodEnd")), actor());
        jdbc.update("update tenant_billing_account set balance=balance+?, total_paid=total_paid+?, total_consumed=total_consumed+?, updated_at=current_timestamp where tenant_id=?",
                delta, type.equals("PAYMENT") || type.equals("REFUND") ? amount : BigDecimal.ZERO,
                charge ? amount : BigDecimal.ZERO, tenantId);
        return billing(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> points(long tenantId) {
        customerTenant(tenantId);
        ensureAccounts(tenantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("account", normalizedRow(jdbc.queryForMap("select tenant_id, balance, total_earned, total_spent, updated_at from tenant_points_account where tenant_id=?", tenantId)));
        result.put("ledger", normalizedRows(jdbc.queryForList("select id, change_amount, balance_after, reason, reference_type, reference_id, created_by, created_at from tenant_points_ledger where tenant_id=? order by created_at desc, id desc", tenantId)));
        return result;
    }

    @Override
    public Map<String, Object> adjustPoints(Map<String, Object> payload) {
        long tenantId = longNumber(payload.get("tenantId"), 0);
        customerTenant(tenantId);
        long amount = longNumber(payload.get("amount"), 0);
        if (amount == 0) throw new IllegalArgumentException("积分变动必须不为 0");
        String reason = required(payload, "reason", "积分变动原因");
        ensureAccounts(tenantId);
        int updated = jdbc.update("update tenant_points_account set balance=balance+?, total_earned=total_earned+case when ? > 0 then ? else 0 end, total_spent=total_spent+case when ? < 0 then -? else 0 end, updated_at=current_timestamp where tenant_id=? and balance+? >= 0",
                amount, amount, amount, amount, amount, tenantId, amount);
        if (updated == 0) throw new IllegalArgumentException("积分账户不存在或余额不足");
        long balance = ((Number) jdbc.queryForObject("select balance from tenant_points_account where tenant_id=?", Object.class, tenantId)).longValue();
        jdbc.update("insert into tenant_points_ledger(tenant_id, change_amount, balance_after, reason, reference_type, reference_id, created_by) values(?,?,?,?,?,?,?)",
                tenantId, amount, balance, reason, string(payload.get("referenceType")), string(payload.get("referenceId")), actor());
        return points(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> traffic(long tenantId) {
        customerTenant(tenantId);
        return traffic.traffic(tenantId);
    }

    @Override
    public Map<String, Object> allocateTraffic(Map<String, Object> payload) {
        long tenantId = longNumber(payload.get("tenantId"), 0);
        customerTenant(tenantId);
        return traffic.allocate(tenantId, payload);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> storage(long tenantId) {
        customerTenant(tenantId);
        return storage.storage(tenantId);
    }

    @Override
    public Map<String, Object> allocateStorage(Map<String, Object> payload) {
        long tenantId = longNumber(payload.get("tenantId"), 0);
        customerTenant(tenantId);
        return storage.allocate(tenantId, payload);
    }

    @Override
    public Map<String, Object> recordStorageUsage(Map<String, Object> payload) {
        long tenantId = longNumber(payload.get("tenantId"), 0);
        customerTenant(tenantId);
        return storage.recordUsage(tenantId, payload);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTickets(String status) {
        String normalized = status == null || status.isBlank() ? null : status.toUpperCase(Locale.ROOT);
        String sql = "select s.id, s.tenant_id, t.tenant_code, t.tenant_name, s.category, s.subject, s.status, s.priority, s.created_by, s.assignee, s.created_at, s.updated_at, " +
                "(select count(*) from platform_service_message m where m.ticket_id=s.id) as message_count from platform_service_ticket s join sys_tenant t on t.id=s.tenant_id" +
                (normalized == null ? "" : " where s.status=?") + " order by s.updated_at desc, s.id desc";
        return normalizedRows(normalized == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, normalized));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> ticketMessages(long ticketId) {
        if (jdbc.queryForObject("select count(*) from platform_service_ticket where id=?", Integer.class, ticketId) == 0) {
            throw new IllegalArgumentException("服务工单不存在");
        }
        return normalizedRows(jdbc.queryForList("select id, ticket_id, sender_type, sender_name, content, created_at from platform_service_message where ticket_id=? order by created_at, id", ticketId));
    }

    @Override
    public Map<String, Object> createTicket(Map<String, Object> payload) {
        long tenantId = longNumber(payload.get("tenantId"), 0);
        customerTenant(tenantId);
        String subject = required(payload, "subject", "问题主题");
        String content = required(payload, "content", "问题内容");
        jdbc.update("insert into platform_service_ticket(tenant_id, category, subject, status, priority, created_by, assignee) values(?,?,?,?,?,?,?)",
                tenantId, stringOr(payload.get("category"), "QUESTION").toUpperCase(Locale.ROOT), subject, "OPEN",
                stringOr(payload.get("priority"), "NORMAL").toUpperCase(Locale.ROOT), actor(), string(payload.get("assignee")));
        long ticketId = ((Number) jdbc.queryForObject("select id from platform_service_ticket where tenant_id=? and subject=? order by id desc limit 1", Object.class, tenantId, subject)).longValue();
        jdbc.update("insert into platform_service_message(ticket_id, sender_type, sender_name, content) values(?,?,?,?)", ticketId, "PLATFORM", actor(), content);
        return ticket(ticketId);
    }

    @Override
    public Map<String, Object> replyTicket(long ticketId, Map<String, Object> payload) {
        ticket(ticketId);
        String content = required(payload, "content", "回复内容");
        String status = stringOr(payload.get("status"), "WAITING_CUSTOMER").toUpperCase(Locale.ROOT);
        jdbc.update("insert into platform_service_message(ticket_id, sender_type, sender_name, content) values(?,?,?,?)", ticketId, "PLATFORM", actor(), content);
        jdbc.update("update platform_service_ticket set status=?, assignee=?, updated_at=current_timestamp where id=?", status, actor(), ticketId);
        return ticket(ticketId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCourses() {
        return normalizedRows(jdbc.queryForList("select c.id, c.course_code, c.course_name, c.category, c.instructor, c.schedule_at, c.capacity, c.status, c.description, c.created_by, c.created_at, " +
                "(select count(*) from platform_training_enrollment e where e.course_id=c.id) as enrollment_count from platform_training_course c order by c.schedule_at desc, c.id desc"));
    }

    @Override
    public Map<String, Object> saveCourse(Map<String, Object> payload, Long id) {
        String code = required(payload, "courseCode", "课程编码").toUpperCase(Locale.ROOT);
        String name = required(payload, "courseName", "课程名称");
        if (id == null) {
            try {
                jdbc.update("insert into platform_training_course(course_code, course_name, category, instructor, schedule_at, capacity, status, description, created_by) values(?,?,?,?,?,?,?,?,?)",
                        code, name, stringOr(payload.get("category"), "PRODUCT"), string(payload.get("instructor")), string(payload.get("scheduleAt")),
                        Math.max(0, number(payload.get("capacity"), 0)), stringOr(payload.get("status"), "DRAFT"), string(payload.get("description")), actor());
                id = ((Number) jdbc.queryForObject("select id from platform_training_course where course_code=?", Object.class, code)).longValue();
            } catch (DataAccessException ex) {
                throw new IllegalArgumentException("课程编码已存在");
            }
        } else {
            int updated = jdbc.update("update platform_training_course set course_name=?, category=?, instructor=?, schedule_at=?, capacity=?, status=?, description=?, updated_at=current_timestamp where id=?",
                    name, stringOr(payload.get("category"), "PRODUCT"), string(payload.get("instructor")), string(payload.get("scheduleAt")),
                    Math.max(0, number(payload.get("capacity"), 0)), stringOr(payload.get("status"), "DRAFT"), string(payload.get("description")), id);
            if (updated == 0) throw new IllegalArgumentException("课程不存在");
        }
        return course(id);
    }

    @Override
    public Map<String, Object> enrollCourse(long courseId, Map<String, Object> payload) {
        course(courseId);
        long tenantId = longNumber(payload.get("tenantId"), 0);
        customerTenant(tenantId);
        try {
            jdbc.update("insert into platform_training_enrollment(course_id, tenant_id, contact_name, status) values(?,?,?,?)",
                    courseId, tenantId, string(payload.get("contactName")), "ENROLLED");
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("该租户已经报名此课程");
        }
        return normalizedRow(jdbc.queryForMap("select e.id, e.course_id, e.tenant_id, t.tenant_code, t.tenant_name, e.contact_name, e.status, e.enrolled_at from platform_training_enrollment e join sys_tenant t on t.id=e.tenant_id where e.course_id=? and e.tenant_id=?", courseId, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCampaigns() {
        return normalizedRows(jdbc.queryForList("select id, campaign_code, campaign_name, campaign_type, audience, content, status, starts_at, ends_at, created_by, created_at, updated_at from platform_marketing_campaign order by created_at desc, id desc"));
    }

    @Override
    public Map<String, Object> saveCampaign(Map<String, Object> payload, Long id) {
        String code = required(payload, "campaignCode", "活动编码").toUpperCase(Locale.ROOT);
        String name = required(payload, "campaignName", "活动名称");
        String content = required(payload, "content", "活动内容");
        if (id == null) {
            try {
                jdbc.update("insert into platform_marketing_campaign(campaign_code, campaign_name, campaign_type, audience, content, status, starts_at, ends_at, created_by) values(?,?,?,?,?,?,?,?,?)",
                        code, name, stringOr(payload.get("campaignType"), "ANNOUNCEMENT"), stringOr(payload.get("audience"), "ALL_CUSTOMERS"), content,
                        stringOr(payload.get("status"), "DRAFT"), string(payload.get("startsAt")), string(payload.get("endsAt")), actor());
                id = ((Number) jdbc.queryForObject("select id from platform_marketing_campaign where campaign_code=?", Object.class, code)).longValue();
            } catch (DataAccessException ex) {
                throw new IllegalArgumentException("活动编码已存在");
            }
        } else {
            int updated = jdbc.update("update platform_marketing_campaign set campaign_name=?, campaign_type=?, audience=?, content=?, status=?, starts_at=?, ends_at=?, updated_at=current_timestamp where id=?",
                    name, stringOr(payload.get("campaignType"), "ANNOUNCEMENT"), stringOr(payload.get("audience"), "ALL_CUSTOMERS"), content,
                    stringOr(payload.get("status"), "DRAFT"), string(payload.get("startsAt")), string(payload.get("endsAt")), id);
            if (updated == 0) throw new IllegalArgumentException("营销活动不存在");
        }
        return campaign(id);
    }

    private Map<String, Object> tenant(long id) {
        List<Map<String, Object>> rows = listTenants(null).stream().filter(row -> longNumber(row.get("id"), 0) == id).toList();
        if (rows.isEmpty()) throw new IllegalArgumentException("租户不存在");
        return rows.get(0);
    }

    private Map<String, Object> customerTenant(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, tenant_code, tenant_name, tenant_type, plan_code, status from sys_tenant where id=? and tenant_type='CUSTOMER'", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("客户租户不存在");
        return rows.get(0);
    }

    private Map<String, Object> ticket(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select s.id, s.tenant_id, t.tenant_code, t.tenant_name, s.category, s.subject, s.status, s.priority, s.created_by, s.assignee, s.created_at, s.updated_at from platform_service_ticket s join sys_tenant t on t.id=s.tenant_id where s.id=?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("服务工单不存在");
        Map<String, Object> result = new LinkedHashMap<>(normalizedRow(rows.get(0)));
        result.put("messages", ticketMessages(id));
        return result;
    }

    private Map<String, Object> course(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, course_code, course_name, category, instructor, schedule_at, capacity, status, description, created_by, created_at, updated_at from platform_training_course where id=?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("课程不存在");
        return normalizedRow(rows.get(0));
    }

    private Map<String, Object> campaign(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, campaign_code, campaign_name, campaign_type, audience, content, status, starts_at, ends_at, created_by, created_at, updated_at from platform_marketing_campaign where id=?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("营销活动不存在");
        return normalizedRow(rows.get(0));
    }

    private void ensureAccounts(long tenantId) {
        try { jdbc.update("insert into tenant_billing_account(tenant_id) values(?)", tenantId); } catch (DataAccessException ignored) {}
        try { jdbc.update("insert into tenant_points_account(tenant_id) values(?)", tenantId); } catch (DataAccessException ignored) {}
        try { jdbc.update("insert into tenant_storage_account(tenant_id, quota_bytes) values(?, 10737418240)", tenantId); } catch (DataAccessException ignored) {}
    }

    private Object scalar(String sql, Object... args) { return jdbc.queryForObject(sql, Object.class, args); }
    private String actor() { return TenantContext.require().username(); }
    private static String required(Map<String, Object> payload, String key, String label) {
        String value = string(payload.get(key));
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static String stringOr(Object value, String fallback) { String text = string(value); return text == null || text.isBlank() ? fallback : text; }
    private static int number(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }
    private static long longNumber(Object value, long fallback) { try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }
    private static BigDecimal decimal(Object value, BigDecimal fallback) { try { return value == null ? fallback : new BigDecimal(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }
    private static Map<String, Object> normalizedRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(String.valueOf(key).toLowerCase(Locale.ROOT), value));
        return normalized;
    }
    private static List<Map<String, Object>> normalizedRows(List<Map<String, Object>> rows) {
        return rows.stream().map(PlatformAdminServiceImpl::normalizedRow).toList();
    }
}
