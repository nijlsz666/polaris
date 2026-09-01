package com.polaris.bpm.service;

import java.util.List;
import java.util.Map;

public interface BpmFormDefinitionApplicationService {
    List<Map<String, Object>> listForms();
    void createForm(Map<String, Object> payload, String actor);
    void updateForm(String code, Map<String, Object> payload, String actor);
}
