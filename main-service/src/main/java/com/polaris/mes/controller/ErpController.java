package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.ErpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/erp")
public class ErpController {
    private final ErpService erp;

    public ErpController(ErpService erp) { this.erp = erp; }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() { return ApiResponse.ok(erp.overview()); }

    @GetMapping("/{domain}/records")
    public ApiResponse<List<Map<String, Object>>> records(@PathVariable String domain,
                                                           @RequestParam(required = false) String type,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String scope,
                                                           HttpServletRequest request) {
        return ApiResponse.ok(erp.listRecords(domain, type, keyword, status, scope, RequestContext.actor(request)));
    }

    @GetMapping("/{domain}/records/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String domain, @PathVariable long id) {
        return ApiResponse.ok(erp.detailRecord(domain, id));
    }

    /** Cross-domain lookup is used by the approval center to open an ERP document. */
    @GetMapping("/records/{id}")
    public ApiResponse<Map<String, Object>> detailAny(@PathVariable long id) {
        return ApiResponse.ok(erp.detailRecord(id));
    }

    @PostMapping("/{domain}/records")
    public ApiResponse<Map<String, Object>> create(@PathVariable String domain, @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(erp.createRecord(domain, payload), "ERP 业务单据已创建");
    }

    @PostMapping("/{domain}/records/draft")
    public ApiResponse<Map<String, Object>> saveDraft(@PathVariable String domain, @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(erp.saveDraft(domain, payload), "采购申请草稿已保存");
    }

    @PostMapping("/{domain}/records/{id}/transition")
    public ApiResponse<Map<String, Object>> transition(@PathVariable String domain, @PathVariable long id, @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(erp.transition(domain, id, payload), "ERP 业务单据状态已更新");
    }
}
