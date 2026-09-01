package com.polaris.mes.service;

import java.util.List;
import java.util.Map;

public interface QualityService {
    Map<String, Object> summary();
    List<Map<String, Object>> listPlans(String status, String keyword);
    Map<String, Object> getPlan(long id);
    Map<String, Object> savePlan(Map<String, Object> payload, long id, String actor);
    List<Map<String, Object>> listLots(String status, String keyword, String inspectionType);
    Map<String, Object> getLot(long id);
    Map<String, Object> createLot(Map<String, Object> payload, String actor);
    Map<String, Object> startLot(long id, String actor);
    Map<String, Object> saveResults(long id, Map<String, Object> payload, String actor);
    Map<String, Object> completeLot(long id, String actor);
    List<Map<String, Object>> listNonconformances(String status, String keyword);
    Map<String, Object> createNonconformance(Map<String, Object> payload, String actor);
    Map<String, Object> updateDisposition(long id, Map<String, Object> payload, String actor);
    Map<String, Object> closeNonconformance(long id, Map<String, Object> payload, String actor);
    List<Map<String, Object>> listActions(long id);
    Map<String, Object> createAction(long id, Map<String, Object> payload);
    Map<String, Object> completeAction(long id, String actor);

    List<Map<String, Object>> listSupplierEvaluations(String status, String keyword);
    Map<String, Object> saveSupplierEvaluation(Map<String, Object> payload, long id, String actor);
    Map<String, Object> submitSupplierEvaluation(long id, String actor);
    List<Map<String, Object>> listAvl(String status, String keyword);
    Map<String, Object> saveAvl(Map<String, Object> payload, long id, String actor);
    Map<String, Object> updateAvlStatus(long id, Map<String, Object> payload, String actor);
    List<Map<String, Object>> listIpqc(String status, String keyword, String lineCode);
    Map<String, Object> getIpqc(long id);
    Map<String, Object> createIpqc(Map<String, Object> payload, String actor);
    Map<String, Object> saveIpqcResult(long id, Map<String, Object> payload, String actor);
}
