package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.QualityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quality")
public class QualityController {
    private final QualityService quality;

    public QualityController(QualityService quality) {
        this.quality = quality;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() { return ApiResponse.ok(quality.summary()); }

    @GetMapping("/plans")
    public ApiResponse<List<Map<String, Object>>> plans(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(quality.listPlans(status, keyword));
    }

    @GetMapping("/plans/{id}")
    public ApiResponse<Map<String, Object>> plan(@PathVariable long id) { return ApiResponse.ok(quality.getPlan(id)); }

    @PostMapping("/plans")
    public ApiResponse<Map<String, Object>> createPlan(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "planner");
        return ApiResponse.ok(quality.savePlan(payload, 0, RequestContext.actor(request)), "检验计划已保存");
    }

    @PutMapping("/plans/{id}")
    public ApiResponse<Map<String, Object>> updatePlan(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "planner");
        return ApiResponse.ok(quality.savePlan(payload, id, RequestContext.actor(request)), "检验计划已更新");
    }

    @GetMapping("/lots")
    public ApiResponse<List<Map<String, Object>>> lots(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword, @RequestParam(required = false) String inspectionType) {
        return ApiResponse.ok(quality.listLots(status, keyword, inspectionType));
    }

    @GetMapping("/lots/{id}")
    public ApiResponse<Map<String, Object>> lot(@PathVariable long id) { return ApiResponse.ok(quality.getLot(id)); }

    @PostMapping("/lots")
    public ApiResponse<Map<String, Object>> createLot(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "warehouse", "operator");
        return ApiResponse.ok(quality.createLot(payload, RequestContext.actor(request)), "检验批已创建");
    }

    @PostMapping("/lots/{id}/start")
    public ApiResponse<Map<String, Object>> startLot(@PathVariable long id, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "warehouse", "operator");
        return ApiResponse.ok(quality.startLot(id, RequestContext.actor(request)), "检验已开始");
    }

    @PostMapping("/lots/{id}/results")
    public ApiResponse<Map<String, Object>> saveResults(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "warehouse", "operator");
        return ApiResponse.ok(quality.saveResults(id, payload, RequestContext.actor(request)), "检验结果已保存");
    }

    @PostMapping("/lots/{id}/complete")
    public ApiResponse<Map<String, Object>> completeLot(@PathVariable long id, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "warehouse", "operator");
        return ApiResponse.ok(quality.completeLot(id, RequestContext.actor(request)), "检验批已完成");
    }

    @GetMapping("/nonconformances")
    public ApiResponse<List<Map<String, Object>>> nonconformances(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(quality.listNonconformances(status, keyword));
    }

    @PostMapping("/nonconformances")
    public ApiResponse<Map<String, Object>> createNonconformance(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "warehouse");
        return ApiResponse.ok(quality.createNonconformance(payload, RequestContext.actor(request)), "不合格单已创建");
    }

    @PostMapping("/nonconformances/{id}/disposition")
    public ApiResponse<Map<String, Object>> disposition(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "warehouse");
        return ApiResponse.ok(quality.updateDisposition(id, payload, RequestContext.actor(request)), "不合格处置已保存");
    }

    @PostMapping("/nonconformances/{id}/close")
    public ApiResponse<Map<String, Object>> closeNonconformance(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality");
        return ApiResponse.ok(quality.closeNonconformance(id, payload, RequestContext.actor(request)), "不合格单已关闭");
    }

    @GetMapping("/nonconformances/{id}/actions")
    public ApiResponse<List<Map<String, Object>>> actions(@PathVariable long id) { return ApiResponse.ok(quality.listActions(id)); }

    @PostMapping("/nonconformances/{id}/actions")
    public ApiResponse<Map<String, Object>> createAction(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality");
        return ApiResponse.ok(quality.createAction(id, payload), "整改措施已添加");
    }

    @PostMapping("/actions/{id}/complete")
    public ApiResponse<Map<String, Object>> completeAction(@PathVariable long id, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality");
        return ApiResponse.ok(quality.completeAction(id, RequestContext.actor(request)), "整改措施已完成");
    }

    @GetMapping("/supplier-evaluations")
    public ApiResponse<List<Map<String, Object>>> supplierEvaluations(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(quality.listSupplierEvaluations(status, keyword));
    }

    @PostMapping("/supplier-evaluations")
    public ApiResponse<Map<String, Object>> createSupplierEvaluation(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "planner", "procurement");
        return ApiResponse.ok(quality.saveSupplierEvaluation(payload, 0, RequestContext.actor(request)), "供应商考评已保存");
    }

    @PutMapping("/supplier-evaluations/{id}")
    public ApiResponse<Map<String, Object>> updateSupplierEvaluation(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "planner", "procurement");
        return ApiResponse.ok(quality.saveSupplierEvaluation(payload, id, RequestContext.actor(request)), "供应商考评已更新");
    }

    @PostMapping("/supplier-evaluations/{id}/submit")
    public ApiResponse<Map<String, Object>> submitSupplierEvaluation(@PathVariable long id, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "planner", "procurement");
        return ApiResponse.ok(quality.submitSupplierEvaluation(id, RequestContext.actor(request)), "供应商考评已提交");
    }

    @GetMapping("/avl")
    public ApiResponse<List<Map<String, Object>>> avl(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(quality.listAvl(status, keyword));
    }

    @PostMapping("/avl")
    public ApiResponse<Map<String, Object>> createAvl(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "planner");
        return ApiResponse.ok(quality.saveAvl(payload, 0, RequestContext.actor(request)), "AVL 已保存");
    }

    @PutMapping("/avl/{id}")
    public ApiResponse<Map<String, Object>> updateAvl(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "planner");
        return ApiResponse.ok(quality.saveAvl(payload, id, RequestContext.actor(request)), "AVL 已更新");
    }

    @PostMapping("/avl/{id}/status")
    public ApiResponse<Map<String, Object>> updateAvlStatus(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "planner");
        return ApiResponse.ok(quality.updateAvlStatus(id, payload, RequestContext.actor(request)), "AVL 状态已更新");
    }

    @GetMapping("/ipqc")
    public ApiResponse<List<Map<String, Object>>> ipqc(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword, @RequestParam(required = false) String lineCode) {
        return ApiResponse.ok(quality.listIpqc(status, keyword, lineCode));
    }

    @GetMapping("/ipqc/{id}")
    public ApiResponse<Map<String, Object>> ipqcDetail(@PathVariable long id) { return ApiResponse.ok(quality.getIpqc(id)); }

    @PostMapping("/ipqc")
    public ApiResponse<Map<String, Object>> createIpqc(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "planner", "operator");
        return ApiResponse.ok(quality.createIpqc(payload, RequestContext.actor(request)), "IPQC 巡检已创建");
    }

    @PostMapping("/ipqc/{id}/result")
    public ApiResponse<Map<String, Object>> saveIpqcResult(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "quality", "planner", "operator");
        return ApiResponse.ok(quality.saveIpqcResult(id, payload, RequestContext.actor(request)), "IPQC 结果已保存");
    }
}
