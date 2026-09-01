package com.polaris.mes.service;

import java.util.List;
import java.util.Map;

public interface UserService {
    List<Map<String, Object>> users();
    List<Map<String, Object>> roles();
    List<Map<String, Object>> permissions(String roleCode);
    Map<String, Object> create(Map<String, Object> payload);
    Map<String, Object> update(long id, Map<String, Object> payload);
    Map<String, Object> resetPassword(long id, Map<String, Object> payload);
    Map<String, Object> createRole(Map<String, Object> payload);
    Map<String, Object> updateRole(long id, Map<String, Object> payload);
    void deleteRole(long id);
    List<Map<String, Object>> updateRolePermissions(String roleCode, List<Map<String, Object>> permissions);
}
