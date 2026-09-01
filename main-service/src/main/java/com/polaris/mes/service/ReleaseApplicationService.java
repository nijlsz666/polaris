package com.polaris.mes.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface ReleaseApplicationService {
    Map<String, Object> overview();
    List<Map<String, Object>> list();
    Map<String, Object> detail(long id);
    Map<String, Object> generate(Map<String, Object> payload, String actor);
    Map<String, Object> verify(long id, Map<String, Object> payload, String actor);
    Map<String, Object> publish(long id, String actor);
    Path packagePath(long id);
}
