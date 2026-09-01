package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.DictionaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class DictionaryIntegrationTests {
    @Autowired DictionaryService dictionaries;

    @Test
    void dictionaryIsTenantScopedAndSeededForTheDefaultTenant() {
        TenantContext.Identity identity = new TenantContext.Identity(1, "demo", "华东一厂", 1, "admin", "admin");
        List<Map<String, Object>> rows = TenantContext.run(identity, () -> dictionaries.list("approval_action", "zh-CN"));
        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().anyMatch(row -> "APPROVED".equals(row.get("code"))));
    }
}
