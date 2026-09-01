package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.service.PlatformService;
import com.polaris.mes.common.RequestContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menus")
public class MenuController {
    private final PlatformService platformService;
    public MenuController(PlatformService platformService) { this.platformService = platformService; }

    @GetMapping("/tree")
    public ApiResponse<List<Map<String, Object>>> tree() { return ApiResponse.ok(platformService.listMenus()); }

    @PostMapping
    public ApiResponse<Void> create(@RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin"); platformService.insertMenu(payload); return ApiResponse.ok(null, "菜单已创建"); }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody Map<String, Object> payload) { RequestContext.requireRole("admin"); platformService.updateMenu(id, payload); return ApiResponse.ok(null, "菜单已更新"); }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) { RequestContext.requireRole("admin"); platformService.deleteMenu(id); return ApiResponse.ok(null, "菜单已删除"); }
}
