package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.BpmApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bpm")
public class BpmController {
    private final BpmApplicationService bpmService;

    public BpmController(BpmApplicationService bpmService) { this.bpmService = bpmService; }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() { return ApiResponse.ok(bpmService.overview()); }

    @GetMapping("/process-definitions")
    public ApiResponse<List<Map<String, Object>>> definitions() { return ApiResponse.ok(bpmService.listDefinitions()); }

    @GetMapping("/bindings")
    public ApiResponse<List<Map<String, Object>>> bindings() { return ApiResponse.ok(bpmService.listBindings()); }

    @PutMapping("/bindings/{businessFunction}")
    public ApiResponse<Map<String, Object>> bind(@PathVariable String businessFunction,
                                                  @RequestBody Map<String, Object> payload,
                                                  HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner");
        return ApiResponse.ok(bpmService.bindProcess(businessFunction, payload, RequestContext.actor(request)), "审批流程绑定已保存");
    }

    @PostMapping("/process-definitions")
    public ApiResponse<Map<String, Object>> createDefinition(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner");
        return ApiResponse.ok(bpmService.createDefinition(payload, RequestContext.actor(request)), "流程定义已发布");
    }

    @PostMapping("/process-definitions/{definitionId}/toggle")
    public ApiResponse<Void> toggleDefinition(@PathVariable String definitionId, @RequestBody Map<String, Object> payload) {
        RequestContext.requireRole("admin", "planner");
        bpmService.toggleDefinition(definitionId, Boolean.parseBoolean(String.valueOf(payload.getOrDefault("suspended", false))));
        return ApiResponse.ok(null, "流程定义状态已更新");
    }

    @GetMapping("/forms")
    public ApiResponse<List<Map<String, Object>>> forms() { return ApiResponse.ok(bpmService.listForms()); }

    @PostMapping("/forms")
    public ApiResponse<Void> createForm(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner");
        bpmService.createForm(payload, RequestContext.actor(request));
        return ApiResponse.ok(null, "表单已保存");
    }

    @PutMapping("/forms/{formCode}")
    public ApiResponse<Void> updateForm(@PathVariable String formCode, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner");
        bpmService.updateForm(formCode, payload, RequestContext.actor(request));
        return ApiResponse.ok(null, "表单已更新");
    }

    @GetMapping("/tasks")
    public ApiResponse<List<Map<String, Object>>> tasks(@RequestParam(defaultValue = "todo") String scope, HttpServletRequest request) {
        return ApiResponse.ok(bpmService.listTasks(scope, RequestContext.actor(request)));
    }

    @GetMapping("/instances")
    public ApiResponse<List<Map<String, Object>>> instances(@RequestParam(required = false) String status,
                                                              @RequestParam(required = false) String starter,
                                                              @RequestParam(required = false) String scope,
                                                              HttpServletRequest request) {
        String effectiveStarter = "mine".equalsIgnoreCase(scope) ? RequestContext.actor(request) : starter;
        return ApiResponse.ok(bpmService.listInstances(status, effectiveStarter));
    }

    @GetMapping("/instances/by-business")
    public ApiResponse<List<Map<String, Object>>> instancesByBusiness(@RequestParam String businessType,
                                                                        @RequestParam String businessId) {
        return ApiResponse.ok(bpmService.listInstancesByBusiness(businessType, businessId));
    }

    @GetMapping("/instances/{instanceId}")
    public ApiResponse<Map<String, Object>> instance(@PathVariable String instanceId) { return ApiResponse.ok(bpmService.instanceDetail(instanceId)); }

    @PostMapping("/process-instances")
    public ApiResponse<Map<String, Object>> start(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(bpmService.startProcess(payload, RequestContext.actor(request)), "流程已发起");
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ApiResponse<Map<String, Object>> complete(@PathVariable String taskId, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return ApiResponse.ok(bpmService.completeTask(taskId, payload, RequestContext.actor(request)), "审批操作已提交");
    }

    @PostMapping("/tasks/{taskId}/claim")
    public ApiResponse<Void> claim(@PathVariable String taskId, HttpServletRequest request) {
        bpmService.claimTask(taskId, RequestContext.actor(request));
        return ApiResponse.ok(null, "任务已签收");
    }

    @PostMapping("/tasks/{taskId}/unclaim")
    public ApiResponse<Void> unclaim(@PathVariable String taskId, HttpServletRequest request) {
        bpmService.unclaimTask(taskId, RequestContext.actor(request));
        return ApiResponse.ok(null, "任务已退回待办池");
    }

    @PostMapping("/instances/{instanceId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String instanceId, HttpServletRequest request) {
        bpmService.cancelInstance(instanceId, RequestContext.actor(request));
        return ApiResponse.ok(null, "流程已撤回");
    }
}
