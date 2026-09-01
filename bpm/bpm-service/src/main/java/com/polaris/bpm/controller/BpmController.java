package com.polaris.bpm.controller;

import com.polaris.bpm.common.ApiResponse;
import com.polaris.bpm.common.RequestContext;
import com.polaris.bpm.service.BpmService;
import com.polaris.bpm.service.WorkflowEventQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bpm")
@Validated
public class BpmController {
    private final BpmService service;
    private final WorkflowEventQueryService events;
    public BpmController(BpmService service, WorkflowEventQueryService events) {
        this.service = service;
        this.events = events;
    }

    @GetMapping("/overview") public ApiResponse<Map<String, Object>> overview(HttpServletRequest request) { return ApiResponse.ok(service.overview(RequestContext.actor(request))); }
    @GetMapping("/process-definitions") public ApiResponse<List<Map<String, Object>>> definitions() { return ApiResponse.ok(service.listDefinitions()); }
    @PostMapping("/process-definitions") public ApiResponse<Map<String, Object>> createDefinition(@RequestBody Map<String, Object> payload) { return ApiResponse.ok(service.createDefinition(payload), "流程定义已发布"); }
    @PostMapping("/process-definitions/{id}/toggle") public ApiResponse<Void> toggleDefinition(@PathVariable String id, @RequestBody Map<String, Object> payload) { service.toggleDefinition(id, Boolean.parseBoolean(String.valueOf(payload.getOrDefault("suspended", false)))); return ApiResponse.ok(null, "流程定义状态已更新"); }
    @GetMapping("/process-designs") public ApiResponse<List<Map<String, Object>>> designs() { return ApiResponse.ok(service.listDesigns()); }
    @GetMapping("/process-designs/{code}") public ApiResponse<Map<String, Object>> design(@PathVariable String code) { return ApiResponse.ok(service.getDesign(code)); }
    @PostMapping("/process-designs/validate") public ApiResponse<Map<String, Object>> validateDesign(@RequestBody Map<String, Object> payload) { return ApiResponse.ok(service.validateDesignPayload(payload)); }
    @PutMapping("/process-designs/{code}") public ApiResponse<Map<String, Object>> saveDesign(@PathVariable String code, @RequestBody Map<String, Object> payload, HttpServletRequest request) { return ApiResponse.ok(service.saveDesign(code, payload, RequestContext.actor(request), "DRAFT"), "流程草稿已保存"); }
    @PostMapping("/process-designs/{code}/publish") public ApiResponse<Map<String, Object>> publishDesign(@PathVariable String code, @RequestBody Map<String, Object> payload, HttpServletRequest request) { return ApiResponse.ok(service.publishDesign(code, payload, RequestContext.actor(request)), "流程已发布"); }
    @GetMapping("/forms") public ApiResponse<List<Map<String, Object>>> forms() { return ApiResponse.ok(service.listForms()); }
    @PostMapping("/forms") public ApiResponse<Void> createForm(@RequestBody Map<String, Object> payload, HttpServletRequest request) { service.createForm(payload, RequestContext.actor(request)); return ApiResponse.ok(null, "表单已保存"); }
    @PutMapping("/forms/{code}") public ApiResponse<Void> updateForm(@PathVariable String code, @RequestBody Map<String, Object> payload, HttpServletRequest request) { service.updateForm(code, payload, RequestContext.actor(request)); return ApiResponse.ok(null, "表单已更新"); }
    @GetMapping("/tasks") public ApiResponse<List<Map<String, Object>>> tasks(@RequestParam(defaultValue = "todo") String scope, HttpServletRequest request) { return ApiResponse.ok(service.listTasks(scope, RequestContext.actor(request))); }
    @GetMapping("/instances") public ApiResponse<List<Map<String, Object>>> instances(@RequestParam(required = false) String status, @RequestParam(required = false) String starter) { return ApiResponse.ok(service.listInstances(status, starter)); }
    @GetMapping("/instances/{id}") public ApiResponse<Map<String, Object>> detail(@PathVariable String id) { return ApiResponse.ok(service.detail(id)); }
    @GetMapping("/events") public ApiResponse<List<Map<String, Object>>> events(@RequestParam(required = false) String instanceId,
                                                                                   @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(events.list(instanceId, limit));
    }
    @PostMapping("/process-instances") public ApiResponse<Map<String, Object>> start(@RequestBody Map<String, Object> payload, HttpServletRequest request) { return ApiResponse.ok(service.start(payload, RequestContext.actor(request)), "流程已发起"); }
    @PostMapping("/tasks/{id}/complete") public ApiResponse<Map<String, Object>> complete(@PathVariable String id, @RequestBody Map<String, Object> payload, HttpServletRequest request) { return ApiResponse.ok(service.complete(id, payload, RequestContext.actor(request)), "审批操作已提交"); }
    @PostMapping("/tasks/{id}/claim") public ApiResponse<Void> claim(@PathVariable String id, HttpServletRequest request) { service.claim(id, RequestContext.actor(request)); return ApiResponse.ok(null, "任务已签收"); }
    @PostMapping("/tasks/{id}/unclaim") public ApiResponse<Void> unclaim(@PathVariable String id, HttpServletRequest request) { service.unclaim(id, RequestContext.actor(request)); return ApiResponse.ok(null, "任务已退回待办池"); }
    @PostMapping("/tasks/{id}/delegate") public ApiResponse<Void> delegate(@PathVariable String id, @RequestBody Map<String, Object> payload, HttpServletRequest request) { service.delegate(id, String.valueOf(payload.getOrDefault("targetUser", "")), RequestContext.actor(request)); return ApiResponse.ok(null, "任务已转办"); }
    @PostMapping("/tasks/{id}/comments") public ApiResponse<Void> comment(@PathVariable String id, @RequestBody Map<String, Object> payload, HttpServletRequest request) { service.comment(id, String.valueOf(payload.getOrDefault("comment", "")), RequestContext.actor(request)); return ApiResponse.ok(null, "评论已添加"); }
    @PostMapping("/instances/{id}/cancel") public ApiResponse<Void> cancel(@PathVariable String id, HttpServletRequest request) { service.cancel(id, RequestContext.actor(request)); return ApiResponse.ok(null, "流程已撤回"); }
    @PostMapping("/instances/{id}/suspend") public ApiResponse<Void> suspend(@PathVariable String id, HttpServletRequest request) { service.suspend(id, RequestContext.actor(request)); return ApiResponse.ok(null, "流程已挂起"); }
    @PostMapping("/instances/{id}/resume") public ApiResponse<Void> resume(@PathVariable String id, HttpServletRequest request) { service.resume(id, RequestContext.actor(request)); return ApiResponse.ok(null, "流程已恢复"); }
}
