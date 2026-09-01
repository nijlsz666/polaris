package com.polaris.mes.service.impl;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final JdbcTemplate jdbc;

    public NotificationServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> list(long userId, boolean unreadOnly, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String readClause = unreadOnly ? " and read_at is null" : "";
        return jdbc.queryForList("select id, notification_type, title, content, level, action_url, read_at, created_at " +
                "from platform_notification where tenant_id=? and (user_id is null or user_id=?)" + readClause +
                " order by created_at desc, id desc limit " + safeLimit, tenantId(), userId);
    }

    @Override
    public long unreadCount(long userId) {
        Long count = jdbc.queryForObject("select count(*) from platform_notification where tenant_id=? and " +
                "(user_id is null or user_id=?) and read_at is null", Long.class, tenantId(), userId);
        return count == null ? 0 : count;
    }

    @Override
    @Transactional
    public void markRead(long id, long userId) {
        jdbc.update("update platform_notification set read_at=current_timestamp where tenant_id=? and id=? " +
                "and (user_id is null or user_id=?)", tenantId(), id, userId);
    }

    @Override
    @Transactional
    public void markAllRead(long userId) {
        jdbc.update("update platform_notification set read_at=current_timestamp where tenant_id=? and " +
                "(user_id is null or user_id=?) and read_at is null", tenantId(), userId);
    }

    @Override
    @Transactional
    public void create(long userId, String type, String title, String content, String level, String actionUrl) {
        jdbc.update("insert into platform_notification(tenant_id, user_id, notification_type, title, content, level, action_url) " +
                "values(?,?,?,?,?,?,?)", tenantId(), userId, type, title, content, level, actionUrl);
    }

    private long tenantId() {
        return TenantContext.require().tenantId();
    }
}
