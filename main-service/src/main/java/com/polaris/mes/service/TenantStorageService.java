package com.polaris.mes.service;

import java.util.Map;

/** Tenant storage quota and GB-month pricing management. */
public interface TenantStorageService {
    Map<String, Object> snapshot(long tenantId);
    Map<String, Object> storage(long tenantId);
    Map<String, Object> allocate(long tenantId, Map<String, Object> payload);
    Map<String, Object> recordUsage(long tenantId, Map<String, Object> payload);
}
