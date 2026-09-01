package com.polaris.mes.service.impl;

import com.polaris.mes.security.PasswordHasher;
import com.polaris.mes.service.PlatformService;
import com.polaris.mes.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {
    private final PlatformService platform;
    private final PasswordHasher passwordHasher;
    public UserServiceImpl(PlatformService platform, PasswordHasher passwordHasher) { this.platform = platform; this.passwordHasher = passwordHasher; }
    @Override public List<Map<String, Object>> users() { return platform.listUsers(); }
    @Override public List<Map<String, Object>> roles() { return platform.listRoles(); }
    @Override public List<Map<String, Object>> permissions(String roleCode) { return platform.listPermissions(roleCode); }
    @Override @Transactional public Map<String, Object> create(Map<String, Object> payload) { return platform.insertUser(payload, hash(payload)); }
    @Override @Transactional public Map<String, Object> update(long id, Map<String, Object> payload) { return platform.updateUser(id, payload); }
    @Override @Transactional public Map<String, Object> resetPassword(long id, Map<String, Object> payload) { return platform.updateUserPassword(id, hash(payload)); }
    @Override @Transactional public Map<String, Object> createRole(Map<String, Object> payload) { return platform.insertRole(payload); }
    @Override @Transactional public Map<String, Object> updateRole(long id, Map<String, Object> payload) { return platform.updateRole(id, payload); }
    @Override @Transactional public void deleteRole(long id) { platform.deleteRole(id); }
    @Override @Transactional public List<Map<String, Object>> updateRolePermissions(String roleCode, List<Map<String, Object>> permissions) { return platform.updateRolePermissions(roleCode, permissions); }
    private String hash(Map<String, Object> payload) { String password = String.valueOf(payload.getOrDefault("password", "")); if (password.length() < 8) throw new IllegalArgumentException("密码至少需要 8 位"); return passwordHasher.hash(password); }
}
