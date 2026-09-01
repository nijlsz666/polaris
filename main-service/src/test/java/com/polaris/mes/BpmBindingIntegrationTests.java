package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.BpmService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BpmBindingIntegrationTests {
    private static final TenantContext.Identity DEMO = new TenantContext.Identity(1, "demo", "华东一厂", 1, "admin", "admin");
    private static final TenantContext.Identity OTHER_TENANT = new TenantContext.Identity(2, "other", "另一租户", 2, "admin", "admin");

    @Autowired BpmService bpm;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from bpm_process_binding where business_function='sales'");
    }

    @AfterEach
    void cleanAfter() {
        jdbc.update("delete from bpm_process_binding where business_function='sales'");
    }

    @Test
    void bindingPersistsPerTenantAndRejectsUnknownProcess() {
        TenantContext.run(DEMO, () -> bpm.ensureTenantProcessDefinition());

        Map<String, Object> saved = TenantContext.run(DEMO,
                () -> bpm.bindProcess("sales", Map.of("processCode", "workOrderApproval"), "admin"));

        assertEquals("sales", saved.get("business_function"));
        assertEquals("workOrderApproval", saved.get("process_code"));
        assertEquals("workOrderApproval", TenantContext.run(DEMO, () -> bpm.listBindings().get(0).get("process_code")));
        assertTrue(TenantContext.run(OTHER_TENANT, () -> bpm.listBindings().isEmpty()));
        assertThrows(IllegalArgumentException.class, () -> TenantContext.run(DEMO,
                () -> bpm.bindProcess("sales", Map.of("processCode", "does-not-exist"), "admin")));
    }
}
