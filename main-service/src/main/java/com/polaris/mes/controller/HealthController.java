package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final HealthService healthService;

    public HealthController(HealthService healthService) { this.healthService = healthService; }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() { return ApiResponse.ok(Map.of("status", "UP", "service", "polaris-service")); }

    @GetMapping("/health/readiness")
    public ApiResponse<Map<String, Object>> readiness() {
        try {
            return ApiResponse.ok(healthService.readiness());
        } catch (RuntimeException ex) {
            return ApiResponse.fail("数据库未就绪");
        }
    }
}
