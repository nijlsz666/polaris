package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.TenantTrafficService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/tenant/traffic")
public class TenantTrafficController {
    private final TenantTrafficService traffic;

    public TenantTrafficController(TenantTrafficService traffic) {
        this.traffic = traffic;
    }

    /** The read endpoint remains available even when the quota is exhausted. */
    @GetMapping
    public ApiResponse<Map<String, Object>> current() {
        return ApiResponse.ok(traffic.snapshot(TenantContext.require().tenantId()));
    }
}
