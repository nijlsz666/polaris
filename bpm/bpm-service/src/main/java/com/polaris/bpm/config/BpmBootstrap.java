package com.polaris.bpm.config;

import com.polaris.bpm.service.BpmService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BpmBootstrap implements CommandLineRunner {
    private final BpmService service;
    public BpmBootstrap(BpmService service) { this.service = service; }

    @Override
    public void run(String... args) {
        if (service.listForms().isEmpty()) {
            service.createForm(Map.of(
                    "formCode", "FORM-WORK-ORDER-APPROVAL",
                    "formName", "工单发布审批表",
                    "businessType", "WORK_ORDER",
                    "schemaJson", "{\"fields\":[{\"name\":\"orderNo\",\"label\":\"工单号\",\"type\":\"text\",\"required\":true},{\"name\":\"planQty\",\"label\":\"计划数量\",\"type\":\"number\",\"required\":true},{\"name\":\"remark\",\"label\":\"申请说明\",\"type\":\"textarea\"}]}",
                    "status", "PUBLISHED"), "system");
        }
    }
}
