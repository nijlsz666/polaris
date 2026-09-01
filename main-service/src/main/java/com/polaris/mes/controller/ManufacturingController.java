package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.PlatformService;
import com.polaris.mes.service.BpmApplicationService;
import com.polaris.mes.service.MrpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manufacturing")
public class ManufacturingController {
    private final PlatformService platformService;
    private final BpmApplicationService bpmService;
    private final MrpService mrpService;
    public ManufacturingController(PlatformService platformService, BpmApplicationService bpmService, MrpService mrpService) { this.platformService = platformService; this.bpmService = bpmService; this.mrpService = mrpService; }

    @GetMapping("/boms")
    public ApiResponse<List<Map<String, Object>>> boms() { return ApiResponse.ok(platformService.listBoms()); }

    @PostMapping("/boms")
    public ApiResponse<Void> createBom(@RequestBody Map<String, Object> payload) { platformService.insertBom(payload); return ApiResponse.ok(null, "BOM 已保存"); }

    @GetMapping("/boms/{id}/items")
    public ApiResponse<List<Map<String, Object>>> bomItems(@PathVariable Long id) { return ApiResponse.ok(platformService.listBomItems(id)); }

    @PostMapping("/boms/{id}/publish")
    public ApiResponse<Void> publishBom(@PathVariable Long id) { platformService.publishBom(id); return ApiResponse.ok(null, "BOM 已发布"); }

    @PostMapping("/mrp")
    public ApiResponse<Map<String, Object>> calculateMrp(@RequestBody Map<String, Object> payload) { return ApiResponse.ok(platformService.calculateMrp(payload)); }

    /** Persisted MRP run with net requirements, open purchase supply and shortage documents. */
    @PostMapping("/mrp/run")
    public ApiResponse<Map<String, Object>> runMrp(@RequestBody Map<String, Object> payload) { return ApiResponse.ok(mrpService.run(payload), "MRP 运算已完成"); }

    @GetMapping("/mrp/runs")
    public ApiResponse<List<Map<String, Object>>> mrpRuns(@RequestParam(required = false) String status) { return ApiResponse.ok(mrpService.listRuns(status)); }

    @GetMapping("/mrp/runs/{id}")
    public ApiResponse<Map<String, Object>> mrpRun(@PathVariable long id) { return ApiResponse.ok(mrpService.detailRun(id)); }

    @GetMapping("/shortages")
    public ApiResponse<List<Map<String, Object>>> shortages(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword) { return ApiResponse.ok(mrpService.listShortages(status, keyword)); }

    @PostMapping("/shortages/{id}/purchase-requisition")
    public ApiResponse<Map<String, Object>> purchaseRequisition(@PathVariable long id, @RequestBody(required = false) Map<String, Object> payload) { return ApiResponse.ok(mrpService.createPurchaseRequisition(id, payload == null ? Map.of() : payload), "采购申请已创建"); }

    @PostMapping("/shortages/{id}/material-call")
    public ApiResponse<Map<String, Object>> materialCall(@PathVariable long id, @RequestBody(required = false) Map<String, Object> payload, HttpServletRequest request) { return ApiResponse.ok(mrpService.createMaterialCall(id, payload == null ? Map.of() : payload, RequestContext.actor(request)), "叫料单已创建"); }

    @GetMapping("/material-calls")
    public ApiResponse<List<Map<String, Object>>> materialCalls(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword) { return ApiResponse.ok(mrpService.listMaterialCalls(status, keyword)); }

    @PostMapping("/material-calls/{id}/transition")
    public ApiResponse<Map<String, Object>> materialCallTransition(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) { return ApiResponse.ok(mrpService.transitionMaterialCall(id, payload, RequestContext.actor(request)), "叫料单状态已更新"); }

    @GetMapping("/asns")
    public ApiResponse<List<Map<String, Object>>> asns(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword) { return ApiResponse.ok(mrpService.listAsns(status, keyword)); }

    @PostMapping("/asns")
    public ApiResponse<Map<String, Object>> createAsn(@RequestBody Map<String, Object> payload, HttpServletRequest request) { return ApiResponse.ok(mrpService.createAsn(payload, RequestContext.actor(request)), "ASN 单已创建"); }

    @GetMapping("/asns/{id}")
    public ApiResponse<Map<String, Object>> asn(@PathVariable long id) { return ApiResponse.ok(mrpService.detailAsn(id)); }

    @PostMapping("/asns/{id}/transition")
    public ApiResponse<Map<String, Object>> asnTransition(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) { return ApiResponse.ok(mrpService.transitionAsn(id, payload, RequestContext.actor(request)), "ASN 单状态已更新"); }

    @GetMapping("/work-orders")
    public ApiResponse<List<Map<String, Object>>> workOrders() { return ApiResponse.ok(platformService.listWorkOrders()); }

    @GetMapping("/work-orders/{id}")
    public ApiResponse<Map<String, Object>> workOrder(@PathVariable long id) { return ApiResponse.ok(platformService.findWorkOrder(id)); }

    @PostMapping("/work-orders")
    public ApiResponse<Void> createWorkOrder(@RequestBody Map<String, Object> payload) { platformService.insertWorkOrder(payload); return ApiResponse.ok(null, "工单已创建"); }

    @PostMapping("/work-orders/{id}/report")
    public ApiResponse<Void> report(@PathVariable Long id, @RequestBody Map<String, Object> payload) { platformService.reportWork(id, payload); return ApiResponse.ok(null, "报工已提交"); }

    @PostMapping("/work-orders/{id}/submit-approval")
    public ApiResponse<Map<String, Object>> submitApproval(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(bpmService.startWorkOrderApproval(id, RequestContext.actor(request)), "工单已提交审批");
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() { return ApiResponse.ok(platformService.overview()); }
}
