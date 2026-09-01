package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.TenantContext;
import com.polaris.mes.security.CaptchaService;
import com.polaris.mes.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    @GetMapping("/tenants")
    public ApiResponse<List<java.util.Map<String, Object>>> tenants() {
        return ApiResponse.ok(authService.tenants());
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String loginKey = captchaService.loginKey(request, payload);
        if (captchaService.isRequired(loginKey)
                && !captchaService.verify(loginKey, text(payload, "captchaId"), text(payload, "captchaCode"))) {
            return ApiResponse.fail(captchaService.issue(loginKey), "图形验证码错误，请重新输入");
        }
        Map<String, Object> session = authService.login(payload);
        if (session == null) {
            return ApiResponse.fail(captchaService.issue(loginKey), "用户名或密码错误，请完成图形验证码后重试");
        }
        captchaService.clear(loginKey);
        return ApiResponse.ok(session, "登录成功");
    }

    @GetMapping("/captcha")
    public ApiResponse<?> captcha(@RequestParam Map<String, String> query, HttpServletRequest request) {
        Map<String, Object> payload = Map.of("tenantCode", query.getOrDefault("tenantCode", ""), "username", query.getOrDefault("username", ""));
        return ApiResponse.ok(captchaService.issue(captchaService.loginKey(request, payload)));
    }

    @PostMapping("/register")
    public ApiResponse<?> register(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(authService.register(payload), "企业工作区已创建");
    }

    @GetMapping("/me")
    public ApiResponse<?> me() {
        TenantContext.Identity identity = TenantContext.require();
        return ApiResponse.ok(Map.of(
                "tenant", Map.of("id", identity.tenantId(), "code", identity.tenantCode(), "name", identity.tenantName()),
                "user", Map.of("id", identity.userId(), "username", identity.username(), "roleCode", identity.roleCode())));
    }

    private static String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }

}
