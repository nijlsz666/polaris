package com.polaris.mes.common;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Request-scoped tenant identity. Database access code must use this context rather
 * than accepting a tenant id from a request parameter or body.
 */
public final class TenantContext {
    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public record Identity(long tenantId, String tenantCode, String tenantName,
                           long userId, String username, String roleCode) {}

    public static void set(Identity identity) {
        CURRENT.set(Objects.requireNonNull(identity, "identity"));
    }

    public static Identity require() {
        Identity identity = CURRENT.get();
        if (identity == null) throw new IllegalStateException("当前请求未绑定租户");
        return identity;
    }

    public static Identity current() { return CURRENT.get(); }

    public static void clear() { CURRENT.remove(); }

    public static <T> T run(Identity identity, Supplier<T> action) {
        Identity previous = CURRENT.get();
        try {
            set(identity);
            return action.get();
        } finally {
            if (previous == null) clear(); else set(previous);
        }
    }

    public static void run(Identity identity, Runnable action) {
        run(identity, () -> { action.run(); return null; });
    }
}
