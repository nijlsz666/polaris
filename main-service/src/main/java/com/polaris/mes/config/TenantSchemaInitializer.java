package com.polaris.mes.config;

import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * Keeps an existing 0.1 database upgradeable while schema.sql remains the
 * canonical definition for new installations.
 */
@Component("tenantSchemaInitializer")
public class TenantSchemaInitializer {
    private final JdbcTemplate jdbc;
    @Value("${polaris.schema.bootstrap-warehouse:false}")
    private boolean bootstrapWarehouse;

    private static final List<String> TENANT_TABLES = List.of(
            "sys_user", "sys_role", "sys_menu", "sys_permission", "bom", "bom_item",
            "production_plan", "work_order", "mrp_run", "mrp_requirement", "mrp_shortage", "material_call", "asn", "asn_line",
            "material_transaction", "inventory", "barcode",
            "qm_inspection_plan", "qm_inspection_plan_item", "qm_inspection_lot", "qm_inspection_result",
            "qm_nonconformance", "qm_corrective_action", "qm_supplier_evaluation", "qm_avl_entry", "qm_ipqc_record",
            "report_definition", "lowcode_page", "dashboard_config", "data_source_config", "audit_log", "platform_notification",
            "release_version", "release_verification", "mfg_equipment", "mfg_downtime_event",
            "mfg_exception", "mfg_exception_action", "erp_business_record", "erp_business_record_line");

    public TenantSchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void migrate() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_tenant (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "tenant_code VARCHAR(64) NOT NULL UNIQUE, " +
                "tenant_name VARCHAR(120) NOT NULL, " +
                "status TINYINT NOT NULL DEFAULT 1, " +
                "settings_json TEXT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        ensureDefaultTenant();
        ensureDictionarySchema();
        ensureTenantSaasColumns();
        ensureAccessSchema();
        ensureUserSchema();
        ensurePlatformAdminSchema();
        ensureInformationSchema();
        ensureManufacturingSchema();
        for (String table : TENANT_TABLES) {
            if (tableExists(table) && !columnExists(table, "tenant_id")) {
                jdbc.execute("ALTER TABLE " + table + " ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1");
            }
        }
        migrateTenantUniqueKeys();
        ensureReleaseTables();
        ensureWarehouseSchema();
        ensureQualitySchema();
        ensurePlatformSchema();
        ensureDataSourceSchema();
        ensureOperationsSchema();
        ensureErpSchema();
    }

    public boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables where lower(table_name)=lower(?)", Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.columns where lower(table_name)=lower(?) and lower(column_name)=lower(?)",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private void ensureDefaultTenant() {
        try {
            jdbc.update("insert into sys_tenant(id, tenant_code, tenant_name, status) values(1, 'demo', '华东一厂', 1)");
        } catch (DataAccessException ignored) {
            // The seed already exists. Do not overwrite tenant administration data.
        }
    }

    private void ensureTenantSaasColumns() {
        ensureColumn("sys_tenant", "tenant_type", "VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER'");
        ensureColumn("sys_tenant", "plan_code", "VARCHAR(32) NOT NULL DEFAULT 'STARTER'");
        ensureColumn("sys_tenant", "contact_name", "VARCHAR(100)");
        ensureColumn("sys_tenant", "contact_email", "VARCHAR(160)");
        ensureColumn("sys_tenant", "trial_ends_at", "TIMESTAMP NULL");
        ensureColumn("sys_tenant", "max_users", "INT NOT NULL DEFAULT 10");
    }

