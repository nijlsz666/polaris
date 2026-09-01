package com.polaris.mes.config;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.BpmService;
import com.polaris.mes.service.PlatformService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BpmBootstrap implements CommandLineRunner {
    private final BpmService bpmService;
    private final PlatformService platform;

    public BpmBootstrap(BpmService bpmService, PlatformService platform) {
        this.bpmService = bpmService;
        this.platform = platform;
    }

    @Override
    public void run(String... args) {
        for (Map<String, Object> tenant : platform.listActiveTenants()) {
            TenantContext.Identity system = new TenantContext.Identity(
                    ((Number) tenant.get("id")).longValue(), String.valueOf(tenant.get("tenant_code")),
                    String.valueOf(tenant.get("tenant_name")), 0, "system", "admin");
            TenantContext.run(system, () -> {
                bpmService.ensureTenantProcessDefinition();
                if (bpmService.listForms().isEmpty()) {
                    bpmService.createForm(Map.of(
                            "formCode", "FORM-WORK-ORDER-APPROVAL",
                            "formName", "工单发布审批表",
                            "businessType", "WORK_ORDER",
                            "schemaJson", "{\"fields\":[{\"name\":\"orderNo\",\"label\":\"工单号\",\"type\":\"text\",\"required\":true},{\"name\":\"planQty\",\"label\":\"计划数量\",\"type\":\"number\",\"required\":true},{\"name\":\"remark\",\"label\":\"申请说明\",\"type\":\"textarea\"}]}",
                            "status", "PUBLISHED"), "system");
                }
            });
        }
    }
}
