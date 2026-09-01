package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.PlatformService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/design")
public class DesignController {
    private final PlatformService platformService;
    public DesignController(PlatformService platformService) { this.platformService = platformService; }

    @GetMapping("/reports")
    public ApiResponse<List<Map<String, Object>>> reports() { return ApiResponse.ok(platformService.listReports()); }

    @PostMapping("/reports")
    public ApiResponse<Map<String, Object>> createReport(@RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin", "planner"); return ApiResponse.ok(platformService.saveReport(payload, null), "报表已创建"); }

    @PutMapping("/reports/{id}")
    public ApiResponse<Map<String, Object>> updateReport(@PathVariable long id, @RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin", "planner"); return ApiResponse.ok(platformService.saveReport(payload, id), "报表已保存"); }

    @PostMapping("/reports/{id}/publish")
    public ApiResponse<Map<String, Object>> publishReport(@PathVariable long id) { RequestContext.requireRole("admin", "planner"); return ApiResponse.ok(platformService.publishReport(id), "报表已发布"); }

    @GetMapping("/reports/{id}/preview")
    public ApiResponse<Map<String, Object>> previewReport(@PathVariable long id) { return ApiResponse.ok(platformService.previewReport(id)); }

    @GetMapping("/low-code/pages")
    public ApiResponse<List<Map<String, Object>>> lowCodePages() { return ApiResponse.ok(platformService.listLowCodePages()); }

    @PostMapping("/low-code/pages")
    public ApiResponse<Map<String, Object>> createLowCodePage(@RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin", "planner"); return ApiResponse.ok(platformService.saveLowCodePage(payload, null), "页面已创建"); }

    @PutMapping("/low-code/pages/{id}")
    public ApiResponse<Map<String, Object>> updateLowCodePage(@PathVariable long id, @RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin", "planner"); return ApiResponse.ok(platformService.saveLowCodePage(payload, id), "页面已保存"); }

    @PostMapping("/low-code/pages/{id}/publish")
    public ApiResponse<Map<String, Object>> publishLowCodePage(@PathVariable long id) { RequestContext.requireRole("admin", "planner"); return ApiResponse.ok(platformService.publishLowCodePage(id), "页面已发布"); }

    @GetMapping("/dashboards")
    public ApiResponse<List<Map<String, Object>>> dashboards() { return ApiResponse.ok(platformService.listDashboards()); }

    @PutMapping("/dashboards/{id}")
    public ApiResponse<Map<String, Object>> updateDashboard(@PathVariable long id, @RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin", "planner"); return ApiResponse.ok(platformService.saveDashboard(payload, id), "大屏配置已保存"); }

    @PostMapping("/dashboards")
    public ApiResponse<Map<String, Object>> createDashboard(@RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin", "planner"); return ApiResponse.ok(platformService.saveDashboard(payload, null), "大屏已创建"); }

    @PostMapping("/dashboards/{id}/publish")
    public ApiResponse<Map<String, Object>> publishDashboard(@PathVariable long id) { RequestContext.requireRole("admin", "planner"); return ApiResponse.ok(platformService.publishDashboard(id), "大屏已发布"); }

    @GetMapping("/data-sources")
    public ApiResponse<List<Map<String, Object>>> dataSources() { return ApiResponse.ok(platformService.listDataSources()); }

    @PostMapping("/data-sources")
    public ApiResponse<Map<String, Object>> createDataSource(@RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin"); return ApiResponse.ok(platformService.saveDataSource(payload, null), "数据源已创建"); }

    @PutMapping("/data-sources/{id}")
    public ApiResponse<Map<String, Object>> updateDataSource(@PathVariable long id, @RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin"); return ApiResponse.ok(platformService.saveDataSource(payload, id), "数据源已保存"); }

    @PostMapping("/data-sources/{id}/toggle")
    public ApiResponse<Map<String, Object>> toggleDataSource(@PathVariable long id, @RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin"); return ApiResponse.ok(platformService.toggleDataSource(id, Boolean.parseBoolean(String.valueOf(payload.getOrDefault("enabled", false)))), "数据源状态已更新"); }
}
