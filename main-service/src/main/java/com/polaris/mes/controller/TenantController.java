package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.common.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Exposes the tenant bound to the signed token for UI workspace displays. */
@RestController
@RequestMapping("/api/tenant")
public class TenantController {
    public TenantController() {}

    @GetMapping("/current")
    public ApiResponse<?> current() {
        TenantContext.Identity identity = RequestContext.identity();
        return ApiResponse.ok(Map.of(
                "id", identity.tenantId(), "code", identity.tenantCode(), "name", identity.tenantName(),
                "user", identity.username(), "roleCode", identity.roleCode()));
    }
}
