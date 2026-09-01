package com.polaris.mes.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.PlatformService;
import com.polaris.mes.service.TenantTrafficService;
import com.polaris.mes.service.TrafficLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class AuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final PlatformService platform;
    private final TenantTrafficService traffic;

    public AuthenticationFilter(TokenService tokenService, ObjectMapper objectMapper, PlatformService platform, TenantTrafficService traffic) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.platform = platform;
        this.traffic = traffic;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isPublic(request)) {
            chain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized(response, "请先登录");
            return;
        }
        try {
            TenantContext.Identity tokenIdentity = tokenService.verify(authorization.substring("Bearer ".length()).trim());
            java.util.Map<String, Object> current = platform.findActiveUser(tokenIdentity.tenantId(), tokenIdentity.userId(), tokenIdentity.username());
            if (current == null) throw new IllegalArgumentException("租户或用户已停用");
            TenantContext.set(new TenantContext.Identity(tokenIdentity.tenantId(),
                    value(current, "tenant_code"), value(current, "tenant_name"),
                    tokenIdentity.userId(), tokenIdentity.username(), value(current, "role_code")));
            TenantContext.Identity identity = TenantContext.require();
            if (!traffic.isBillableTenant(identity.tenantId()) || trafficExempt(request)) {
                chain.doFilter(request, response);
            } else {
                traffic.requireAvailable(identity.tenantId());
                TrafficRequestWrapper countedRequest = new TrafficRequestWrapper(request);
                TrafficResponseWrapper countedResponse = new TrafficResponseWrapper(response);
                chain.doFilter(countedRequest, countedResponse);
                try {
                    traffic.consume(identity.tenantId(), safeAdd(countedRequest.bytesRead(), countedResponse.bodyBytes()));
                    Map<String, Object> usage = traffic.snapshot(identity.tenantId());
                    response.setHeader("X-Tenant-Traffic-Quota", String.valueOf(usage.get("quota_bytes")));
                    response.setHeader("X-Tenant-Traffic-Used", String.valueOf(usage.get("used_bytes")));
                    response.setHeader("X-Tenant-Traffic-Remaining", String.valueOf(usage.get("remaining_bytes")));
                } catch (DataAccessException ex) {
                    // Traffic accounting is post-response bookkeeping. A
                    // transient row deadlock must not turn a successful
                    // business request into an HTTP 500.
                    if (!isTransientTrafficFailure(ex)) throw ex;
                }
                countedResponse.copyTo(response);
            }
        } catch (TrafficLimitExceededException ex) {
            if (!response.isCommitted()) {
                response.reset();
                trafficLimited(response, ex.getMessage());
            }
            else throw ex;
        } catch (IllegalArgumentException ex) {
            unauthorized(response, ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private static boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || "/api/health".equals(path)
                || path.startsWith("/api/health/") || "/api/auth/login".equals(path)
                || "/api/auth/register".equals(path) || "/api/auth/tenants".equals(path)
                || "/api/auth/captcha".equals(path);
    }

    private static boolean trafficExempt(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "GET".equalsIgnoreCase(request.getMethod()) && "/api/tenant/traffic".equals(path);
    }

    private static long safeAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private static boolean isTransientTrafficFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = String.valueOf(current.getMessage()).toLowerCase(java.util.Locale.ROOT);
            if (message.contains("deadlock") || message.contains("lock wait timeout") || message.contains("cannot acquire lock")) return true;
        }
        return false;
    }

    private static String value(java.util.Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase(java.util.Locale.ROOT));
        return value == null ? "" : String.valueOf(value);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(message == null ? "登录状态无效" : message)));
    }

    private void trafficLimited(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Tenant-Traffic-Exhausted", "true");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(message == null ? "租户流量已用尽，请联系总管理员分配流量" : message)));
    }
}
