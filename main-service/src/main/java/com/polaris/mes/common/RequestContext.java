package com.polaris.mes.common;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestContext {
    private RequestContext() {}

    public static String actor(HttpServletRequest request) {
        TenantContext.Identity identity = TenantContext.require();
        return identity.username();
    }

    public static TenantContext.Identity identity() {
        return TenantContext.require();
    }

    public static void requireRole(String... roles) {
        String current = TenantContext.require().roleCode();
        for (String role : roles) if (role.equals(current)) return;
        throw new IllegalArgumentException("当前用户没有执行该操作的权限");
    }

    public static void requirePlatformAdmin() {
        TenantContext.Identity identity = TenantContext.require();
        if (!"polaris-admin".equals(identity.tenantCode()) || !"platform_admin".equals(identity.roleCode())) {
            throw new IllegalArgumentException("当前用户不是平台总管理员");
        }
    }
}