    /** Creates the isolated platform tenant and the platform-operations model. */
    private void ensurePlatformAdminSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS platform_feature (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, feature_code VARCHAR(80) NOT NULL UNIQUE, " +
                "feature_name VARCHAR(120) NOT NULL, category VARCHAR(40) NOT NULL, description VARCHAR(255), " +
                "sort_no INT NOT NULL DEFAULT 99, status TINYINT NOT NULL DEFAULT 1, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_feature_grant (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, feature_code VARCHAR(80) NOT NULL, " +
                "status TINYINT NOT NULL DEFAULT 1, quota_json TEXT NULL, expires_at TIMESTAMP NULL, " +
                "granted_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, feature_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_billing_account (" +
                "tenant_id BIGINT PRIMARY KEY, currency_code VARCHAR(10) NOT NULL DEFAULT 'CNY', " +
                "balance DECIMAL(18,2) NOT NULL DEFAULT 0, total_paid DECIMAL(18,2) NOT NULL DEFAULT 0, " +
                "total_consumed DECIMAL(18,2) NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_billing_record (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, record_type VARCHAR(30) NOT NULL, " +
                "amount DECIMAL(18,2) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED', description VARCHAR(255) NOT NULL, " +
                "period_start DATE NULL, period_end DATE NULL, created_by VARCHAR(64) NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_points_account (" +
                "tenant_id BIGINT PRIMARY KEY, balance BIGINT NOT NULL DEFAULT 0, total_earned BIGINT NOT NULL DEFAULT 0, " +
                "total_spent BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_points_ledger (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, change_amount BIGINT NOT NULL, " +
                "balance_after BIGINT NOT NULL, reason VARCHAR(255) NOT NULL, reference_type VARCHAR(40), " +
                "reference_id VARCHAR(80), created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_traffic_account (" +
                "tenant_id BIGINT PRIMARY KEY, quota_bytes BIGINT NOT NULL DEFAULT 0, used_bytes BIGINT NOT NULL DEFAULT 0, " +
                "warning_threshold_percent INT NOT NULL DEFAULT 10, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_traffic_ledger (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, action_type VARCHAR(20) NOT NULL, " +
                "change_bytes BIGINT NOT NULL DEFAULT 0, consumed_bytes BIGINT NOT NULL DEFAULT 0, " +
                "quota_after_bytes BIGINT NOT NULL, used_after_bytes BIGINT NOT NULL, description VARCHAR(255) NOT NULL, " +
                "created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_storage_account (" +
                "tenant_id BIGINT PRIMARY KEY, quota_bytes BIGINT NOT NULL DEFAULT 0, used_bytes BIGINT NOT NULL DEFAULT 0, " +
                "warning_threshold_percent INT NOT NULL DEFAULT 10, unit_price_per_gb_month DECIMAL(18,4) NOT NULL DEFAULT 0, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_storage_ledger (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, action_type VARCHAR(20) NOT NULL, " +
                "change_bytes BIGINT NOT NULL DEFAULT 0, consumed_bytes BIGINT NOT NULL DEFAULT 0, " +
                "quota_after_bytes BIGINT NOT NULL, used_after_bytes BIGINT NOT NULL, description VARCHAR(255) NOT NULL, " +
                "created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS platform_service_ticket (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, category VARCHAR(30) NOT NULL DEFAULT 'QUESTION', " +
                "subject VARCHAR(180) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'OPEN', priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL', " +
                "created_by VARCHAR(100) NOT NULL, assignee VARCHAR(100), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS platform_service_message (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, ticket_id BIGINT NOT NULL, sender_type VARCHAR(20) NOT NULL, " +
                "sender_name VARCHAR(100) NOT NULL, content TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS platform_training_course (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, course_code VARCHAR(80) NOT NULL UNIQUE, course_name VARCHAR(180) NOT NULL, " +
                "category VARCHAR(40) NOT NULL DEFAULT 'PRODUCT', instructor VARCHAR(100), schedule_at TIMESTAMP NULL, " +
                "capacity INT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', description VARCHAR(500), " +
                "created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS platform_training_enrollment (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, course_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, " +
                "contact_name VARCHAR(100), status VARCHAR(20) NOT NULL DEFAULT 'ENROLLED', enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(course_id, tenant_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS platform_marketing_campaign (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, campaign_code VARCHAR(80) NOT NULL UNIQUE, campaign_name VARCHAR(180) NOT NULL, " +
                "campaign_type VARCHAR(40) NOT NULL DEFAULT 'ANNOUNCEMENT', audience VARCHAR(40) NOT NULL DEFAULT 'ALL_CUSTOMERS', " +
                "content TEXT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', starts_at TIMESTAMP NULL, ends_at TIMESTAMP NULL, " +
                "created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        long platformTenantId;
        try {
            jdbc.update("insert into sys_tenant(tenant_code, tenant_name, tenant_type, plan_code, max_users, status) values(?,?,?,?,?,1)",
                    "polaris-admin", "Polaris 总管理员", "PLATFORM", "PLATFORM", 100);
        } catch (DataAccessException ignored) {
            // Idempotent bootstrap for existing installations.
        }
        platformTenantId = jdbc.queryForObject("select id from sys_tenant where tenant_code=?", Long.class, "polaris-admin");
        jdbc.update("update sys_tenant set tenant_type='PLATFORM', plan_code='PLATFORM', max_users=100, status=1 where id=?", platformTenantId);
        try {
            jdbc.update("insert into sys_role(tenant_id, role_code, role_name, description, status) values(?,?,?,?,1)",
                    platformTenantId, "platform_admin", "总管理员", "跨租户维护租户、授权、计费、积分、服务、培训与营销");
        } catch (DataAccessException ignored) {}
        try {
            jdbc.update("insert into sys_user(tenant_id, username, display_name, password_hash, status, role_code) values(?,?,?,?,1,?)",
                    platformTenantId, "platform-admin", "平台总管理员", "pbkdf2$210000$cG9sYXJpcy1kZW1vLWFkbWlu$8iEJPJBh5WRbq-zrpztf2zBZJQ02IMcpwDUN9m5k0Nc", "platform_admin");
        } catch (DataAccessException ignored) {}
        seedPlatformFeatures(platformTenantId);
    }

    /** Global announcements and tenant-scoped document/ERP attachment metadata. */
    private void ensureInformationSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS platform_announcement (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(180) NOT NULL, summary VARCHAR(500), " +
                "content TEXT NOT NULL, cover_image_url VARCHAR(500), status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', " +
                "publish_at TIMESTAMP NULL, created_by VARCHAR(64) NOT NULL, updated_by VARCHAR(64) NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS platform_announcement_attachment (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, announcement_id BIGINT NOT NULL, original_name VARCHAR(255) NOT NULL, " +
                "storage_path VARCHAR(500) NOT NULL, content_type VARCHAR(160), file_size BIGINT NOT NULL DEFAULT 0, " +
                "created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant_document (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, title VARCHAR(180) NOT NULL, " +
                "category VARCHAR(60) NOT NULL DEFAULT 'GENERAL', description VARCHAR(500), original_name VARCHAR(255) NOT NULL, " +
                "storage_path VARCHAR(500) NOT NULL, content_type VARCHAR(160), file_size BIGINT NOT NULL DEFAULT 0, " +
                "uploaded_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS erp_business_record_attachment (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, record_id BIGINT NOT NULL, " +
                "domain VARCHAR(30) NOT NULL, original_name VARCHAR(255) NOT NULL, storage_path VARCHAR(500) NOT NULL, " +
                "content_type VARCHAR(160), file_size BIGINT NOT NULL DEFAULT 0, created_by VARCHAR(64) NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        try { jdbc.execute("CREATE INDEX idx_announcement_attachment_announcement ON platform_announcement_attachment(announcement_id)"); }
        catch (DataAccessException ignored) { }
        try { jdbc.execute("CREATE INDEX idx_tenant_document_tenant ON tenant_document(tenant_id, category, created_at)"); }
        catch (DataAccessException ignored) { }
        try { jdbc.execute("CREATE INDEX idx_erp_record_attachment_record ON erp_business_record_attachment(tenant_id, domain, record_id)"); }
        catch (DataAccessException ignored) { }
    }

