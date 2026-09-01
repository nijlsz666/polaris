package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.PlatformAdminService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform")
public class PlatformAdminController {
    private final PlatformAdminService platform;

    public PlatformAdminController(PlatformAdminService platform) { this.platform = platform; }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() { auth(); return ApiResponse.ok(platform.overview()); }

    @GetMapping("/tenants")
    public ApiResponse<List<Map<String, Object>>> tenants(@RequestParam(required = false) String keyword) { auth(); return ApiResponse.ok(platform.listTenants(keyword)); }

    @PostMapping("/tenants")
    public ApiResponse<Map<String, Object>> createTenant(@RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.createTenant(payload), "租户已创建"); }

    @PutMapping("/tenants/{tenantId}")
    public ApiResponse<Map<String, Object>> updateTenant(@PathVariable long tenantId, @RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.updateTenant(tenantId, payload), "租户已更新"); }

    @GetMapping("/features")
    public ApiResponse<List<Map<String, Object>>> features() { auth(); return ApiResponse.ok(platform.listFeatures()); }

    @GetMapping("/tenants/{tenantId}/features")
    public ApiResponse<List<Map<String, Object>>> tenantFeatures(@PathVariable long tenantId) { auth(); return ApiResponse.ok(platform.listTenantFeatures(tenantId)); }

    @PutMapping("/tenants/{tenantId}/features")
    public ApiResponse<List<Map<String, Object>>> updateFeatures(@PathVariable long tenantId, @RequestBody List<Map<String, Object>> grants) { auth(); return ApiResponse.ok(platform.updateTenantFeatures(tenantId, grants), "租户功能授权已更新"); }

    @GetMapping("/billing")
    public ApiResponse<Map<String, Object>> billing(@RequestParam long tenantId) { auth(); return ApiResponse.ok(platform.billing(tenantId)); }

    @PostMapping("/billing/records")
    public ApiResponse<Map<String, Object>> createBilling(@RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.createBillingRecord(payload), "计费记录已保存"); }

    @GetMapping("/points")
    public ApiResponse<Map<String, Object>> points(@RequestParam long tenantId) { auth(); return ApiResponse.ok(platform.points(tenantId)); }

    @PostMapping("/points/adjust")
    public ApiResponse<Map<String, Object>> adjustPoints(@RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.adjustPoints(payload), "积分已调整"); }

    @GetMapping("/traffic")
    public ApiResponse<Map<String, Object>> traffic(@RequestParam long tenantId) { auth(); return ApiResponse.ok(platform.traffic(tenantId)); }

    @PutMapping("/traffic")
    public ApiResponse<Map<String, Object>> allocateTraffic(@RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.allocateTraffic(payload), "租户流量配额已保存"); }

    @PostMapping("/traffic/allocate")
    public ApiResponse<Map<String, Object>> allocateTrafficAlias(@RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.allocateTraffic(payload), "租户流量配额已保存"); }

    @GetMapping("/storage")
    public ApiResponse<Map<String, Object>> storage(@RequestParam long tenantId) { auth(); return ApiResponse.ok(platform.storage(tenantId)); }

    @PutMapping("/storage")
    public ApiResponse<Map<String, Object>> allocateStorage(@RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.allocateStorage(payload), "租户存储计费配置已保存"); }

    /** Adapter endpoint for file/object storage integrations to synchronize actual usage. */
    @PostMapping("/storage/usage")
    public ApiResponse<Map<String, Object>> recordStorageUsage(@RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.recordStorageUsage(payload), "租户存储用量已同步"); }

    @GetMapping("/tickets")
    public ApiResponse<List<Map<String, Object>>> tickets(@RequestParam(required = false) String status) { auth(); return ApiResponse.ok(platform.listTickets(status)); }

    @GetMapping("/tickets/{ticketId}/messages")
    public ApiResponse<List<Map<String, Object>>> ticketMessages(@PathVariable long ticketId) { auth(); return ApiResponse.ok(platform.ticketMessages(ticketId)); }

    @PostMapping("/tickets")
    public ApiResponse<Map<String, Object>> createTicket(@RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.createTicket(payload), "服务工单已创建"); }

    @PostMapping("/tickets/{ticketId}/reply")
    public ApiResponse<Map<String, Object>> replyTicket(@PathVariable long ticketId, @RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.replyTicket(ticketId, payload), "回复已发送"); }

    @GetMapping("/training/courses")
    public ApiResponse<List<Map<String, Object>>> courses() { auth(); return ApiResponse.ok(platform.listCourses()); }

    @PostMapping("/training/courses")
    public ApiResponse<Map<String, Object>> createCourse(@RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.saveCourse(payload, null), "培训课程已创建"); }

    @PutMapping("/training/courses/{id}")
    public ApiResponse<Map<String, Object>> updateCourse(@PathVariable long id, @RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.saveCourse(payload, id), "培训课程已更新"); }

    @PostMapping("/training/courses/{id}/enroll")
    public ApiResponse<Map<String, Object>> enrollCourse(@PathVariable long id, @RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.enrollCourse(id, payload), "培训报名已登记"); }

    @GetMapping("/campaigns")
    public ApiResponse<List<Map<String, Object>>> campaigns() { auth(); return ApiResponse.ok(platform.listCampaigns()); }

    @PostMapping("/campaigns")
    public ApiResponse<Map<String, Object>> createCampaign(@RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.saveCampaign(payload, null), "营销活动已创建"); }

    @PutMapping("/campaigns/{id}")
    public ApiResponse<Map<String, Object>> updateCampaign(@PathVariable long id, @RequestBody Map<String, Object> payload) { auth(); return ApiResponse.ok(platform.saveCampaign(payload, id), "营销活动已更新"); }

    private void auth() { RequestContext.requirePlatformAdmin(); }
}
