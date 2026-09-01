package com.polaris.mes.config;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuditInterceptor implements HandlerInterceptor {
    private final AuditService audit;

    public AuditInterceptor(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        if (!isAuditable(request) || TenantContext.current() == null) return;
        try {
            audit.record(TenantContext.require().username(), request.getMethod() + " " + request.getRequestURI(),
                    "HTTP", request.getRequestURI());
        } catch (RuntimeException ignored) {
            // An audit failure must never turn a completed business request into a 500.
        }
    }

    private boolean isAuditable(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        return ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))
                && uri.startsWith(request.getContextPath() + "/api/")
                && !uri.startsWith(request.getContextPath() + "/api/auth/")
                && !uri.startsWith(request.getContextPath() + "/api/health");
    }
}
