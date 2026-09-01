package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.PurchaseAiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/purchase-requisitions")
public class PurchaseAiController {
    private final PurchaseAiService service;

    public PurchaseAiController(PurchaseAiService service) {
        this.service = service;
    }

    @GetMapping("/context")
    public ApiResponse<Map<String, Object>> context() {
        RequestContext.requireRole("admin", "planner");
        return ApiResponse.ok(service.context());
    }

    @PostMapping("/parse")
    public ApiResponse<Map<String, Object>> parse(@RequestBody Map<String, Object> payload) {
        RequestContext.requireRole("admin", "planner");
        return ApiResponse.ok(service.parse(payload));
    }

    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validate(@RequestBody Map<String, Object> payload) {
        RequestContext.requireRole("admin", "planner");
        return ApiResponse.ok(service.validate(payload));
    }

    @PostMapping("/confirm")
    public ApiResponse<Map<String, Object>> confirm(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        RequestContext.requireRole("admin", "planner");
        return ApiResponse.ok(service.confirm(payload, idempotencyKey), "采购申请审批已发起");
    }
}
