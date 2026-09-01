package com.polaris.mes.service;

import java.util.List;
import java.util.Map;

/** Platform application service for tenant, master-data, design and manufacturing use cases. */
public interface PlatformService {
    List<Map<String, Object>> listActiveTenants();
    Map<String, Object> findUser(String tenantCode, String username);
    Map<String, Object> findActiveUser(long tenantId, long userId, String username);
    Map<String, Object> registerTenant(Map<String, Object> payload, String passwordHash);
    void recordLogin(long tenantId, long userId);
    void updatePasswordHash(long tenantId, long userId, String passwordHash);
    Map<String, Object> overview();
    Map<String, Object> tenantProfile();
    Map<String, Object> updateTenantProfile(Map<String, Object> payload);
    List<Map<String, Object>> listReports();
    Map<String, Object> saveReport(Map<String, Object> payload, Long id);
    Map<String, Object> publishReport(long id);
    Map<String, Object> previewReport(long id);
    List<Map<String, Object>> listLowCodePages();
    Map<String, Object> saveLowCodePage(Map<String, Object> payload, Long id);
    Map<String, Object> publishLowCodePage(long id);
    List<Map<String, Object>> listDashboards();
    Map<String, Object> saveDashboard(Map<String, Object> payload, Long id);
    Map<String, Object> publishDashboard(long id);
    List<Map<String, Object>> listDataSources();
    Map<String, Object> saveDataSource(Map<String, Object> payload, Long id);
    Map<String, Object> toggleDataSource(long id, boolean enabled);
    List<Map<String, Object>> listBoms();
    int insertBom(Map<String, Object> payload);
    List<Map<String, Object>> listBomItems(long bomId);
    int publishBom(long bomId);
    Map<String, Object> calculateMrp(Map<String, Object> payload);
    List<Map<String, Object>> listWorkOrders();
    Map<String, Object> findWorkOrder(long id);
    int insertWorkOrder(Map<String, Object> payload);
    int reportWork(Long id, Map<String, Object> payload);
    List<Map<String, Object>> listMenus();
    int insertMenu(Map<String, Object> payload);
    int updateMenu(long id, Map<String, Object> payload);
    int deleteMenu(long id);
    List<Map<String, Object>> listUsers();
    List<Map<String, Object>> listRoles();
    Map<String, Object> insertRole(Map<String, Object> payload);
    Map<String, Object> updateRole(long id, Map<String, Object> payload);
    int deleteRole(long id);
    List<Map<String, Object>> updateRolePermissions(String roleCode, List<Map<String, Object>> permissions);
    List<Map<String, Object>> listPermissions(String roleCode);
    Map<String, Object> insertUser(Map<String, Object> payload, String passwordHash);
    Map<String, Object> updateUser(long id, Map<String, Object> payload);
    Map<String, Object> updateUserPassword(long id, String passwordHash);
}
