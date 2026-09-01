package com.polaris.mes;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.ErpService;
import com.polaris.mes.service.InformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class InformationIntegrationTests {
    @Autowired InformationService information;
    @Autowired ErpService erp;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("delete from erp_business_record_attachment where tenant_id=1");
        jdbc.execute("delete from erp_business_record where tenant_id=1 and record_no like 'TEST-INFO-%'");
        jdbc.execute("delete from tenant_document where tenant_id=1 and title like 'TEST-INFO-%'");
        jdbc.execute("delete from platform_announcement_attachment where announcement_id in (select id from platform_announcement where title like 'TEST-INFO-%')");
        jdbc.execute("delete from platform_announcement where title like 'TEST-INFO-%'");
    }

    @Test
    void platformAnnouncementIsVisibleToTenantsAndRecordAttachmentsRespectOwnership() {
        long platformTenantId = jdbc.queryForObject("select id from sys_tenant where tenant_code='polaris-admin'", Long.class);
        TenantContext.Identity platformAdmin = new TenantContext.Identity(platformTenantId, "polaris-admin", "Polaris 总管理员", 1, "platform-admin", "platform_admin");
        Map<String, Object> announcement = TenantContext.run(platformAdmin, () -> information.saveAnnouncement(Map.of(
                "title", "TEST-INFO-公告", "summary", "测试摘要", "content", "测试正文", "status", "PUBLISHED"), null));
        TenantContext.run(platformAdmin, () -> information.uploadAnnouncementAttachments(((Number) announcement.get("id")).longValue(), new MockMultipartFile[]{
                new MockMultipartFile("files", "notice.txt", "text/plain", "公告附件".getBytes(StandardCharsets.UTF_8))}));

        TenantContext.Identity planner = new TenantContext.Identity(1, "demo", "华东一厂", 1, "planner", "planner");
        TenantContext.Identity otherUser = new TenantContext.Identity(1, "demo", "华东一厂", 2, "worker", "operator");
        TenantContext.run(planner, () -> {
            assertEquals(1, information.listAnnouncements().size());
            information.uploadDocument(new MockMultipartFile("file", "guide.txt", "text/plain", "资料".getBytes(StandardCharsets.UTF_8)), "TEST-INFO-资料", "GENERAL", "说明");
            Map<String, Object> record = erp.createRecord("sales", Map.of("type", "orders", "no", "TEST-INFO-SO-001", "name", "测试单据", "amount", "100"));
            information.uploadRecordAttachment("sales", ((Number) record.get("id")).longValue(), new MockMultipartFile("file", "order.txt", "text/plain", "单据".getBytes(StandardCharsets.UTF_8)));
            assertEquals(1, information.listRecordAttachments("sales", ((Number) record.get("id")).longValue()).size());
        });
        long recordId = jdbc.queryForObject("select id from erp_business_record where record_no='TEST-INFO-SO-001'", Long.class);
        TenantContext.run(otherUser, () -> assertThrows(IllegalArgumentException.class, () -> information.listRecordAttachments("sales", recordId)));
        TenantContext.Identity tenantAdmin = new TenantContext.Identity(1, "demo", "华东一厂", 3, "admin", "admin");
        TenantContext.run(tenantAdmin, () -> assertEquals(1, information.listRecordAttachments("sales", recordId).size()));
    }
}
