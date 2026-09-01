package com.polaris.mes.service.impl;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.security.PasswordHasher;
import com.polaris.mes.security.TokenService;
import com.polaris.mes.service.AuthService;
import com.polaris.mes.service.NotificationService;
import com.polaris.mes.service.PlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {
    private final PlatformService platform;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final NotificationService notifications;

    public AuthServiceImpl(PlatformService platform, PasswordHasher passwordHasher, TokenService tokenService, NotificationService notifications) {
        this.platform = platform;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.notifications = notifications;
    }

    @Override public List<Map<String, Object>> tenants() { return platform.listActiveTenants(); }

    @Override
    @Transactional
    public Map<String, Object> login(Map<String, Object> payload) {
        String tenantCode = String.valueOf(payload.getOrDefault("tenantCode", "demo"));
        String username = String.valueOf(payload.getOrDefault("username", ""));
        String password = String.valueOf(payload.getOrDefault("password", ""));
        Map<String, Object> user = platform.findUser(tenantCode, username);
        if (user == null || !"1".equals(String.valueOf(user.get("status"))) || !passwordMatches(password, user)) return null;
        TenantContext.Identity identity = identity(user);
        platform.recordLogin(identity.tenantId(), identity.userId());
        return session(identity, user.get("display_name"));
    }

    @Override
    @Transactional
    public Map<String, Object> register(Map<String, Object> payload) {
        String password = String.valueOf(payload.getOrDefault("password", ""));
        if (password.length() < 8) throw new IllegalArgumentException("管理员密码至少需要 8 位");
        Map<String, Object> registered = platform.registerTenant(payload, passwordHasher.hash(password));
        TenantContext.Identity identity = new TenantContext.Identity(
                ((Number) registered.get("tenantId")).longValue(), String.valueOf(registered.get("tenantCode")), String.valueOf(registered.get("tenantName")),
                ((Number) registered.get("userId")).longValue(), String.valueOf(registered.get("username")), "admin");
        TenantContext.run(identity, () -> notifications.create(identity.userId(), "ONBOARDING", "欢迎使用 Polaris",
                "企业工作区已创建。建议先完善仓库、物料和角色权限，再邀请团队成员。", "INFO", "/admin/users"));
        return session(identity, registered.get("displayName"));
    }

    private TenantContext.Identity identity(Map<String, Object> user) {
        return new TenantContext.Identity(((Number) user.get("tenant_id")).longValue(), String.valueOf(user.get("tenant_code")), String.valueOf(user.get("tenant_name")),
                ((Number) user.get("id")).longValue(), String.valueOf(user.get("username")), String.valueOf(user.get("role_code")));
    }

    private Map<String, Object> session(TenantContext.Identity identity, Object displayName) {
        return Map.of("token", tokenService.issue(identity),
                "tenant", Map.of("id", identity.tenantId(), "code", identity.tenantCode(), "name", identity.tenantName()),
                "user", Map.of("id", identity.userId(), "username", identity.username(), "displayName", displayName == null ? identity.username() : displayName, "roleCode", identity.roleCode()));
    }

    private boolean passwordMatches(String password, Map<String, Object> user) {
        String stored = String.valueOf(user.get("password_hash"));
        if (passwordHasher.matches(password, stored)) return true;
        if (passwordHasher.isLegacyPlaintext(stored) && stored.equals(password)) {
            platform.updatePasswordHash(((Number) user.get("tenant_id")).longValue(), ((Number) user.get("id")).longValue(), passwordHasher.hash(password));
            return true;
        }
        return false;
    }
}
