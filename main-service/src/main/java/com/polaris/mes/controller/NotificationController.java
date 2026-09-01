package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                        @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(notifications.list(TenantContext.require().userId(), unreadOnly, limit));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("count", notifications.unreadCount(TenantContext.require().userId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable long id) {
        notifications.markRead(id, TenantContext.require().userId());
        return ApiResponse.ok(null, "通知已读");
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notifications.markAllRead(TenantContext.require().userId());
        return ApiResponse.ok(null, "通知已全部读完");
    }
}
