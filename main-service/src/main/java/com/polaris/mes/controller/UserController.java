package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class UserController {
    private final UserService userService;
    public UserController(UserService userService) { this.userService = userService; }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users() { RequestContext.requireRole("admin"); return ApiResponse.ok(userService.users()); }

    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> roles() { RequestContext.requireRole("admin"); return ApiResponse.ok(userService.roles()); }

    @GetMapping("/permissions")
    public ApiResponse<List<Map<String, Object>>> permissions(@RequestParam(defaultValue = "warehouse") String roleCode) {
        RequestContext.requireRole("admin"); return ApiResponse.ok(userService.permissions(roleCode));
    }

    @PostMapping("/users")
    public ApiResponse<Map<String, Object>> createUser(@RequestBody Map<String, Object> payload) {
        RequestContext.requireRole("admin");
        return ApiResponse.ok(userService.create(payload), "用户已创建");
    }

    @PutMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable long id, @RequestBody Map<String, Object> payload) {
        RequestContext.requireRole("admin");
        return ApiResponse.ok(userService.update(id, payload), "用户信息已更新");
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable long id, @RequestBody Map<String, Object> payload) {
        RequestContext.requireRole("admin");
        return ApiResponse.ok(userService.resetPassword(id, payload), "密码已重置");
    }

    @PostMapping("/roles")
    public ApiResponse<Map<String, Object>> createRole(@RequestBody Map<String, Object> payload) {
        RequestContext.requireRole("admin");
        return ApiResponse.ok(userService.createRole(payload), "角色已创建");
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<Map<String, Object>> updateRole(@PathVariable long id, @RequestBody Map<String, Object> payload) {
        RequestContext.requireRole("admin");
        return ApiResponse.ok(userService.updateRole(id, payload), "角色已更新");
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable long id) {
        RequestContext.requireRole("admin");
        userService.deleteRole(id);
        return ApiResponse.ok(null, "角色已删除");
    }

    @PutMapping("/roles/{roleCode}/permissions")
    public ApiResponse<List<Map<String, Object>>> updateRolePermissions(@PathVariable String roleCode, @RequestBody List<Map<String, Object>> permissions) {
        RequestContext.requireRole("admin");
        return ApiResponse.ok(userService.updateRolePermissions(roleCode, permissions), "角色权限已保存");
    }
}
