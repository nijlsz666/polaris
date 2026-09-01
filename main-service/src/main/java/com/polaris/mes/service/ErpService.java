package com.polaris.mes.service;

import java.util.List;
import java.util.Map;

/**
 * Shared ERP business-record boundary for sales, procurement, finance and
 * master-data workflows. Domain-specific tables can be introduced later
 * without changing the frontend contract exposed here.
 */
public interface ErpService {
    Map<String, Object> overview();
    List<Map<String, Object>> listRecords(String domain, String recordType, String keyword, String status);
    List<Map<String, Object>> listRecords(String domain, String recordType, String keyword, String status, String scope, String actor);
    Map<String, Object> createRecord(String domain, Map<String, Object> payload);
    Map<String, Object> saveDraft(String domain, Map<String, Object> payload);
    Map<String, Object> detailRecord(String domain, long id);
    Map<String, Object> detailRecord(long id);
    Map<String, Object> transition(String domain, long id, Map<String, Object> payload);
}
