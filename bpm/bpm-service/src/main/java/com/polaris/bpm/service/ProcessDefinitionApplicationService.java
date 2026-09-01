package com.polaris.bpm.service;

import java.util.List;
import java.util.Map;

public interface ProcessDefinitionApplicationService {
    List<Map<String, Object>> listDefinitions();
    List<Map<String, Object>> listDesigns();
    Map<String, Object> getDesign(String code);
    Map<String, Object> saveDesign(String code, Map<String, Object> payload, String actor, String status);
    Map<String, Object> publishDesign(String code, Map<String, Object> payload, String actor);
    Map<String, Object> validateDesignPayload(Map<String, Object> payload);
    Map<String, Object> createDefinition(Map<String, Object> payload);
    void toggleDefinition(String id, boolean suspended);
}
