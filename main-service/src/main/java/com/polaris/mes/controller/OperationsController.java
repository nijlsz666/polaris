package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.OperationsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** REST surface for the manufacturing floor control tower. */
@RestController
@RequestMapping("/api/operations")
public class OperationsController {
    private final OperationsService operations;

    public OperationsController(OperationsService operations) { this.operations = operations; }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() { return ApiResponse.ok(operations.summary()); }

    @GetMapping("/equipment")
    public ApiResponse<List<Map<String, Object>>> equipment(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(operations.listEquipment(status, keyword));
    }

    @PostMapping("/equipment")
    public ApiResponse<Map<String, Object>> saveEquipment(@RequestBody Map<String, Object> payload) {
        RequestContext.requireRole("admin", "planner");
        return ApiResponse.ok(operations.saveEquipment(payload), "设备档案已保存");
    }

    @PostMapping("/equipment/{id}/heartbeat")
    public ApiResponse<Map<String, Object>> heartbeat(@PathVariable long id, @RequestBody Map<String, Object> payload) {
        RequestContext.requireRole("admin", "planner", "operator");
        return ApiResponse.ok(operations.heartbeat(id, payload), "设备状态已更新");
    }

    @GetMapping("/downtime")
    public ApiResponse<List<Map<String, Object>>> downtime(@RequestParam(required = false) String status, @RequestParam(required = false) String equipmentCode) {
        return ApiResponse.ok(operations.listDowntime(status, equipmentCode));
    }

    @PostMapping("/downtime")
    public ApiResponse<Map<String, Object>> startDowntime(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner", "operator");
        return ApiResponse.ok(operations.startDowntime(payload, RequestContext.actor(request)), "停机事件已登记，并已创建现场异常");
    }

    @PostMapping("/downtime/{id}/resume")
    public ApiResponse<Map<String, Object>> resumeDowntime(@PathVariable long id, HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner", "operator");
        return ApiResponse.ok(operations.resumeDowntime(id, RequestContext.actor(request)), "设备已恢复运行");
    }

    @GetMapping("/exceptions")
    public ApiResponse<List<Map<String, Object>>> exceptions(@RequestParam(required = false) String status, @RequestParam(required = false) String priority, @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(operations.listExceptions(status, priority, keyword));
    }

    @GetMapping("/exceptions/{id}")
    public ApiResponse<Map<String, Object>> exception(@PathVariable long id) { return ApiResponse.ok(operations.getException(id)); }

    @PostMapping("/exceptions")
    public ApiResponse<Map<String, Object>> createException(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner", "quality", "warehouse", "operator");
        return ApiResponse.ok(operations.createException(payload, RequestContext.actor(request)), "现场异常已登记");
    }

    @PostMapping("/exceptions/{id}/transition")
    public ApiResponse<Map<String, Object>> transition(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner", "quality", "warehouse", "operator");
        return ApiResponse.ok(operations.transitionException(id, payload, RequestContext.actor(request)), "异常状态已更新");
    }

    @GetMapping("/exceptions/{id}/actions")
    public ApiResponse<List<Map<String, Object>>> actions(@PathVariable long id) { return ApiResponse.ok(operations.listActions(id)); }

    @PostMapping("/exceptions/{id}/actions")
    public ApiResponse<Map<String, Object>> createAction(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner", "quality", "warehouse", "operator");
        return ApiResponse.ok(operations.createAction(id, payload, RequestContext.actor(request)), "责任动作已添加");
    }

    @PostMapping("/actions/{id}/complete")
    public ApiResponse<Map<String, Object>> completeAction(@PathVariable long id, HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner", "quality", "warehouse", "operator");
        return ApiResponse.ok(operations.completeAction(id, RequestContext.actor(request)), "责任动作已完成");
    }
}
