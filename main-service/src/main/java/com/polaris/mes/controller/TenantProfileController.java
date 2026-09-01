package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.PlatformService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@RestController
@RequestMapping("/api/tenant")
public class TenantProfileController {
    private final PlatformService platformService;

    public TenantProfileController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        RequestContext.requireRole("admin");
        return ApiResponse.ok(platformService.tenantProfile());
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> update(@RequestBody Map<String, Object> payload) {
        RequestContext.requireRole("admin");
        return ApiResponse.ok(platformService.updateTenantProfile(payload), "租户配置已保存");
    }
}
