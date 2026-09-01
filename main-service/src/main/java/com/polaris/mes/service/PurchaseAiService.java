package com.polaris.mes.service;

import java.util.Map;

public interface PurchaseAiService {
    Map<String, Object> context();

    Map<String, Object> parse(Map<String, Object> payload);

    Map<String, Object> validate(Map<String, Object> payload);

    Map<String, Object> confirm(Map<String, Object> payload, String idempotencyKey);
}