    private void seedPlatformFeatures(long platformTenantId) {
        List<String[]> features = List.of(
                new String[]{"WORKSPACE", "工作台", "产品功能", "企业工作台与基础组织管理", "10"},
                new String[]{"ERP", "经营管理", "产品功能", "销售、采购、财务与主数据", "20"},
                new String[]{"MFG", "制造管理", "产品功能", "BOM、计划、工单与现场执行", "30"},
                new String[]{"WMS", "仓储管理", "产品功能", "收发料、库存、批次与条码", "40"},
                new String[]{"QUALITY", "质量管理", "产品功能", "检验、不合格与整改闭环", "50"},
                new String[]{"BPM", "审批中心", "产品功能", "业务流程与审批协同", "60"},
                new String[]{"DESIGN", "设计中心", "产品功能", "报表、低代码与大屏", "70"},
                new String[]{"API", "开放接口", "增值服务", "开放 API 与数据集成能力", "80"});
        for (String[] feature : features) {
            try {
                jdbc.update("insert into platform_feature(feature_code, feature_name, category, description, sort_no, status) values(?,?,?,?,?,1)",
                        feature[0], feature[1], feature[2], feature[3], Integer.parseInt(feature[4]));
            } catch (DataAccessException ignored) {}
        }
        List<Map<String, Object>> customerTenants = jdbc.queryForList("select id from sys_tenant where tenant_type='CUSTOMER' and status=1");
        for (Map<String, Object> tenant : customerTenants) {
            ensureCustomerAccounts(((Number) tenant.get("id")).longValue());
            for (String[] feature : features) {
                try {
                    jdbc.update("insert into tenant_feature_grant(tenant_id, feature_code, status, quota_json, granted_by) values(?,?,1,'{}',?)",
                            tenant.get("id"), feature[0], "bootstrap");
                } catch (DataAccessException ignored) {}
            }
        }
        ensureCustomerAccounts(platformTenantId);
    }

    private void ensureCustomerAccounts(long tenantId) {
        try { jdbc.update("insert into tenant_billing_account(tenant_id) values(?)", tenantId); } catch (DataAccessException ignored) {}
        try { jdbc.update("insert into tenant_points_account(tenant_id) values(?)", tenantId); } catch (DataAccessException ignored) {}
        try { jdbc.update("insert into tenant_traffic_account(tenant_id, quota_bytes) values(?, 1073741824)", tenantId); } catch (DataAccessException ignored) {}
        try { jdbc.update("insert into tenant_storage_account(tenant_id, quota_bytes) values(?, 10737418240)", tenantId); } catch (DataAccessException ignored) {}
    }

