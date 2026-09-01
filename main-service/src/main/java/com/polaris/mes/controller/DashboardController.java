package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.service.PlatformService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final PlatformService platformService;
    public DashboardController(PlatformService platformService) { this.platformService = platformService; }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() { return ApiResponse.ok(platformService.overview()); }
}
