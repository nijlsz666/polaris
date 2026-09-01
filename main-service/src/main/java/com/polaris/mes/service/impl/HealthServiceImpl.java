package com.polaris.mes.service.impl;

import com.polaris.mes.service.HealthService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class HealthServiceImpl implements HealthService {
    private final JdbcTemplate jdbc;
    public HealthServiceImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Map<String, Object> readiness() { jdbc.queryForObject("select 1", Integer.class); return Map.of("status", "UP", "database", "UP", "service", "polaris-service"); }
}
