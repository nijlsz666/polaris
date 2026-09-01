CREATE DATABASE IF NOT EXISTS polaris_mes CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE polaris_mes;

-- A tenant is the security boundary. Every operational table carries tenant_id.
CREATE TABLE IF NOT EXISTS sys_tenant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_code VARCHAR(64) NOT NULL UNIQUE,
  tenant_name VARCHAR(120) NOT NULL,
  tenant_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
  plan_code VARCHAR(32) NOT NULL DEFAULT 'STARTER',
  contact_name VARCHAR(100),
  contact_email VARCHAR(160),
  trial_ends_at DATETIME NULL,
  max_users INT NOT NULL DEFAULT 10,
  status TINYINT NOT NULL DEFAULT 1,
  settings_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Platform-operations data is intentionally not tenant-scoped. Access is
-- restricted to the dedicated polaris-admin tenant and platform_admin role.
CREATE TABLE IF NOT EXISTS platform_feature (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  feature_code VARCHAR(80) NOT NULL UNIQUE,
  feature_name VARCHAR(120) NOT NULL,
  category VARCHAR(40) NOT NULL,
  description VARCHAR(255),
  sort_no INT NOT NULL DEFAULT 99,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_feature_grant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  feature_code VARCHAR(80) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  quota_json TEXT NULL,
  expires_at DATETIME NULL,
  granted_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_feature(tenant_id, feature_code),
  KEY idx_tenant_feature_tenant(tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_billing_account (
  tenant_id BIGINT PRIMARY KEY,
  currency_code VARCHAR(10) NOT NULL DEFAULT 'CNY',
  balance DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_paid DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_consumed DECIMAL(18,2) NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_billing_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  record_type VARCHAR(30) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  description VARCHAR(255) NOT NULL,
  period_start DATE NULL,
  period_end DATE NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_billing_tenant_time(tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_points_account (
  tenant_id BIGINT PRIMARY KEY,
  balance BIGINT NOT NULL DEFAULT 0,
  total_earned BIGINT NOT NULL DEFAULT 0,
  total_spent BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_points_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  change_amount BIGINT NOT NULL,
  balance_after BIGINT NOT NULL,
  reason VARCHAR(255) NOT NULL,
  reference_type VARCHAR(40),
  reference_id VARCHAR(80),
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_points_tenant_time(tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tenant traffic is measured in request and response body bytes. The account is
-- the current quota snapshot; the ledger keeps allocation and consumption history.
CREATE TABLE IF NOT EXISTS tenant_traffic_account (
  tenant_id BIGINT PRIMARY KEY,
  quota_bytes BIGINT NOT NULL DEFAULT 0,
  used_bytes BIGINT NOT NULL DEFAULT 0,
  warning_threshold_percent INT NOT NULL DEFAULT 10,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_traffic_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  action_type VARCHAR(20) NOT NULL,
  change_bytes BIGINT NOT NULL DEFAULT 0,
  consumed_bytes BIGINT NOT NULL DEFAULT 0,
  quota_after_bytes BIGINT NOT NULL,
  used_after_bytes BIGINT NOT NULL,
  description VARCHAR(255) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_traffic_tenant_time(tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tenant storage is measured by occupied bytes. The unit price is CNY per GB-month;
-- the estimated monthly charge is calculated from the current used_bytes snapshot.
CREATE TABLE IF NOT EXISTS tenant_storage_account (
  tenant_id BIGINT PRIMARY KEY,
  quota_bytes BIGINT NOT NULL DEFAULT 0,
  used_bytes BIGINT NOT NULL DEFAULT 0,
  warning_threshold_percent INT NOT NULL DEFAULT 10,
  unit_price_per_gb_month DECIMAL(18,4) NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_storage_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  action_type VARCHAR(20) NOT NULL,
  change_bytes BIGINT NOT NULL DEFAULT 0,
  consumed_bytes BIGINT NOT NULL DEFAULT 0,
  quota_after_bytes BIGINT NOT NULL,
  used_after_bytes BIGINT NOT NULL,
  description VARCHAR(255) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_storage_tenant_time(tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_service_ticket (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  category VARCHAR(30) NOT NULL DEFAULT 'QUESTION',
  subject VARCHAR(180) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  created_by VARCHAR(100) NOT NULL,
  assignee VARCHAR(100),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ticket_status_time(status, created_at),
  KEY idx_ticket_tenant(tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_service_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_id BIGINT NOT NULL,
  sender_type VARCHAR(20) NOT NULL,
  sender_name VARCHAR(100) NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ticket_message(ticket_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_training_course (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_code VARCHAR(80) NOT NULL UNIQUE,
  course_name VARCHAR(180) NOT NULL,
  category VARCHAR(40) NOT NULL DEFAULT 'PRODUCT',
  instructor VARCHAR(100),
  schedule_at DATETIME NULL,
  capacity INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  description VARCHAR(500),
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_training_enrollment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  contact_name VARCHAR(100),
  status VARCHAR(20) NOT NULL DEFAULT 'ENROLLED',
  enrolled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_course_tenant(course_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_marketing_campaign (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  campaign_code VARCHAR(80) NOT NULL UNIQUE,
  campaign_name VARCHAR(180) NOT NULL,
  campaign_type VARCHAR(40) NOT NULL DEFAULT 'ANNOUNCEMENT',
  audience VARCHAR(40) NOT NULL DEFAULT 'ALL_CUSTOMERS',
  content TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  starts_at DATETIME NULL,
  ends_at DATETIME NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  role_code VARCHAR(64) NOT NULL DEFAULT 'operator',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_tenant_username(tenant_id, username),
  KEY idx_user_tenant(tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_tenant_code(tenant_id, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  parent_id BIGINT NOT NULL DEFAULT 0,
  menu_code VARCHAR(100) NOT NULL,
  menu_name VARCHAR(100) NOT NULL,
  menu_type VARCHAR(20) NOT NULL DEFAULT 'MENU',
  route_path VARCHAR(200),
  icon VARCHAR(100),
  sort_no INT NOT NULL DEFAULT 99,
  visible TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_menu_tenant_code(tenant_id, menu_code),
  KEY idx_menu_tenant(tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  role_code VARCHAR(64) NOT NULL,
  resource_type VARCHAR(20) NOT NULL,
  resource_code VARCHAR(150) NOT NULL,
  action_code VARCHAR(50) NOT NULL DEFAULT 'VIEW',
  field_mask_json JSON NULL,
  effect VARCHAR(10) NOT NULL DEFAULT 'ALLOW',
  UNIQUE KEY uk_permission(tenant_id, role_code, resource_type, resource_code, action_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_dictionary (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  dict_type VARCHAR(80) NOT NULL,
  parent_id BIGINT NOT NULL DEFAULT 0,
  dict_code VARCHAR(80) NOT NULL,
  dict_label VARCHAR(160) NOT NULL,
  dict_value VARCHAR(160) NOT NULL,
  locale VARCHAR(20) NOT NULL DEFAULT 'zh-CN',
  sort_no INT NOT NULL DEFAULT 99,
  status TINYINT NOT NULL DEFAULT 1,
  metadata_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dictionary_tenant_item(tenant_id, dict_type, dict_code, locale),
  KEY idx_dictionary_lookup(tenant_id, dict_type, locale, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ERP business foundation. Domain-specific modules share this auditable record
-- contract first; high-volume installations can split these records later.
CREATE TABLE IF NOT EXISTS erp_business_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  domain VARCHAR(30) NOT NULL,
  record_type VARCHAR(40) NOT NULL,
  record_no VARCHAR(100) NOT NULL,
  record_name VARCHAR(180) NOT NULL,
  partner_name VARCHAR(180),
  org_code VARCHAR(64),
  department_code VARCHAR(64),
  requester_code VARCHAR(64),
  currency_code VARCHAR(10) NOT NULL DEFAULT 'CNY',
  amount_value DECIMAL(18,2) NULL,
  tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  amount_label VARCHAR(100),
  business_date DATE NOT NULL,
  delivery_date DATE NULL,
  payment_terms VARCHAR(120),
  source_type VARCHAR(40),
  source_doc_no VARCHAR(100),
  owner_code VARCHAR(64),
  status VARCHAR(30) NOT NULL,
  remark VARCHAR(500),
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_erp_record_tenant_no(tenant_id, record_no),
  KEY idx_erp_record_list(tenant_id, domain, record_type, status, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS erp_business_record_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  record_id BIGINT NOT NULL,
  line_no INT NOT NULL,
  material_code VARCHAR(64),
  material_name VARCHAR(180) NOT NULL,
  specification VARCHAR(255),
  unit VARCHAR(20) NOT NULL DEFAULT '件',
  requested_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  delivered_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  unit_price DECIMAL(18,6) NOT NULL DEFAULT 0,
  tax_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
  amount_value DECIMAL(18,2) NOT NULL DEFAULT 0,
  required_date DATE NULL,
  warehouse_code VARCHAR(64),
  cost_center VARCHAR(64),
  project_code VARCHAR(64),
  source_ref VARCHAR(100),
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_erp_record_line_record(tenant_id, record_id, line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI-assisted purchase requisition sessions and idempotent confirmations.
CREATE TABLE IF NOT EXISTS ai_generation_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  session_id VARCHAR(100) NOT NULL,
  user_id BIGINT NOT NULL,
  intent VARCHAR(80),
  input_hash VARCHAR(128),
  input_text TEXT,
  draft_json LONGTEXT NOT NULL,
  model VARCHAR(100),
  prompt_version VARCHAR(100),
  status VARCHAR(30) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ai_session_tenant(tenant_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_generation_field_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  session_id VARCHAR(100) NOT NULL,
  field_path VARCHAR(180) NOT NULL,
  source_type VARCHAR(40) NOT NULL,
  source_text TEXT,
  confidence DECIMAL(6,4),
  before_value TEXT,
  after_value TEXT,
  edited_by VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_audit_session(tenant_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_purchase_submission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  idempotency_key VARCHAR(180) NOT NULL,
  session_id VARCHAR(100) NOT NULL,
  record_id BIGINT NULL,
  record_no VARCHAR(100),
  bpm_instance_id VARCHAR(150),
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ai_submit_key(tenant_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS purchase_approval_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  rule_code VARCHAR(80) NOT NULL,
  priority INT NOT NULL DEFAULT 99,
  min_amount DECIMAL(18,2) NULL,
  max_amount DECIMAL(18,2) NULL,
  currency_code VARCHAR(10) NULL,
  nodes_json TEXT NOT NULL,
  version VARCHAR(40) NOT NULL DEFAULT 'v0.1',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  effective_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_purchase_rule_version(tenant_id, rule_code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bom (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  bom_code VARCHAR(64) NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  product_name VARCHAR(120) NOT NULL,
  version VARCHAR(30) NOT NULL DEFAULT 'V1',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  remark VARCHAR(255),
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_bom_tenant_code(tenant_id, bom_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bom_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  bom_id BIGINT NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(120) NOT NULL,
  quantity DECIMAL(18,6) NOT NULL DEFAULT 0,
  unit VARCHAR(20) NOT NULL DEFAULT '件',
  loss_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
  issue_method VARCHAR(30) NOT NULL DEFAULT 'PICK',
  UNIQUE KEY uk_bom_item(tenant_id, bom_id, material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS production_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  plan_no VARCHAR(64) NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  product_name VARCHAR(120) NOT NULL,
  plan_qty INT NOT NULL DEFAULT 0,
  released_qty INT NOT NULL DEFAULT 0,
  plan_date DATE NOT NULL,
  priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_plan_tenant_no(tenant_id, plan_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS work_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  product_name VARCHAR(120) NOT NULL,
  plan_qty INT NOT NULL DEFAULT 0,
  completed_qty INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
  planned_start DATETIME NULL,
  planned_end DATETIME NULL,
  work_center VARCHAR(64),
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_work_order_tenant_no(tenant_id, order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Persisted planning execution documents.  A run is immutable evidence of the
-- supply snapshot used by MRP; shortage/call/ASN are executable follow-up
-- documents linked back to that run or to the source purchase order.
CREATE TABLE IF NOT EXISTS mrp_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  run_no VARCHAR(100) NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  product_name VARCHAR(120) NOT NULL,
  plan_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  plan_date DATE NOT NULL,
  bom_id BIGINT NOT NULL,
  bom_code VARCHAR(64) NOT NULL,
  bom_version VARCHAR(30) NOT NULL,
  source_type VARCHAR(40),
  source_doc_no VARCHAR(100),
  priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  status VARCHAR(20) NOT NULL DEFAULT 'CALCULATED',
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mrp_run_tenant_no(tenant_id, run_no),
  KEY idx_mrp_run_product(tenant_id, product_code, plan_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mrp_requirement (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  line_no INT NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(120) NOT NULL,
  unit VARCHAR(20) NOT NULL DEFAULT '件',
  quantity_per DECIMAL(18,6) NOT NULL DEFAULT 0,
  loss_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
  gross_required_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  safety_stock_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  required_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  available_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  reserved_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  locked_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  in_transit_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  open_po_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  covered_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  net_shortage_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  due_date DATE NOT NULL,
  issue_method VARCHAR(30) NOT NULL DEFAULT 'PICK',
  shortage_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mrp_requirement_line(tenant_id, run_id, line_no),
  KEY idx_mrp_requirement_material(tenant_id, material_code, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mrp_shortage (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  shortage_no VARCHAR(100) NOT NULL,
  run_id BIGINT NOT NULL,
  requirement_id BIGINT NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(120) NOT NULL,
  unit VARCHAR(20) NOT NULL DEFAULT '件',
  shortage_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  resolved_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  required_date DATE NOT NULL,
  priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  source_type VARCHAR(40),
  source_doc_no VARCHAR(100),
  procurement_record_no VARCHAR(100),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  owner_code VARCHAR(64),
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mrp_shortage_tenant_no(tenant_id, shortage_no),
  KEY idx_mrp_shortage_queue(tenant_id, status, priority, required_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS material_call (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  call_no VARCHAR(100) NOT NULL,
  shortage_id BIGINT NOT NULL,
  work_order_no VARCHAR(64),
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(120) NOT NULL,
  unit VARCHAR(20) NOT NULL DEFAULT '件',
  requested_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  issued_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  required_at DATETIME NOT NULL,
  priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  from_warehouse_code VARCHAR(64),
  to_warehouse_code VARCHAR(64),
  requested_by VARCHAR(64) NOT NULL,
  assigned_to VARCHAR(64),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_material_call_tenant_no(tenant_id, call_no),
  KEY idx_material_call_queue(tenant_id, status, priority, required_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS asn (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  asn_no VARCHAR(100) NOT NULL,
  purchase_order_no VARCHAR(100) NOT NULL,
  supplier_code VARCHAR(64),
  supplier_name VARCHAR(180) NOT NULL,
  expected_arrival DATE NOT NULL,
  warehouse_code VARCHAR(64) NOT NULL,
  carrier VARCHAR(120),
  tracking_no VARCHAR(120),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  created_by VARCHAR(64) NOT NULL,
  submitted_at DATETIME NULL,
  received_at DATETIME NULL,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_asn_tenant_no(tenant_id, asn_no),
  KEY idx_asn_queue(tenant_id, status, expected_arrival),
  KEY idx_asn_po(tenant_id, purchase_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS asn_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  asn_id BIGINT NOT NULL,
  line_no INT NOT NULL,
  po_line_id BIGINT NULL,
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(120) NOT NULL,
  unit VARCHAR(20) NOT NULL DEFAULT '件',
  planned_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  shipped_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  received_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
  batch_no VARCHAR(64),
  production_date DATE NULL,
  expiry_date DATE NULL,
  quality_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_asn_line(tenant_id, asn_id, line_no),
  KEY idx_asn_line_material(tenant_id, material_code, batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS material_transaction (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  transaction_no VARCHAR(100) NOT NULL,
  transaction_type VARCHAR(30) NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(120),
  warehouse_code VARCHAR(64) NOT NULL,
  location_code VARCHAR(64),
  batch_no VARCHAR(64),
  quantity INT NOT NULL DEFAULT 0,
  unit VARCHAR(20) NOT NULL DEFAULT '件',
  operator_name VARCHAR(100) NOT NULL,
  source_doc_no VARCHAR(64),
  document_no VARCHAR(100),
  from_warehouse_code VARCHAR(64),
  from_location_code VARCHAR(64),
  to_warehouse_code VARCHAR(64),
  to_location_code VARCHAR(64),
  reason_code VARCHAR(64),
  idempotency_key VARCHAR(120),
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tx_tenant_no(tenant_id, transaction_no),
  UNIQUE KEY uk_tx_tenant_idempotency(tenant_id, idempotency_key),
  KEY idx_tx_material(tenant_id, material_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(120) NOT NULL,
  warehouse_code VARCHAR(64) NOT NULL,
  location_code VARCHAR(64) NOT NULL,
  batch_no VARCHAR(64),
  available_qty INT NOT NULL DEFAULT 0,
  locked_qty INT NOT NULL DEFAULT 0,
  reserved_qty INT NOT NULL DEFAULT 0,
  in_transit_qty INT NOT NULL DEFAULT 0,
  unit VARCHAR(20) NOT NULL DEFAULT '件',
  safety_stock INT NOT NULL DEFAULT 0,
  stock_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  expiry_date DATE NULL,
  version_no BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_inventory(tenant_id, material_code, warehouse_code, location_code, batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS barcode (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  barcode VARCHAR(100) NOT NULL,
  barcode_type VARCHAR(30) NOT NULL DEFAULT 'MATERIAL',
  material_code VARCHAR(64),
  batch_no VARCHAR(64),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  source_doc_no VARCHAR(64),
  warehouse_code VARCHAR(64),
  location_code VARCHAR(64),
  printed_count INT NOT NULL DEFAULT 0,
  voided_at DATETIME NULL,
  printed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_barcode_tenant_code(tenant_id, barcode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Warehouse master data and document models. These tables keep the WMS
-- vocabulary explicit while the legacy inventory/transaction tables remain
-- compatible with the first Polaris release.
CREATE TABLE IF NOT EXISTS wh_warehouse (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  warehouse_code VARCHAR(64) NOT NULL,
  warehouse_name VARCHAR(120) NOT NULL,
  warehouse_type VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
  owner_code VARCHAR(64),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wh_warehouse_code(tenant_id, warehouse_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_storage_area (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  warehouse_code VARCHAR(64) NOT NULL,
  area_code VARCHAR(64) NOT NULL,
  area_name VARCHAR(120) NOT NULL,
  area_type VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wh_area_code(tenant_id, warehouse_code, area_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_location (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  warehouse_code VARCHAR(64) NOT NULL,
  area_code VARCHAR(64),
  location_code VARCHAR(64) NOT NULL,
  location_name VARCHAR(120),
  location_type VARCHAR(30) NOT NULL DEFAULT 'BIN',
  capacity_qty INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wh_location_code(tenant_id, warehouse_code, location_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_material (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(120) NOT NULL,
  material_type VARCHAR(30) NOT NULL DEFAULT 'RAW',
  unit VARCHAR(20) NOT NULL DEFAULT '件',
  lot_control TINYINT NOT NULL DEFAULT 1,
  serial_control TINYINT NOT NULL DEFAULT 0,
  shelf_life_days INT NOT NULL DEFAULT 0,
  safety_stock INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wh_material_code(tenant_id, material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  batch_no VARCHAR(64) NOT NULL,
  production_date DATE NULL,
  expiry_date DATE NULL,
  supplier_code VARCHAR(64),
  quality_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  batch_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wh_batch(tenant_id, material_code, batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  document_no VARCHAR(100) NOT NULL,
  document_type VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  source_doc_no VARCHAR(64),
  warehouse_code VARCHAR(64),
  from_warehouse_code VARCHAR(64),
  to_warehouse_code VARCHAR(64),
  operator_name VARCHAR(100) NOT NULL,
  remark VARCHAR(255),
  idempotency_key VARCHAR(120),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  UNIQUE KEY uk_wh_document_no(tenant_id, document_no),
  UNIQUE KEY uk_wh_document_idempotency(tenant_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_document_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  document_id BIGINT NOT NULL,
  line_no INT NOT NULL DEFAULT 1,
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(120),
  unit VARCHAR(20) NOT NULL DEFAULT '件',
  planned_qty INT NOT NULL DEFAULT 0,
  actual_qty INT NOT NULL DEFAULT 0,
  batch_no VARCHAR(64),
  from_location_code VARCHAR(64),
  to_location_code VARCHAR(64),
  work_order_no VARCHAR(64),
  quality_status VARCHAR(20),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_wh_doc_line(tenant_id, document_id),
  UNIQUE KEY uk_wh_doc_line(tenant_id, document_id, line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_stock_count (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  count_no VARCHAR(100) NOT NULL,
  count_type VARCHAR(20) NOT NULL DEFAULT 'CYCLE',
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  warehouse_code VARCHAR(64) NOT NULL,
  location_code VARCHAR(64),
  operator_name VARCHAR(100) NOT NULL,
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  submitted_at DATETIME NULL,
  UNIQUE KEY uk_wh_count_no(tenant_id, count_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_stock_count_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  count_id BIGINT NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  location_code VARCHAR(64) NOT NULL,
  batch_no VARCHAR(64),
  book_qty INT NOT NULL DEFAULT 0,
  count_qty INT NULL,
  difference_qty INT NOT NULL DEFAULT 0,
  reason_code VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wh_count_line(tenant_id, count_id, material_code, location_code, batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wh_barcode_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  rule_code VARCHAR(64) NOT NULL,
  rule_name VARCHAR(120) NOT NULL,
  barcode_type VARCHAR(30) NOT NULL DEFAULT 'MATERIAL',
  prefix VARCHAR(30),
  sequence_no BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wh_barcode_rule(tenant_id, rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quality management: inspection planning, inspection lots, results and nonconformance.
CREATE TABLE IF NOT EXISTS qm_inspection_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  plan_code VARCHAR(64) NOT NULL,
  plan_name VARCHAR(120) NOT NULL,
  inspection_type VARCHAR(20) NOT NULL,
  material_code VARCHAR(64),
  product_code VARCHAR(64),
  sampling_method VARCHAR(30) NOT NULL DEFAULT 'FULL',
  version VARCHAR(30) NOT NULL DEFAULT 'V1',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  effective_from DATE NULL,
  effective_to DATE NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qm_plan_tenant_code(tenant_id, plan_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qm_inspection_plan_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  characteristic_code VARCHAR(64) NOT NULL,
  characteristic_name VARCHAR(120) NOT NULL,
  result_type VARCHAR(20) NOT NULL DEFAULT 'QUALITATIVE',
  standard_text VARCHAR(255),
  lower_limit DECIMAL(18,6) NULL,
  upper_limit DECIMAL(18,6) NULL,
  unit VARCHAR(20),
  required_flag TINYINT NOT NULL DEFAULT 1,
  sort_no INT NOT NULL DEFAULT 10,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qm_plan_item(tenant_id, plan_id, characteristic_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qm_inspection_lot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  lot_no VARCHAR(100) NOT NULL,
  plan_id BIGINT NULL,
  inspection_type VARCHAR(20) NOT NULL,
  source_type VARCHAR(30),
  source_doc_no VARCHAR(100),
  work_order_no VARCHAR(64),
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(120),
  batch_no VARCHAR(64),
  warehouse_code VARCHAR(64),
  location_code VARCHAR(64),
  sample_qty INT NOT NULL DEFAULT 0,
  inspected_qty INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  inspector VARCHAR(64),
  started_at DATETIME NULL,
  completed_at DATETIME NULL,
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qm_lot_tenant_no(tenant_id, lot_no),
  KEY idx_qm_lot_status(tenant_id, status, created_at),
  KEY idx_qm_lot_material(tenant_id, material_code, batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qm_inspection_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  lot_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  result_value DECIMAL(18,6) NULL,
  result_text VARCHAR(255),
  result_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  inspector VARCHAR(64),
  remark VARCHAR(255),
  inspected_at DATETIME NULL,
  UNIQUE KEY uk_qm_result_item(tenant_id, lot_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qm_nonconformance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  nc_no VARCHAR(100) NOT NULL,
  lot_id BIGINT NULL,
  source_type VARCHAR(30) NOT NULL DEFAULT 'INSPECTION',
  source_doc_no VARCHAR(100),
  material_code VARCHAR(64),
  batch_no VARCHAR(64),
  defect_code VARCHAR(64) NOT NULL,
  defect_name VARCHAR(120) NOT NULL,
  severity VARCHAR(20) NOT NULL DEFAULT 'MINOR',
  defect_qty INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  disposition VARCHAR(30),
  containment_action VARCHAR(255),
  root_cause VARCHAR(500),
  corrective_action VARCHAR(500),
  owner_code VARCHAR(64),
  due_date DATE NULL,
  closed_by VARCHAR(64),
  closed_at DATETIME NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qm_nc_tenant_no(tenant_id, nc_no),
  KEY idx_qm_nc_status(tenant_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qm_corrective_action (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  nc_id BIGINT NOT NULL,
  action_type VARCHAR(30) NOT NULL DEFAULT 'CORRECTIVE',
  action_description VARCHAR(500) NOT NULL,
  owner_code VARCHAR(64),
  due_date DATE NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  completed_at DATETIME NULL,
  completed_by VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_qm_action_nc(tenant_id, nc_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qm_supplier_evaluation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  evaluation_no VARCHAR(100) NOT NULL,
  supplier_code VARCHAR(64) NOT NULL,
  supplier_name VARCHAR(160) NOT NULL,
  evaluation_period VARCHAR(30) NOT NULL,
  delivery_score DECIMAL(6,2) NOT NULL DEFAULT 0,
  quality_score DECIMAL(6,2) NOT NULL DEFAULT 0,
  service_score DECIMAL(6,2) NOT NULL DEFAULT 0,
  price_score DECIMAL(6,2) NOT NULL DEFAULT 0,
  total_score DECIMAL(6,2) NOT NULL DEFAULT 0,
  grade VARCHAR(10) NOT NULL DEFAULT 'C',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  owner_code VARCHAR(64),
  evaluated_at DATE NULL,
  remark VARCHAR(500),
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qm_supplier_eval_no(tenant_id, evaluation_no),
  KEY idx_qm_supplier_eval_status(tenant_id, status, evaluated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qm_avl_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(160),
  supplier_code VARCHAR(64) NOT NULL,
  supplier_name VARCHAR(160) NOT NULL,
  supplier_part_no VARCHAR(100),
  approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  valid_from DATE NULL,
  valid_to DATE NULL,
  last_evaluation_score DECIMAL(6,2) NULL,
  approved_by VARCHAR(64),
  approved_at DATETIME NULL,
  remark VARCHAR(500),
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qm_avl_entry(tenant_id, material_code, supplier_code, supplier_part_no),
  KEY idx_qm_avl_status(tenant_id, approval_status, valid_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qm_ipqc_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  ipqc_no VARCHAR(100) NOT NULL,
  line_code VARCHAR(64) NOT NULL,
  work_order_no VARCHAR(64),
  process_code VARCHAR(64),
  process_name VARCHAR(120) NOT NULL,
  product_code VARCHAR(64),
  product_name VARCHAR(160),
  batch_no VARCHAR(64),
  sample_qty INT NOT NULL DEFAULT 0,
  inspected_qty INT NOT NULL DEFAULT 0,
  defect_qty INT NOT NULL DEFAULT 0,
  first_piece_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  inspector VARCHAR(64),
  started_at DATETIME NULL,
  completed_at DATETIME NULL,
  remark VARCHAR(500),
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qm_ipqc_no(tenant_id, ipqc_no),
  KEY idx_qm_ipqc_status(tenant_id, status, created_at),
  KEY idx_qm_ipqc_line(tenant_id, line_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS report_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  report_code VARCHAR(64) NOT NULL,
  report_name VARCHAR(120) NOT NULL,
  source_table VARCHAR(120) NOT NULL,
  chart_type VARCHAR(30) NOT NULL DEFAULT 'TABLE',
  config_json JSON NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_report_tenant_code(tenant_id, report_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS data_source_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  source_code VARCHAR(80) NOT NULL,
  source_name VARCHAR(160) NOT NULL,
  source_type VARCHAR(30) NOT NULL DEFAULT 'SQL',
  config_json JSON NOT NULL,
  schema_json JSON NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_data_source_tenant_code(tenant_id, source_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lowcode_page (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  page_code VARCHAR(64) NOT NULL,
  page_name VARCHAR(120) NOT NULL,
  page_type VARCHAR(30) NOT NULL DEFAULT 'FORM',
  schema_json JSON NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_lowcode_tenant_code(tenant_id, page_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dashboard_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  dashboard_code VARCHAR(64) NOT NULL,
  dashboard_name VARCHAR(120) NOT NULL,
  layout_json JSON NOT NULL,
  theme VARCHAR(30) NOT NULL DEFAULT 'dark-blue',
  status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dashboard_tenant_code(tenant_id, dashboard_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  actor VARCHAR(100) NOT NULL,
  action_code VARCHAR(100) NOT NULL,
  resource_type VARCHAR(30) NOT NULL,
  resource_id VARCHAR(64),
  request_uri VARCHAR(255),
  request_body JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_tenant_time(tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  notification_type VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
  title VARCHAR(160) NOT NULL,
  content VARCHAR(500) NOT NULL,
  level VARCHAR(20) NOT NULL DEFAULT 'INFO',
  action_url VARCHAR(255),
  read_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_notification_user(tenant_id, user_id, read_at, created_at),
  KEY idx_notification_tenant(tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS release_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  release_no VARCHAR(64) NOT NULL,
  version VARCHAR(64) NOT NULL,
  package_type VARCHAR(20) NOT NULL DEFAULT 'DATA',
  source_environment VARCHAR(30) NOT NULL DEFAULT 'TEST',
  target_environment VARCHAR(30) NOT NULL DEFAULT 'PRODUCTION',
  package_name VARCHAR(180) NOT NULL,
  package_path VARCHAR(500) NOT NULL,
  artifact_hash CHAR(64) NOT NULL,
  manifest_json JSON NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
  verification_status VARCHAR(20) NOT NULL DEFAULT 'NOT_VERIFIED',
  verification_message VARCHAR(500),
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at DATETIME NULL,
  verified_at DATETIME NULL,
  UNIQUE KEY uk_release_tenant_no(tenant_id, release_no),
  UNIQUE KEY uk_release_tenant_version(tenant_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS release_verification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  release_id BIGINT NOT NULL,
  environment VARCHAR(30) NOT NULL,
  expected_hash CHAR(64) NOT NULL,
  actual_hash CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  details_json JSON NULL,
  verified_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_release_verification_tenant(tenant_id, release_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Manufacturing operations control tower: equipment, downtime and exception closure.
-- These records are deliberately separate from inventory and quality documents so
-- an incident can span a work order, machine, material and corrective action.
CREATE TABLE IF NOT EXISTS mfg_equipment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  equipment_code VARCHAR(64) NOT NULL,
  equipment_name VARCHAR(120) NOT NULL,
  work_center VARCHAR(64),
  model VARCHAR(100),
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  health_score INT NOT NULL DEFAULT 100,
  current_work_order VARCHAR(64),
  last_maintenance_at DATETIME NULL,
  next_maintenance_at DATETIME NULL,
  last_heartbeat_at DATETIME NULL,
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mfg_equipment(tenant_id, equipment_code),
  KEY idx_mfg_equipment_status(tenant_id, status, next_maintenance_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mfg_downtime_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  event_no VARCHAR(100) NOT NULL,
  equipment_code VARCHAR(64) NOT NULL,
  work_center VARCHAR(64),
  work_order_no VARCHAR(64),
  reason_code VARCHAR(64) NOT NULL,
  reason_name VARCHAR(120),
  severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  description VARCHAR(500),
  started_at DATETIME NOT NULL,
  ended_at DATETIME NULL,
  duration_minutes INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  reported_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mfg_downtime_event(tenant_id, event_no),
  KEY idx_mfg_downtime(tenant_id, status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mfg_exception (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  exception_no VARCHAR(100) NOT NULL,
  idempotency_key VARCHAR(120),
  category VARCHAR(30) NOT NULL DEFAULT 'PROCESS',
  priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  source_type VARCHAR(30),
  source_ref VARCHAR(100),
  equipment_code VARCHAR(64),
  work_center VARCHAR(64),
  work_order_no VARCHAR(64),
  title VARCHAR(160) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  impact_qty INT NOT NULL DEFAULT 0,
  owner_code VARCHAR(64),
  due_at DATETIME NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  detected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged_at DATETIME NULL,
  resolved_at DATETIME NULL,
  closed_at DATETIME NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mfg_exception_no(tenant_id, exception_no),
  UNIQUE KEY uk_mfg_exception_idempotency(tenant_id, idempotency_key),
  KEY idx_mfg_exception_status(tenant_id, status, priority, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mfg_exception_action (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  exception_id BIGINT NOT NULL,
  action_type VARCHAR(30) NOT NULL DEFAULT 'CONTAINMENT',
  action_description VARCHAR(500) NOT NULL,
  owner_code VARCHAR(64),
  due_at DATETIME NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  completed_at DATETIME NULL,
  completed_by VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_mfg_exception_action(tenant_id, exception_id, status, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Workflow definitions are created by Flowable at runtime; business bindings
-- live in the tenant schema so every business module can select its process.
CREATE TABLE IF NOT EXISTS bpm_process_binding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  business_function VARCHAR(80) NOT NULL,
  process_code VARCHAR(100) NOT NULL,
  updated_by VARCHAR(64) NOT NULL DEFAULT 'admin',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_bpm_binding_tenant_function(tenant_id, business_function),
  KEY idx_bpm_binding_process(tenant_id, process_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
