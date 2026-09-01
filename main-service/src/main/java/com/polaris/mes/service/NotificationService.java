package com.polaris.mes.service;

import java.util.List;
import java.util.Map;

public interface NotificationService {
    List<Map<String, Object>> list(long userId, boolean unreadOnly, int limit);
    long unreadCount(long userId);
    void markRead(long id, long userId);
    void markAllRead(long userId);
    void create(long userId, String type, String title, String content, String level, String actionUrl);
}
