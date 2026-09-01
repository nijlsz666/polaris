package com.polaris.mes.service;

import java.util.List;
import java.util.Map;

/**
 * Planning execution boundary.  MRP results, shortages, line-side calls and
 * supplier ASN documents are kept as business documents instead of being
 * calculated only in the browser.
 */
public interface MrpService {
    Map<String, Object> run(Map<String, Object> payload);
    List<Map<String, Object>> listRuns(String status);
    Map<String, Object> detailRun(long id);
    List<Map<String, Object>> listShortages(String status, String keyword);
    Map<String, Object> createPurchaseRequisition(long shortageId, Map<String, Object> payload);
    Map<String, Object> createMaterialCall(long shortageId, Map<String, Object> payload, String actor);
    List<Map<String, Object>> listMaterialCalls(String status, String keyword);
    Map<String, Object> transitionMaterialCall(long id, Map<String, Object> payload, String actor);
    List<Map<String, Object>> listAsns(String status, String keyword);
    Map<String, Object> createAsn(Map<String, Object> payload, String actor);
    Map<String, Object> detailAsn(long id);
    Map<String, Object> transitionAsn(long id, Map<String, Object> payload, String actor);
}
