package com.polaris.mes.service;

import java.util.List;
import java.util.Map;

public interface AuthService {
    List<Map<String, Object>> tenants();
    Map<String, Object> login(Map<String, Object> payload);
    Map<String, Object> register(Map<String, Object> payload);
}