    private void ensurePlatformSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS audit_log (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, actor VARCHAR(100) NOT NULL, " +
                "action_code VARCHAR(100) NOT NULL, resource_type VARCHAR(30) NOT NULL, resource_id VARCHAR(64), " +
                "request_uri VARCHAR(255), request_body TEXT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS platform_notification (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, user_id BIGINT NULL, " +
                "notification_type VARCHAR(30) NOT NULL DEFAULT 'SYSTEM', title VARCHAR(160) NOT NULL, " +
                "content VARCHAR(500) NOT NULL, level VARCHAR(20) NOT NULL DEFAULT 'INFO', action_url VARCHAR(255), " +
                "read_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    private void ensureDataSourceSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS data_source_config (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, source_code VARCHAR(80) NOT NULL, " +
                "source_name VARCHAR(160) NOT NULL, source_type VARCHAR(30) NOT NULL DEFAULT 'SQL', config_json TEXT NOT NULL, " +
                "schema_json TEXT NOT NULL, status TINYINT NOT NULL DEFAULT 1, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, source_code))");
        List<Map<String, Object>> tenants = jdbc.queryForList("select id from sys_tenant where status=1");
        for (Map<String, Object> tenant : tenants) {
            try { jdbc.update("insert into data_source_config(tenant_id, source_code, source_name, source_type, config_json, schema_json, status) values(?,?,?,?,?,?,1)", tenant.get("id"), "work_order", "生产工单", "TABLE", "{}", "{\"columns\":[\"status\",\"plan_qty\",\"completed_qty\"]}"); } catch (DataAccessException ignored) {}
            try { jdbc.update("insert into data_source_config(tenant_id, source_code, source_name, source_type, config_json, schema_json, status) values(?,?,?,?,?,?,1)", tenant.get("id"), "inventory", "库存明细", "TABLE", "{}", "{\"columns\":[\"material_code\",\"material_name\",\"available_qty\",\"safety_stock\"]}"); } catch (DataAccessException ignored) {}
        }
    }

    private void ensureDictionarySchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_dictionary (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, dict_type VARCHAR(80) NOT NULL, " +
                "dict_code VARCHAR(80) NOT NULL, dict_label VARCHAR(160) NOT NULL, dict_value VARCHAR(160) NOT NULL, " +
                "locale VARCHAR(20) NOT NULL DEFAULT 'zh-CN', sort_no INT NOT NULL DEFAULT 99, status TINYINT NOT NULL DEFAULT 1, " +
                "metadata_json TEXT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, dict_type, dict_code, locale))");
        ensureColumn("sys_dictionary", "parent_id", "BIGINT NOT NULL DEFAULT 0");
        List<Map<String, Object>> tenants = jdbc.queryForList("select id from sys_tenant where status=1");
        List<String[]> seed = List.of(
                new String[]{"APPROVAL_ACTION", "APPROVED", "同意", "同意", "1"}, new String[]{"APPROVAL_ACTION", "REJECTED", "驳回", "驳回", "2"},
                new String[]{"APPROVAL_ACTION", "TRANSFERRED", "转办", "转办", "3"}, new String[]{"BUSINESS_STATUS", "REVIEW", "待评审", "待评审", "1"},
                new String[]{"BUSINESS_STATUS", "CONFIRMED", "已确认", "已确认", "2"}, new String[]{"BUSINESS_STATUS", "COMPLETED", "已完成", "已完成", "9"},
                new String[]{"COMMON_STATUS", "ACTIVE", "已启用", "ACTIVE", "1"}, new String[]{"COMMON_STATUS", "INACTIVE", "已停用", "INACTIVE", "2"});
        for (Map<String, Object> tenant : tenants) for (String[] item : seed) {
            try { jdbc.update("insert into sys_dictionary(tenant_id,dict_type,dict_code,dict_label,dict_value,locale,sort_no,status) values(?,?,?,?,?,'zh-CN',?,1)", tenant.get("id"), item[0], item[1], item[2], item[3], Integer.parseInt(item[4])); }
            catch (DataAccessException ignored) { /* idempotent seed */ }
        }
    }

    private void ensureAccessSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_role (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, role_code VARCHAR(64) NOT NULL, " +
                "role_name VARCHAR(100) NOT NULL, description VARCHAR(255), status TINYINT NOT NULL DEFAULT 1, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, role_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_permission (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, role_code VARCHAR(64) NOT NULL, " +
                "resource_type VARCHAR(20) NOT NULL, resource_code VARCHAR(150) NOT NULL, action_code VARCHAR(50) NOT NULL DEFAULT 'VIEW', " +
                "field_mask_json TEXT NULL, effect VARCHAR(10) NOT NULL DEFAULT 'ALLOW', " +
                "UNIQUE(tenant_id, role_code, resource_type, resource_code, action_code))");
    }

    private void ensureUserSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_user (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, username VARCHAR(64) NOT NULL, " +
                "display_name VARCHAR(100) NOT NULL, password_hash VARCHAR(255) NOT NULL, status TINYINT NOT NULL DEFAULT 1, " +
                "role_code VARCHAR(64) NOT NULL DEFAULT 'operator', last_login_at TIMESTAMP NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, username))");
    }

    private void ensureManufacturingSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS bom (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, bom_code VARCHAR(64) NOT NULL, " +
                "product_code VARCHAR(64) NOT NULL, product_name VARCHAR(120) NOT NULL, version VARCHAR(30) NOT NULL DEFAULT 'V1', " +
                "status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', remark VARCHAR(255), deleted INT NOT NULL DEFAULT 0, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, bom_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS bom_item (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, bom_id BIGINT NOT NULL, " +
                "material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120) NOT NULL, quantity DECIMAL(18,6) NOT NULL DEFAULT 0, " +
                "unit VARCHAR(20) NOT NULL DEFAULT '件', loss_rate DECIMAL(8,4) NOT NULL DEFAULT 0, issue_method VARCHAR(30) NOT NULL DEFAULT 'PICK', " +
                "UNIQUE(tenant_id, bom_id, material_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS production_plan (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, plan_no VARCHAR(64) NOT NULL, " +
                "product_code VARCHAR(64) NOT NULL, product_name VARCHAR(120) NOT NULL, plan_qty INT NOT NULL DEFAULT 0, " +
                "released_qty INT NOT NULL DEFAULT 0, plan_date DATE NOT NULL, priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL', " +
                "status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, plan_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS work_order (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, order_no VARCHAR(64) NOT NULL, " +
                "product_code VARCHAR(64) NOT NULL, product_name VARCHAR(120) NOT NULL, plan_qty INT NOT NULL DEFAULT 0, " +
                "completed_qty INT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'PLANNED', planned_start TIMESTAMP NULL, " +
                "planned_end TIMESTAMP NULL, work_center VARCHAR(64), deleted INT NOT NULL DEFAULT 0, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, order_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mrp_run (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_no VARCHAR(100) NOT NULL, " +
                "product_code VARCHAR(64) NOT NULL, product_name VARCHAR(120) NOT NULL, plan_qty DECIMAL(18,6) NOT NULL DEFAULT 0, plan_date DATE NOT NULL, " +
                "bom_id BIGINT NOT NULL, bom_code VARCHAR(64) NOT NULL, bom_version VARCHAR(30) NOT NULL, source_type VARCHAR(40), source_doc_no VARCHAR(100), " +
                "priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL', status VARCHAR(20) NOT NULL DEFAULT 'CALCULATED', created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, run_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mrp_requirement (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, run_id BIGINT NOT NULL, line_no INT NOT NULL, material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120) NOT NULL, unit VARCHAR(20) NOT NULL DEFAULT '件', " +
                "quantity_per DECIMAL(18,6) NOT NULL DEFAULT 0, loss_rate DECIMAL(8,4) NOT NULL DEFAULT 0, gross_required_qty DECIMAL(18,6) NOT NULL DEFAULT 0, safety_stock_qty DECIMAL(18,6) NOT NULL DEFAULT 0, required_qty DECIMAL(18,6) NOT NULL DEFAULT 0, " +
                "available_qty DECIMAL(18,6) NOT NULL DEFAULT 0, reserved_qty DECIMAL(18,6) NOT NULL DEFAULT 0, locked_qty DECIMAL(18,6) NOT NULL DEFAULT 0, in_transit_qty DECIMAL(18,6) NOT NULL DEFAULT 0, open_po_qty DECIMAL(18,6) NOT NULL DEFAULT 0, covered_qty DECIMAL(18,6) NOT NULL DEFAULT 0, net_shortage_qty DECIMAL(18,6) NOT NULL DEFAULT 0, " +
                "due_date DATE NOT NULL, issue_method VARCHAR(30) NOT NULL DEFAULT 'PICK', shortage_status VARCHAR(20) NOT NULL DEFAULT 'OPEN', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, run_id, line_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mrp_shortage (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, shortage_no VARCHAR(100) NOT NULL, run_id BIGINT NOT NULL, requirement_id BIGINT NOT NULL, material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120) NOT NULL, unit VARCHAR(20) NOT NULL DEFAULT '件', " +
                "shortage_qty DECIMAL(18,6) NOT NULL DEFAULT 0, resolved_qty DECIMAL(18,6) NOT NULL DEFAULT 0, required_date DATE NOT NULL, priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL', source_type VARCHAR(40), source_doc_no VARCHAR(100), procurement_record_no VARCHAR(100), status VARCHAR(20) NOT NULL DEFAULT 'OPEN', owner_code VARCHAR(64), remark VARCHAR(500), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, shortage_no))");
        ensureColumn("mrp_shortage", "procurement_record_no", "VARCHAR(100)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS material_call (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, call_no VARCHAR(100) NOT NULL, shortage_id BIGINT NOT NULL, work_order_no VARCHAR(64), material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120) NOT NULL, unit VARCHAR(20) NOT NULL DEFAULT '件', " +
                "requested_qty DECIMAL(18,6) NOT NULL DEFAULT 0, issued_qty DECIMAL(18,6) NOT NULL DEFAULT 0, required_at TIMESTAMP NOT NULL, priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL', from_warehouse_code VARCHAR(64), to_warehouse_code VARCHAR(64), requested_by VARCHAR(64) NOT NULL, assigned_to VARCHAR(64), status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', remark VARCHAR(500), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, call_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS asn (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, asn_no VARCHAR(100) NOT NULL, purchase_order_no VARCHAR(100) NOT NULL, supplier_code VARCHAR(64), supplier_name VARCHAR(180) NOT NULL, expected_arrival DATE NOT NULL, warehouse_code VARCHAR(64) NOT NULL, carrier VARCHAR(120), tracking_no VARCHAR(120), status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', created_by VARCHAR(64) NOT NULL, submitted_at TIMESTAMP NULL, received_at TIMESTAMP NULL, remark VARCHAR(500), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, asn_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS asn_line (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, asn_id BIGINT NOT NULL, line_no INT NOT NULL, po_line_id BIGINT NULL, material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120) NOT NULL, unit VARCHAR(20) NOT NULL DEFAULT '件', planned_qty DECIMAL(18,6) NOT NULL DEFAULT 0, shipped_qty DECIMAL(18,6) NOT NULL DEFAULT 0, received_qty DECIMAL(18,6) NOT NULL DEFAULT 0, batch_no VARCHAR(64), production_date DATE NULL, expiry_date DATE NULL, quality_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', remark VARCHAR(500), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, asn_id, line_no))");
    }

    private void migrateTenantUniqueKeys() {
        ensureCompositeUnique("sys_user", "username", "uk_user_tenant_username", "tenant_id, username");
        ensureCompositeUnique("sys_role", "role_code", "uk_role_tenant_code", "tenant_id, role_code");
        ensureCompositeUnique("sys_menu", "menu_code", "uk_menu_tenant_code", "tenant_id, menu_code");
        ensureCompositeUnique("sys_permission", "uk_permission", "uk_permission_tenant", "tenant_id, role_code, resource_type, resource_code, action_code");
        ensureCompositeUnique("bom", "bom_code", "uk_bom_tenant_code", "tenant_id, bom_code");
        ensureCompositeUnique("production_plan", "plan_no", "uk_plan_tenant_no", "tenant_id, plan_no");
        ensureCompositeUnique("work_order", "order_no", "uk_work_order_tenant_no", "tenant_id, order_no");
        ensureCompositeUnique("material_transaction", "transaction_no", "uk_tx_tenant_no", "tenant_id, transaction_no");
        ensureCompositeUnique("inventory", "uk_inventory", "uk_inventory_tenant", "tenant_id, material_code, warehouse_code, location_code, batch_no");
        ensureCompositeUnique("barcode", "barcode", "uk_barcode_tenant_code", "tenant_id, barcode");
        ensureCompositeUnique("report_definition", "report_code", "uk_report_tenant_code", "tenant_id, report_code");
        ensureCompositeUnique("lowcode_page", "page_code", "uk_lowcode_tenant_code", "tenant_id, page_code");
        ensureCompositeUnique("dashboard_config", "dashboard_code", "uk_dashboard_tenant_code", "tenant_id, dashboard_code");
        ensureCompositeUnique("release_version", "release_no", "uk_release_tenant_no", "tenant_id, release_no");
        ensureCompositeUnique("release_version", "version", "uk_release_tenant_version", "tenant_id, version");
    }

    private void ensureCompositeUnique(String table, String oldIndex, String newIndex, String columns) {
        if (!tableExists(table) || indexExists(table, newIndex)) return;
        if (indexExists(table, oldIndex)) {
            try { jdbc.execute("ALTER TABLE " + table + " DROP INDEX " + oldIndex); }
            catch (DataAccessException ignored) { /* Existing constraint may use another generated name. */ }
        }
        if (!indexExists(table, oldIndex)) {
            try { jdbc.execute("ALTER TABLE " + table + " ADD UNIQUE KEY " + newIndex + "(" + columns + ")"); }
            catch (DataAccessException ignored) { /* A concurrent migration may have added it. */ }
        }
    }

    private boolean indexExists(String table, String index) {
        try {
            Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.indexes where lower(table_name)=lower(?) and lower(index_name)=lower(?)",
                    Integer.class, table, index);
            return count != null && count > 0;
        } catch (DataAccessException ignored) {
            // MySQL exposes the same metadata through STATISTICS; H2 uses INDEXES.
            Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.statistics where lower(table_name)=lower(?) and lower(index_name)=lower(?)",
                    Integer.class, table, index);
            return count != null && count > 0;
        }
    }

    private void ensureReleaseTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS release_version (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL DEFAULT 1, " +
                "release_no VARCHAR(64) NOT NULL, version VARCHAR(64) NOT NULL, " +
                "package_type VARCHAR(20) NOT NULL DEFAULT 'DATA', source_environment VARCHAR(30) NOT NULL DEFAULT 'TEST', " +
                "target_environment VARCHAR(30) NOT NULL DEFAULT 'PRODUCTION', package_name VARCHAR(180) NOT NULL, " +
                "package_path VARCHAR(500) NOT NULL, artifact_hash CHAR(64) NOT NULL, manifest_json TEXT NOT NULL, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'GENERATED', verification_status VARCHAR(20) NOT NULL DEFAULT 'NOT_VERIFIED', " +
                "verification_message VARCHAR(500), created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "published_at TIMESTAMP NULL, verified_at TIMESTAMP NULL, " +
                "UNIQUE(tenant_id, release_no), UNIQUE(tenant_id, version))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS release_verification (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL DEFAULT 1, release_id BIGINT NOT NULL, " +
                "environment VARCHAR(30) NOT NULL, expected_hash CHAR(64) NOT NULL, actual_hash CHAR(64) NOT NULL, " +
                "status VARCHAR(20) NOT NULL, details_json TEXT, verified_by VARCHAR(64) NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    /**
     * Adds the warehouse models to an already running Polaris database. The
     * project deliberately keeps this migration small and idempotent because
     * customer installations are initialized from schema.sql while older
     * installations are upgraded at application startup.
     */
    private void ensureWarehouseSchema() {
        if (bootstrapWarehouse) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS material_transaction (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, transaction_no VARCHAR(100) NOT NULL, " +
                    "transaction_type VARCHAR(30) NOT NULL, material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120), " +
                    "warehouse_code VARCHAR(64) NOT NULL, location_code VARCHAR(64), batch_no VARCHAR(64), quantity INT NOT NULL DEFAULT 0, " +
                    "unit VARCHAR(20) NOT NULL DEFAULT '件', operator_name VARCHAR(100) NOT NULL, source_doc_no VARCHAR(64), document_no VARCHAR(100), " +
                    "from_warehouse_code VARCHAR(64), from_location_code VARCHAR(64), to_warehouse_code VARCHAR(64), to_location_code VARCHAR(64), " +
                    "reason_code VARCHAR(64), idempotency_key VARCHAR(120), status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED', remark VARCHAR(255), " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, transaction_no), UNIQUE(tenant_id, idempotency_key))");
            jdbc.execute("CREATE TABLE IF NOT EXISTS inventory (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120) NOT NULL, " +
                    "warehouse_code VARCHAR(64) NOT NULL, location_code VARCHAR(64) NOT NULL, batch_no VARCHAR(64), available_qty INT NOT NULL DEFAULT 0, " +
                    "locked_qty INT NOT NULL DEFAULT 0, reserved_qty INT NOT NULL DEFAULT 0, in_transit_qty INT NOT NULL DEFAULT 0, unit VARCHAR(20) NOT NULL DEFAULT '件', " +
                    "safety_stock INT NOT NULL DEFAULT 0, stock_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', expiry_date DATE NULL, version_no BIGINT NOT NULL DEFAULT 0, " +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, material_code, warehouse_code, location_code, batch_no))");
            jdbc.execute("CREATE TABLE IF NOT EXISTS barcode (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, barcode VARCHAR(100) NOT NULL, barcode_type VARCHAR(30) NOT NULL DEFAULT 'MATERIAL', " +
                    "material_code VARCHAR(64), batch_no VARCHAR(64), status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', source_doc_no VARCHAR(64), " +
                    "warehouse_code VARCHAR(64), location_code VARCHAR(64), printed_count INT NOT NULL DEFAULT 0, voided_at TIMESTAMP NULL, printed_at TIMESTAMP NULL, " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, barcode))");
        }
        ensureColumn("material_transaction", "document_no", "VARCHAR(100)");
        ensureColumn("material_transaction", "from_warehouse_code", "VARCHAR(64)");
        ensureColumn("material_transaction", "from_location_code", "VARCHAR(64)");
        ensureColumn("material_transaction", "to_warehouse_code", "VARCHAR(64)");
        ensureColumn("material_transaction", "to_location_code", "VARCHAR(64)");
        ensureColumn("material_transaction", "reason_code", "VARCHAR(64)");
        ensureColumn("material_transaction", "idempotency_key", "VARCHAR(120)");
        ensureColumn("material_transaction", "status", "VARCHAR(20) DEFAULT 'COMPLETED'");

        ensureColumn("inventory", "reserved_qty", "INT NOT NULL DEFAULT 0");
        ensureColumn("inventory", "in_transit_qty", "INT NOT NULL DEFAULT 0");
        ensureColumn("inventory", "stock_status", "VARCHAR(20) DEFAULT 'AVAILABLE'");
        ensureColumn("inventory", "expiry_date", "DATE NULL");
        ensureColumn("inventory", "version_no", "BIGINT NOT NULL DEFAULT 0");

        ensureColumn("barcode", "warehouse_code", "VARCHAR(64)");
        ensureColumn("barcode", "location_code", "VARCHAR(64)");
        ensureColumn("barcode", "printed_count", "INT NOT NULL DEFAULT 0");
        ensureColumn("barcode", "voided_at", "TIMESTAMP NULL");

        jdbc.execute("CREATE TABLE IF NOT EXISTS wh_warehouse (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, " +
                "warehouse_code VARCHAR(64) NOT NULL, warehouse_name VARCHAR(120) NOT NULL, " +
                "warehouse_type VARCHAR(30) NOT NULL DEFAULT 'GENERAL', owner_code VARCHAR(64), " +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', remark VARCHAR(255), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, warehouse_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS wh_storage_area (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, warehouse_code VARCHAR(64) NOT NULL, " +
                "area_code VARCHAR(64) NOT NULL, area_name VARCHAR(120) NOT NULL, area_type VARCHAR(30) NOT NULL DEFAULT 'NORMAL', " +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', remark VARCHAR(255), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, warehouse_code, area_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS wh_location (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, warehouse_code VARCHAR(64) NOT NULL, " +
                "area_code VARCHAR(64), location_code VARCHAR(64) NOT NULL, location_name VARCHAR(120), " +
                "location_type VARCHAR(30) NOT NULL DEFAULT 'BIN', capacity_qty INT NOT NULL DEFAULT 0, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, warehouse_code, location_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS wh_material (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, material_code VARCHAR(64) NOT NULL, " +
                "material_name VARCHAR(120) NOT NULL, material_type VARCHAR(30) NOT NULL DEFAULT 'RAW', unit VARCHAR(20) NOT NULL DEFAULT '件', " +
                "lot_control INT NOT NULL DEFAULT 1, serial_control INT NOT NULL DEFAULT 0, shelf_life_days INT NOT NULL DEFAULT 0, " +
                "safety_stock INT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', remark VARCHAR(255), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, material_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS wh_batch (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, material_code VARCHAR(64) NOT NULL, batch_no VARCHAR(64) NOT NULL, " +
                "production_date DATE NULL, expiry_date DATE NULL, supplier_code VARCHAR(64), quality_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                "batch_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', remark VARCHAR(255), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, material_code, batch_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS wh_document (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, document_no VARCHAR(100) NOT NULL, document_type VARCHAR(30) NOT NULL, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED', source_doc_no VARCHAR(64), warehouse_code VARCHAR(64), " +
                "from_warehouse_code VARCHAR(64), to_warehouse_code VARCHAR(64), operator_name VARCHAR(100) NOT NULL, remark VARCHAR(255), " +
                "idempotency_key VARCHAR(120), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, completed_at TIMESTAMP NULL, " +
                "UNIQUE(tenant_id, document_no), UNIQUE(tenant_id, idempotency_key))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS wh_document_line (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, document_id BIGINT NOT NULL, line_no INT NOT NULL DEFAULT 1, " +
                "material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120), unit VARCHAR(20) NOT NULL DEFAULT '件', " +
                "planned_qty INT NOT NULL DEFAULT 0, actual_qty INT NOT NULL DEFAULT 0, batch_no VARCHAR(64), from_location_code VARCHAR(64), " +
                "to_location_code VARCHAR(64), work_order_no VARCHAR(64), quality_status VARCHAR(20), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, document_id, line_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS wh_stock_count (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, count_no VARCHAR(100) NOT NULL, count_type VARCHAR(20) NOT NULL DEFAULT 'CYCLE', " +
                "status VARCHAR(20) NOT NULL DEFAULT 'OPEN', warehouse_code VARCHAR(64) NOT NULL, location_code VARCHAR(64), " +
                "operator_name VARCHAR(100) NOT NULL, remark VARCHAR(255), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, submitted_at TIMESTAMP NULL, " +
                "UNIQUE(tenant_id, count_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS wh_stock_count_line (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, count_id BIGINT NOT NULL, material_code VARCHAR(64) NOT NULL, " +
                "location_code VARCHAR(64) NOT NULL, batch_no VARCHAR(64), book_qty INT NOT NULL DEFAULT 0, count_qty INT NULL, " +
                "difference_qty INT NOT NULL DEFAULT 0, reason_code VARCHAR(64), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, count_id, material_code, location_code, batch_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS wh_barcode_rule (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, rule_code VARCHAR(64) NOT NULL, rule_name VARCHAR(120) NOT NULL, " +
                "barcode_type VARCHAR(30) NOT NULL DEFAULT 'MATERIAL', prefix VARCHAR(30), sequence_no BIGINT NOT NULL DEFAULT 0, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, rule_code))");
    }

    private void ensureQualitySchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS qm_inspection_plan (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, plan_code VARCHAR(64) NOT NULL, " +
                "plan_name VARCHAR(120) NOT NULL, inspection_type VARCHAR(20) NOT NULL, material_code VARCHAR(64), product_code VARCHAR(64), " +
                "sampling_method VARCHAR(30) NOT NULL DEFAULT 'FULL', version VARCHAR(30) NOT NULL DEFAULT 'V1', status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', " +
                "effective_from DATE NULL, effective_to DATE NULL, created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, plan_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS qm_inspection_plan_item (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, plan_id BIGINT NOT NULL, characteristic_code VARCHAR(64) NOT NULL, " +
                "characteristic_name VARCHAR(120) NOT NULL, result_type VARCHAR(20) NOT NULL DEFAULT 'QUALITATIVE', standard_text VARCHAR(255), " +
                "lower_limit DECIMAL(18,6) NULL, upper_limit DECIMAL(18,6) NULL, unit VARCHAR(20), required_flag TINYINT NOT NULL DEFAULT 1, " +
                "sort_no INT NOT NULL DEFAULT 10, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, plan_id, characteristic_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS qm_inspection_lot (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, lot_no VARCHAR(100) NOT NULL, plan_id BIGINT NULL, " +
                "inspection_type VARCHAR(20) NOT NULL, source_type VARCHAR(30), source_doc_no VARCHAR(100), work_order_no VARCHAR(64), " +
                "material_code VARCHAR(64) NOT NULL, material_name VARCHAR(120), batch_no VARCHAR(64), warehouse_code VARCHAR(64), location_code VARCHAR(64), " +
                "sample_qty INT NOT NULL DEFAULT 0, inspected_qty INT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'PENDING', inspector VARCHAR(64), " +
                "started_at TIMESTAMP NULL, completed_at TIMESTAMP NULL, remark VARCHAR(255), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, lot_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS qm_inspection_result (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, lot_id BIGINT NOT NULL, item_id BIGINT NOT NULL, " +
                "result_value DECIMAL(18,6) NULL, result_text VARCHAR(255), result_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', inspector VARCHAR(64), " +
                "remark VARCHAR(255), inspected_at TIMESTAMP NULL, UNIQUE(tenant_id, lot_id, item_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS qm_nonconformance (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, nc_no VARCHAR(100) NOT NULL, lot_id BIGINT NULL, " +
                "source_type VARCHAR(30) NOT NULL DEFAULT 'INSPECTION', source_doc_no VARCHAR(100), material_code VARCHAR(64), batch_no VARCHAR(64), " +
                "defect_code VARCHAR(64) NOT NULL, defect_name VARCHAR(120) NOT NULL, severity VARCHAR(20) NOT NULL DEFAULT 'MINOR', defect_qty INT NOT NULL DEFAULT 0, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'OPEN', disposition VARCHAR(30), containment_action VARCHAR(255), root_cause VARCHAR(500), " +
                "corrective_action VARCHAR(500), owner_code VARCHAR(64), due_date DATE NULL, closed_by VARCHAR(64), closed_at TIMESTAMP NULL, " +
                "created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, nc_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS qm_corrective_action (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, nc_id BIGINT NOT NULL, action_type VARCHAR(30) NOT NULL DEFAULT 'CORRECTIVE', " +
                "action_description VARCHAR(500) NOT NULL, owner_code VARCHAR(64), due_date DATE NULL, status VARCHAR(20) NOT NULL DEFAULT 'OPEN', " +
                "completed_at TIMESTAMP NULL, completed_by VARCHAR(64), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS qm_supplier_evaluation (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, evaluation_no VARCHAR(100) NOT NULL, " +
                "supplier_code VARCHAR(64) NOT NULL, supplier_name VARCHAR(160) NOT NULL, evaluation_period VARCHAR(30) NOT NULL, " +
                "delivery_score DECIMAL(6,2) NOT NULL DEFAULT 0, quality_score DECIMAL(6,2) NOT NULL DEFAULT 0, " +
                "service_score DECIMAL(6,2) NOT NULL DEFAULT 0, price_score DECIMAL(6,2) NOT NULL DEFAULT 0, total_score DECIMAL(6,2) NOT NULL DEFAULT 0, " +
                "grade VARCHAR(10) NOT NULL DEFAULT 'C', status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', owner_code VARCHAR(64), " +
                "evaluated_at DATE NULL, remark VARCHAR(500), created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, evaluation_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS qm_avl_entry (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, material_code VARCHAR(64) NOT NULL, material_name VARCHAR(160), " +
                "supplier_code VARCHAR(64) NOT NULL, supplier_name VARCHAR(160) NOT NULL, supplier_part_no VARCHAR(100), " +
                "approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', valid_from DATE NULL, valid_to DATE NULL, " +
                "last_evaluation_score DECIMAL(6,2) NULL, approved_by VARCHAR(64), approved_at TIMESTAMP NULL, remark VARCHAR(500), " +
                "created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, material_code, supplier_code, supplier_part_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS qm_ipqc_record (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, ipqc_no VARCHAR(100) NOT NULL, line_code VARCHAR(64) NOT NULL, " +
                "work_order_no VARCHAR(64), process_code VARCHAR(64), process_name VARCHAR(120) NOT NULL, product_code VARCHAR(64), product_name VARCHAR(160), " +
                "batch_no VARCHAR(64), sample_qty INT NOT NULL DEFAULT 0, inspected_qty INT NOT NULL DEFAULT 0, defect_qty INT NOT NULL DEFAULT 0, " +
                "first_piece_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', status VARCHAR(20) NOT NULL DEFAULT 'PENDING', inspector VARCHAR(64), " +
                "started_at TIMESTAMP NULL, completed_at TIMESTAMP NULL, remark VARCHAR(500), created_by VARCHAR(64) NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, ipqc_no))");
    }

    /**
     * Operations control-tower tables are also created for upgraded customer
     * databases.  Keeping this migration idempotent lets an existing MES/WMS
     * installation adopt equipment and exception management without a manual
     * destructive migration.
     */
    private void ensureOperationsSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS mfg_equipment (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, equipment_code VARCHAR(64) NOT NULL, " +
                "equipment_name VARCHAR(120) NOT NULL, work_center VARCHAR(64), model VARCHAR(100), status VARCHAR(20) NOT NULL DEFAULT 'RUNNING', " +
                "health_score INT NOT NULL DEFAULT 100, current_work_order VARCHAR(64), last_maintenance_at TIMESTAMP NULL, " +
                "next_maintenance_at TIMESTAMP NULL, last_heartbeat_at TIMESTAMP NULL, remark VARCHAR(255), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, equipment_code))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mfg_downtime_event (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, event_no VARCHAR(100) NOT NULL, equipment_code VARCHAR(64) NOT NULL, " +
                "work_center VARCHAR(64), work_order_no VARCHAR(64), reason_code VARCHAR(64) NOT NULL, reason_name VARCHAR(120), " +
                "severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM', description VARCHAR(500), started_at TIMESTAMP NOT NULL, ended_at TIMESTAMP NULL, " +
                "duration_minutes INT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'OPEN', reported_by VARCHAR(64) NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id, event_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mfg_exception (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, exception_no VARCHAR(100) NOT NULL, idempotency_key VARCHAR(120), " +
                "category VARCHAR(30) NOT NULL DEFAULT 'PROCESS', priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM', source_type VARCHAR(30), source_ref VARCHAR(100), " +
                "equipment_code VARCHAR(64), work_center VARCHAR(64), work_order_no VARCHAR(64), title VARCHAR(160) NOT NULL, description VARCHAR(1000) NOT NULL, " +
                "impact_qty INT NOT NULL DEFAULT 0, owner_code VARCHAR(64), due_at TIMESTAMP NULL, status VARCHAR(20) NOT NULL DEFAULT 'OPEN', " +
                "detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, acknowledged_at TIMESTAMP NULL, resolved_at TIMESTAMP NULL, closed_at TIMESTAMP NULL, " +
                "created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, exception_no), UNIQUE(tenant_id, idempotency_key))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mfg_exception_action (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, exception_id BIGINT NOT NULL, action_type VARCHAR(30) NOT NULL DEFAULT 'CONTAINMENT', " +
                "action_description VARCHAR(500) NOT NULL, owner_code VARCHAR(64), due_at TIMESTAMP NULL, status VARCHAR(20) NOT NULL DEFAULT 'OPEN', " +
                "completed_at TIMESTAMP NULL, completed_by VARCHAR(64), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    private void ensureErpSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS erp_business_record (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, domain VARCHAR(30) NOT NULL, " +
                "record_type VARCHAR(40) NOT NULL, record_no VARCHAR(100) NOT NULL, record_name VARCHAR(180) NOT NULL, " +
                "partner_name VARCHAR(180), org_code VARCHAR(64), department_code VARCHAR(64), requester_code VARCHAR(64), " +
                "currency_code VARCHAR(10) NOT NULL DEFAULT 'CNY', amount_value DECIMAL(18,2) NULL, tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0, " +
                "amount_label VARCHAR(100), business_date DATE NOT NULL, delivery_date DATE NULL, payment_terms VARCHAR(120), " +
                "source_type VARCHAR(40), source_doc_no VARCHAR(100), owner_code VARCHAR(64), status VARCHAR(30) NOT NULL, " +
                "remark VARCHAR(500), created_by VARCHAR(64) NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(tenant_id, record_no))");
        ensureColumn("erp_business_record", "org_code", "VARCHAR(64)");
        ensureColumn("erp_business_record", "department_code", "VARCHAR(64)");
        ensureColumn("erp_business_record", "requester_code", "VARCHAR(64)");
        ensureColumn("erp_business_record", "currency_code", "VARCHAR(10) NOT NULL DEFAULT 'CNY'");
        ensureColumn("erp_business_record", "tax_amount", "DECIMAL(18,2) NOT NULL DEFAULT 0");
        ensureColumn("erp_business_record", "delivery_date", "DATE NULL");
        ensureColumn("erp_business_record", "payment_terms", "VARCHAR(120)");
        ensureColumn("erp_business_record", "source_type", "VARCHAR(40)");
        ensureColumn("erp_business_record", "source_doc_no", "VARCHAR(100)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS erp_business_record_line (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, record_id BIGINT NOT NULL, line_no INT NOT NULL, " +
                "material_code VARCHAR(64), material_name VARCHAR(180) NOT NULL, specification VARCHAR(255), unit VARCHAR(20) NOT NULL DEFAULT '件', " +
                "requested_qty DECIMAL(18,6) NOT NULL DEFAULT 0, delivered_qty DECIMAL(18,6) NOT NULL DEFAULT 0, " +
                "unit_price DECIMAL(18,6) NOT NULL DEFAULT 0, tax_rate DECIMAL(8,4) NOT NULL DEFAULT 0, amount_value DECIMAL(18,2) NOT NULL DEFAULT 0, " +
                "required_date DATE NULL, warehouse_code VARCHAR(64), cost_center VARCHAR(64), project_code VARCHAR(64), source_ref VARCHAR(100), " +
                "remark VARCHAR(500), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        try { jdbc.execute("CREATE INDEX idx_erp_record_line_record ON erp_business_record_line(tenant_id, record_id, line_no)"); }
        catch (DataAccessException ignored) { /* existing installation already has the index */ }
    }

    private void ensureColumn(String table, String column, String definition) {
        if (!tableExists(table) || columnExists(table, column)) return;
        try { jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition); }
        catch (DataAccessException ignored) { /* Another instance may have completed the migration. */ }
    }
}
