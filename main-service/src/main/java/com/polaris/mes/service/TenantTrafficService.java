package com.polaris.mes.service;

import java.util.Map;

/** Tenant traffic quota management and byte-level consumption accounting. */
public interface TenantTrafficService {
    boolean isBillableTenant(long tenantId);
    void requireAvailable(long tenantId);
    Map<String, Object> consume(long tenantId, long bytes);
    Map<String, Object> snapshot(long tenantId);
    Map<String, Object> traffic(long tenantId);
    Map<String, Object> allocate(long tenantId, Map<String, Object> payload);
}
