package com.polaris.bpm.service;

import java.util.List;
import java.util.Map;

public interface ProcessInstanceApplicationService {
    Map<String, Object> start(Map<String, Object> payload, String actor);
    List<Map<String, Object>> listInstances(String status, String starter);
    Map<String, Object> detail(String instanceId);
    void cancel(String instanceId, String actor);
    void suspend(String instanceId, String actor);
    void resume(String instanceId, String actor);
}
