package com.polaris.mes.service;

import java.util.List;
import java.util.Map;

/**
 * Manufacturing operations control-tower use cases.  The service owns the
 * lifecycle rules for incidents, corrective actions, equipment and downtime.
 */
public interface OperationsService {
    Map<String, Object> summary();
    List<Map<String, Object>> listEquipment(String status, String keyword);
    Map<String, Object> saveEquipment(Map<String, Object> payload);
    Map<String, Object> heartbeat(long id, Map<String, Object> payload);
    List<Map<String, Object>> listDowntime(String status, String equipmentCode);
    Map<String, Object> startDowntime(Map<String, Object> payload, String actor);
    Map<String, Object> resumeDowntime(long id, String actor);
    List<Map<String, Object>> listExceptions(String status, String priority, String keyword);
    Map<String, Object> getException(long id);
    Map<String, Object> createException(Map<String, Object> payload, String actor);
    Map<String, Object> transitionException(long id, Map<String, Object> payload, String actor);
    List<Map<String, Object>> listActions(long exceptionId);
    Map<String, Object> createAction(long exceptionId, Map<String, Object> payload, String actor);
    Map<String, Object> completeAction(long actionId, String actor);
}
