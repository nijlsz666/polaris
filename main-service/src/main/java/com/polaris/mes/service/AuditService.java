package com.polaris.mes.service;

import java.util.Map;

public interface AuditService {
    void record(String actor, String actionCode, String resourceType, String requestUri);
    Map<String, Object> page(String keyword, int page, int size);
}
