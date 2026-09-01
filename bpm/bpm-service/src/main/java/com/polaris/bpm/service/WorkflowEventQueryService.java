package com.polaris.bpm.service;

import java.util.List;
import java.util.Map;

public interface WorkflowEventQueryService {
    List<Map<String, Object>> list(String instanceId, int limit);
}
