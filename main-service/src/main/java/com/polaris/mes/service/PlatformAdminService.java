package com.polaris.mes.service;

import java.util.List;
import java.util.Map;

/** Cross-tenant operations exposed only to the dedicated platform tenant. */
public interface PlatformAdminService {
    Map<String, Object> overview();
    List<Map<String, Object>> listTenants(String keyword);
    Map<String, Object> createTenant(Map<String, Object> payload);
    Map<String, Object> updateTenant(long tenantId, Map<String, Object> payload);
    List<Map<String, Object>> listFeatures();
    List<Map<String, Object>> listTenantFeatures(long tenantId);
    List<Map<String, Object>> updateTenantFeatures(long tenantId, List<Map<String, Object>> grants);
    Map<String, Object> billing(long tenantId);
    Map<String, Object> createBillingRecord(Map<String, Object> payload);
    Map<String, Object> points(long tenantId);
    Map<String, Object> adjustPoints(Map<String, Object> payload);
    Map<String, Object> traffic(long tenantId);
    Map<String, Object> allocateTraffic(Map<String, Object> payload);
    Map<String, Object> storage(long tenantId);
    Map<String, Object> allocateStorage(Map<String, Object> payload);
    Map<String, Object> recordStorageUsage(Map<String, Object> payload);
    List<Map<String, Object>> listTickets(String status);
    List<Map<String, Object>> ticketMessages(long ticketId);
    Map<String, Object> createTicket(Map<String, Object> payload);
    Map<String, Object> replyTicket(long ticketId, Map<String, Object> payload);
    List<Map<String, Object>> listCourses();
    Map<String, Object> saveCourse(Map<String, Object> payload, Long id);
    Map<String, Object> enrollCourse(long courseId, Map<String, Object> payload);
    List<Map<String, Object>> listCampaigns();
    Map<String, Object> saveCampaign(Map<String, Object> payload, Long id);
}
