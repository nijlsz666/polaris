USE polaris_mes;

INSERT INTO sys_tenant(tenant_code, tenant_name, status) VALUES
('demo', '华东一厂', 1),
('south-plant', '南方二厂', 1)
ON DUPLICATE KEY UPDATE tenant_name=VALUES(tenant_name), status=VALUES(status);

INSERT INTO sys_tenant(tenant_code, tenant_name, tenant_type, plan_code, max_users, status)
VALUES ('polaris-admin', 'Polaris 总管理员', 'PLATFORM', 'PLATFORM', 100, 1)
ON DUPLICATE KEY UPDATE tenant_name=VALUES(tenant_name), tenant_type='PLATFORM', plan_code='PLATFORM', max_users=100, status=1;

INSERT INTO sys_role(tenant_id, role_code, role_name, description, status)
SELECT id, 'platform_admin', '总管理员', '跨租户维护租户、授权、计费、积分、服务、培训与营销', 1
FROM sys_tenant WHERE tenant_code='polaris-admin'
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name), description=VALUES(description), status=1;

INSERT INTO sys_user(tenant_id, username, display_name, password_hash, status, role_code)
SELECT id, 'platform-admin', '平台总管理员', 'pbkdf2$210000$cG9sYXJpcy1kZW1vLWFkbWlu$8iEJPJBh5WRbq-zrpztf2zBZJQ02IMcpwDUN9m5k0Nc', 1, 'platform_admin'
FROM sys_tenant WHERE tenant_code='polaris-admin'
ON DUPLICATE KEY UPDATE display_name=VALUES(display_name), password_hash=VALUES(password_hash), role_code='platform_admin', status=1;

INSERT INTO sys_role(tenant_id, role_code, role_name, description)
SELECT t.id, r.role_code, r.role_name, r.description
FROM sys_tenant t
JOIN (SELECT 'admin' role_code, '系统管理员' role_name, '全量菜单、按钮、接口和字段权限' description
      UNION ALL SELECT 'planner', '计划员', 'BOM、生产计划、工单与报表权限'
      UNION ALL SELECT 'warehouse', '仓库管理员', '收发料、库存、条码、调拨与盘点权限'
      UNION ALL SELECT 'quality', '质量管理员', '检验计划、检验批、不合格处置与质量整改'
      UNION ALL SELECT 'operator', '现场操作员', 'PDA 扫码、报工与领料权限') r
WHERE t.tenant_code IN ('demo', 'south-plant')
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name), description=VALUES(description);

INSERT INTO sys_dictionary(tenant_id, dict_type, dict_code, dict_label, dict_value, locale, sort_no, status)
SELECT t.id, d.dict_type, d.dict_code, d.dict_label, d.dict_value, 'zh-CN', d.sort_no, 1
FROM sys_tenant t
JOIN (SELECT 'APPROVAL_ACTION' dict_type, 'APPROVED' dict_code, '同意' dict_label, '同意' dict_value, 1 sort_no
      UNION ALL SELECT 'APPROVAL_ACTION', 'REJECTED', '驳回', '驳回', 2
      UNION ALL SELECT 'APPROVAL_ACTION', 'TRANSFERRED', '转办', '转办', 3
      UNION ALL SELECT 'BUSINESS_STATUS', 'REVIEW', '待评审', '待评审', 1
      UNION ALL SELECT 'BUSINESS_STATUS', 'CONFIRMED', '已确认', '已确认', 2
      UNION ALL SELECT 'BUSINESS_STATUS', 'COMPLETED', '已完成', '已完成', 9
      UNION ALL SELECT 'COMMON_STATUS', 'ACTIVE', '已启用', 'ACTIVE', 1
      UNION ALL SELECT 'COMMON_STATUS', 'INACTIVE', '已停用', 'INACTIVE', 2) d
ON DUPLICATE KEY UPDATE dict_label=VALUES(dict_label), dict_value=VALUES(dict_value), sort_no=VALUES(sort_no), status=1;

INSERT INTO sys_user(tenant_id, username, display_name, password_hash, status, role_code)
SELECT t.id, u.username, u.display_name, u.password_hash, 1, u.role_code
FROM sys_tenant t
JOIN (SELECT 'admin' username, '平台管理员' display_name, 'pbkdf2$210000$cG9sYXJpcy1kZW1vLWFkbWlu$8iEJPJBh5WRbq-zrpztf2zBZJQ02IMcpwDUN9m5k0Nc' password_hash, 'admin' role_code
      UNION ALL SELECT 'planner', '生产计划员', 'pbkdf2$210000$cG9sYXJpcy1kZW1vLXBsYW4$H4c55wZ5Ah9ak7jzYVO5Avov-YDzjGmwYyk_y1wjt8g', 'planner'
      UNION ALL SELECT 'warehouse', '仓库管理员', 'pbkdf2$210000$cG9sYXJpcy1kZW1vLXdhcmU$8ivSS5Ui2U22ynQ2wDIxCe92LGVEdBsvOHO-UFj3GW4', 'warehouse') u
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE display_name=VALUES(display_name), role_code=VALUES(role_code), password_hash=VALUES(password_hash);

INSERT INTO sys_user(tenant_id, username, display_name, password_hash, status, role_code)
SELECT id, 'admin', '南方二厂管理员', 'pbkdf2$210000$cG9sYXJpcy1kZW1vLWFkbWlu$8iEJPJBh5WRbq-zrpztf2zBZJQ02IMcpwDUN9m5k0Nc', 1, 'admin'
FROM sys_tenant WHERE tenant_code='south-plant'
ON DUPLICATE KEY UPDATE display_name=VALUES(display_name), role_code=VALUES(role_code), password_hash=VALUES(password_hash);

INSERT INTO sys_menu(tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, icon, sort_no)
SELECT t.id, 0, m.menu_code, m.menu_name, m.menu_type, m.route_path, m.icon, m.sort_no
FROM sys_tenant t
JOIN (SELECT 'dashboard' menu_code, '工作台' menu_name, 'MENU' menu_type, '/dashboard' route_path, 'grid' icon, 10 sort_no
      UNION ALL SELECT 'manufacturing', '制造管理', 'GROUP', '/manufacturing', 'factory', 20
      UNION ALL SELECT 'warehouse', '仓储管理', 'GROUP', '/warehouse', 'warehouse', 30
      UNION ALL SELECT 'quality', '质量管理', 'GROUP', '/quality', 'shield', 40
      UNION ALL SELECT 'design', '设计中心', 'GROUP', '/design', 'sliders', 50
      UNION ALL SELECT 'admin', '系统管理', 'GROUP', '/admin', 'settings', 90) m
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), route_path=VALUES(route_path);

INSERT INTO sys_menu(tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, icon, sort_no)
SELECT t.id, p.id, m.menu_code, m.menu_name, 'MENU', m.route_path, m.icon, m.sort_no
FROM sys_tenant t
JOIN (SELECT 'bom' menu_code, 'BOM 管理' menu_name, '/manufacturing/bom' route_path, 'layers' icon, 21 sort_no, 'manufacturing' parent_code
      UNION ALL SELECT 'production-plan', '生产计划', '/manufacturing/plan', 'calendar', 22, 'manufacturing'
      UNION ALL SELECT 'work-order', '工单管理', '/manufacturing/work-order', 'clipboard', 23, 'manufacturing'
      UNION ALL SELECT 'warehouse-overview', '仓储总览', '/warehouse/overview', 'grid', 31, 'warehouse'
      UNION ALL SELECT 'material-receipt', '收料 / 上架 / 退料', '/warehouse/inbound', 'inbox', 32, 'warehouse'
      UNION ALL SELECT 'material-outbound', '领料 / 出库 / 报废', '/warehouse/outbound', 'arrow', 33, 'warehouse'
      UNION ALL SELECT 'warehouse-transfer', '调拨 / 移库', '/warehouse/transfer', 'layers', 34, 'warehouse'
      UNION ALL SELECT 'warehouse-count', '盘点与差异', '/warehouse/count', 'clipboard', 35, 'warehouse'
      UNION ALL SELECT 'inventory', '库存与批次', '/warehouse/inventory', 'box', 36, 'warehouse'
      UNION ALL SELECT 'warehouse-trace', '批次追溯', '/warehouse/trace', 'timeline', 37, 'warehouse'
      UNION ALL SELECT 'barcode', '条码管理', '/warehouse/barcode', 'qr', 38, 'warehouse'
      UNION ALL SELECT 'warehouse-master', '仓库主数据', '/warehouse/master', 'settings', 39, 'warehouse'
      UNION ALL SELECT 'quality-overview', '质量总览', '/quality/overview', 'shield', 41, 'quality'
      UNION ALL SELECT 'quality-plans', '检验计划', '/quality/plans', 'clipboard', 42, 'quality'
      UNION ALL SELECT 'quality-lots', '检验批与结果', '/quality/lots', 'scan', 43, 'quality'
      UNION ALL SELECT 'quality-nc', '不合格与整改', '/quality/nonconformance', 'timeline', 44, 'quality'
      UNION ALL SELECT 'reports', '快速报表', '/design/reports', 'chart', 51, 'design'
      UNION ALL SELECT 'low-code', '低代码页面', '/design/low-code', 'layout', 52, 'design'
      UNION ALL SELECT 'big-screen', '大屏展示', '/design/big-screen', 'monitor', 53, 'design'
      UNION ALL SELECT 'users', '用户管理', '/admin/users', 'users', 91, 'admin'
      UNION ALL SELECT 'permissions', '权限管理', '/admin/permissions', 'shield', 92, 'admin') m
JOIN sys_menu p ON p.tenant_id=t.id AND p.menu_code=m.parent_code
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), route_path=VALUES(route_path);

INSERT INTO bom(tenant_id, bom_code, product_code, product_name, version, status, remark)
SELECT t.id, b.bom_code, b.product_code, b.product_name, b.version, b.status, b.remark
FROM sys_tenant t JOIN (SELECT 'BOM-DRONE-001' bom_code, 'FG-DRONE-001' product_code, '工业巡检无人机' product_name, 'V2.1' version, 'RELEASED' status, '量产版本' remark
                        UNION ALL SELECT 'BOM-PUMP-002', 'FG-PUMP-002', '智能变频泵', 'V1.3', 'RELEASED', '含电机与控制器') b
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name), version=VALUES(version), status=VALUES(status);

INSERT INTO production_plan(tenant_id, plan_no, product_code, product_name, plan_qty, released_qty, plan_date, priority, status)
SELECT t.id, 'PLAN-20260819-001', 'FG-DRONE-001', '工业巡检无人机', 120, 80, CURRENT_DATE, 'HIGH', 'RELEASED' FROM sys_tenant t WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty), released_qty=VALUES(released_qty), status=VALUES(status);
INSERT INTO production_plan(tenant_id, plan_no, product_code, product_name, plan_qty, released_qty, plan_date, priority, status)
SELECT t.id, 'PLAN-20260819-002', 'FG-PUMP-002', '智能变频泵', 80, 0, CURRENT_DATE + INTERVAL 1 DAY, 'NORMAL', 'DRAFT' FROM sys_tenant t WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty), released_qty=VALUES(released_qty), status=VALUES(status);

INSERT INTO work_order(tenant_id, order_no, product_code, product_name, plan_qty, completed_qty, status, planned_start, planned_end, work_center)
SELECT t.id, w.order_no, w.product_code, w.product_name, w.plan_qty, w.completed_qty, w.status, w.planned_start, w.planned_end, w.work_center
FROM sys_tenant t JOIN (SELECT 'WO-20260819-001' order_no, 'FG-DRONE-001' product_code, '工业巡检无人机' product_name, 80 plan_qty, 56 completed_qty, 'IN_PROGRESS' status, CURRENT_TIMESTAMP planned_start, CURRENT_TIMESTAMP + INTERVAL 8 HOUR planned_end, '装配一线' work_center
                        UNION ALL SELECT 'WO-20260819-002', 'FG-PUMP-002', '智能变频泵', 40, 40, 'COMPLETED', CURRENT_TIMESTAMP - INTERVAL 1 DAY, CURRENT_TIMESTAMP - INTERVAL 2 HOUR, '泵体装配线'
                        UNION ALL SELECT 'WO-20260819-003', 'FG-DRONE-001', '工业巡检无人机', 40, 0, 'PLANNED', CURRENT_TIMESTAMP + INTERVAL 1 DAY, CURRENT_TIMESTAMP + INTERVAL 2 DAY, '装配一线') w
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE plan_qty=VALUES(plan_qty), completed_qty=VALUES(completed_qty), status=VALUES(status);

INSERT INTO inventory(tenant_id, material_code, material_name, warehouse_code, location_code, batch_no, available_qty, locked_qty, unit, safety_stock)
SELECT t.id, i.material_code, i.material_name, i.warehouse_code, i.location_code, i.batch_no, i.available_qty, i.locked_qty, i.unit, i.safety_stock
FROM sys_tenant t JOIN (SELECT 'RM-MOTOR-001' material_code, '无刷电机' material_name, 'WH-RAW' warehouse_code, 'A-01-01' location_code, 'B20260801' batch_no, 230 available_qty, 20 locked_qty, '件' unit, 100 safety_stock
                        UNION ALL SELECT 'RM-BATTERY-001', '锂电池组', 'WH-RAW', 'A-02-03', 'B20260728', 68, 12, '件', 80
                        UNION ALL SELECT 'RM-CONTROL-001', '主控板', 'WH-RAW', 'A-03-02', 'B20260805', 145, 0, '件', 50
                        UNION ALL SELECT 'FG-DRONE-001', '工业巡检无人机', 'WH-FG', 'F-01-02', 'FG20260818', 56, 0, '台', 20
                        UNION ALL SELECT 'FG-PUMP-002', '智能变频泵', 'WH-FG', 'F-02-01', 'FG20260818', 40, 0, '台', 10) i
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE available_qty=VALUES(available_qty), locked_qty=VALUES(locked_qty);

INSERT INTO material_transaction(tenant_id, transaction_no, transaction_type, material_code, material_name, warehouse_code, location_code, batch_no, quantity, unit, operator_name, remark)
SELECT t.id, x.transaction_no, x.transaction_type, x.material_code, x.material_name, x.warehouse_code, x.location_code, x.batch_no, x.quantity, x.unit, 'warehouse', x.remark
FROM sys_tenant t JOIN (SELECT 'TX-20260819-0001' transaction_no, 'RECEIPT' transaction_type, 'RM-MOTOR-001' material_code, '无刷电机' material_name, 'WH-RAW' warehouse_code, 'A-01-01' location_code, 'B20260801' batch_no, 120 quantity, '件' unit, '供应商来料' remark
                        UNION ALL SELECT 'TX-20260819-0002', 'ISSUE', 'RM-CONTROL-001', '主控板', 'WH-RAW', 'A-03-02', 'B20260805', 24, '件', '工单 WO-20260819-001') x
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE quantity=VALUES(quantity);

INSERT INTO barcode(tenant_id, barcode, barcode_type, material_code, batch_no, status, source_doc_no)
SELECT t.id, b.barcode, b.barcode_type, b.material_code, b.batch_no, 'ACTIVE', b.source_doc_no
FROM sys_tenant t JOIN (SELECT 'PDA-QR-0000001' barcode, 'MATERIAL' barcode_type, 'RM-MOTOR-001' material_code, 'B20260801' batch_no, 'TX-20260819-0001' source_doc_no
                        UNION ALL SELECT 'PDA-QR-0000002', 'PRODUCT', 'FG-DRONE-001', 'FG20260818', 'WO-20260819-001') b
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE status=VALUES(status);

INSERT INTO wh_warehouse(tenant_id, warehouse_code, warehouse_name, warehouse_type, owner_code, status, remark)
SELECT t.id, w.warehouse_code, w.warehouse_name, w.warehouse_type, w.owner_code, 'ACTIVE', w.remark
FROM sys_tenant t
JOIN (SELECT 'WH-RAW' warehouse_code, '原材料仓' warehouse_name, 'RAW' warehouse_type, 'demo' owner_code, '来料、退料和生产备料' remark
      UNION ALL SELECT 'WH-FG', '成品仓', 'FINISHED', 'demo', '完工入库和销售出库'
      UNION ALL SELECT 'WH-HOLD', '质量隔离仓', 'QUARANTINE', 'quality', '来料检验和不合格品隔离') w
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE warehouse_name=VALUES(warehouse_name), warehouse_type=VALUES(warehouse_type), remark=VALUES(remark);

INSERT INTO wh_storage_area(tenant_id, warehouse_code, area_code, area_name, area_type, status)
SELECT t.id, a.warehouse_code, a.area_code, a.area_name, a.area_type, 'ACTIVE'
FROM sys_tenant t
JOIN (SELECT 'WH-RAW' warehouse_code, 'RAW-A' area_code, '原材料货架区' area_name, 'NORMAL' area_type
      UNION ALL SELECT 'WH-RAW', 'RAW-Q', '来料待检区', 'QC'
      UNION ALL SELECT 'WH-FG', 'FG-A', '成品存储区', 'NORMAL'
      UNION ALL SELECT 'WH-HOLD', 'HOLD-A', '质量隔离区', 'QUARANTINE') a
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE area_name=VALUES(area_name), area_type=VALUES(area_type);

INSERT INTO wh_location(tenant_id, warehouse_code, area_code, location_code, location_name, location_type, capacity_qty, status)
SELECT t.id, l.warehouse_code, l.area_code, l.location_code, l.location_name, 'BIN', l.capacity_qty, 'AVAILABLE'
FROM sys_tenant t
JOIN (SELECT 'WH-RAW' warehouse_code, 'RAW-A' area_code, 'A-01-01' location_code, '电机货位' location_name, 1000 capacity_qty
      UNION ALL SELECT 'WH-RAW', 'RAW-A', 'A-02-03', '电池货位', 500
      UNION ALL SELECT 'WH-RAW', 'RAW-A', 'A-03-02', '主控板货位', 500
      UNION ALL SELECT 'WH-RAW', 'RAW-Q', 'Q-01-01', '来料待检货位', 500
      UNION ALL SELECT 'WH-FG', 'FG-A', 'F-01-02', '无人机成品货位', 200
      UNION ALL SELECT 'WH-FG', 'FG-A', 'F-02-01', '变频泵成品货位', 200
      UNION ALL SELECT 'WH-HOLD', 'HOLD-A', 'H-01-01', '不合格隔离货位', 300) l
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE location_name=VALUES(location_name), capacity_qty=VALUES(capacity_qty);

INSERT INTO wh_material(tenant_id, material_code, material_name, material_type, unit, lot_control, serial_control, shelf_life_days, safety_stock, status, remark)
SELECT t.id, m.material_code, m.material_name, m.material_type, m.unit, 1, m.serial_control, m.shelf_life_days, m.safety_stock, 'ACTIVE', m.remark
FROM sys_tenant t
JOIN (SELECT 'RM-MOTOR-001' material_code, '无刷电机' material_name, 'RAW' material_type, '件' unit, 0 serial_control, 0 shelf_life_days, 100 safety_stock, '采购来料，按批次管理' remark
      UNION ALL SELECT 'RM-BATTERY-001', '锂电池组', 'RAW', '件', 1, 365, 80, '按序列号和有效期管理'
      UNION ALL SELECT 'RM-CONTROL-001', '主控板', 'RAW', '件', 1, 0, 50, '按批次管理'
      UNION ALL SELECT 'FG-DRONE-001', '工业巡检无人机', 'FINISHED', '台', 1, 0, 20, '成品序列号追溯'
      UNION ALL SELECT 'FG-PUMP-002', '智能变频泵', 'FINISHED', '台', 1, 0, 10, '成品序列号追溯') m
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE material_name=VALUES(material_name), material_type=VALUES(material_type), unit=VALUES(unit), safety_stock=VALUES(safety_stock);

INSERT INTO wh_batch(tenant_id, material_code, batch_no, production_date, expiry_date, supplier_code, quality_status, batch_status, remark)
SELECT t.id, b.material_code, b.batch_no, b.production_date, b.expiry_date, b.supplier_code, b.quality_status, 'ACTIVE', b.remark
FROM sys_tenant t
JOIN (SELECT 'RM-MOTOR-001' material_code, 'B20260801' batch_no, CURRENT_DATE - INTERVAL 18 DAY production_date, NULL expiry_date, 'SUP-MOTOR' supplier_code, 'PASSED' quality_status, '来料检验合格' remark
      UNION ALL SELECT 'RM-BATTERY-001', 'B20260728', CURRENT_DATE - INTERVAL 22 DAY, CURRENT_DATE + INTERVAL 343 DAY, 'SUP-BATTERY', 'PASSED', '有效期内'
      UNION ALL SELECT 'RM-CONTROL-001', 'B20260805', CURRENT_DATE - INTERVAL 14 DAY, NULL, 'SUP-PCB', 'PASSED', '来料检验合格'
      UNION ALL SELECT 'FG-DRONE-001', 'FG20260818', CURRENT_DATE - INTERVAL 1 DAY, NULL, 'SELF', 'PASSED', '完工入库') b
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE quality_status=VALUES(quality_status), batch_status=VALUES(batch_status), expiry_date=VALUES(expiry_date);

INSERT INTO wh_barcode_rule(tenant_id, rule_code, rule_name, barcode_type, prefix, sequence_no, status)
SELECT t.id, r.rule_code, r.rule_name, r.barcode_type, r.prefix, r.sequence_no, 'ACTIVE'
FROM sys_tenant t
JOIN (SELECT 'MATERIAL' rule_code, '物料条码' rule_name, 'MATERIAL' barcode_type, 'MAT' prefix, 1 sequence_no
      UNION ALL SELECT 'PRODUCT', '成品条码', 'PRODUCT', 'FG', 1
      UNION ALL SELECT 'PALLET', '托盘条码', 'PALLET', 'PLT', 1) r
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE rule_name=VALUES(rule_name), prefix=VALUES(prefix), status=VALUES(status);

INSERT INTO qm_inspection_plan(tenant_id, plan_code, plan_name, inspection_type, material_code, product_code, sampling_method, version, status, effective_from, created_by)
SELECT t.id, 'IQC-MOTOR-001', '无刷电机来料检验', 'INCOMING', 'RM-MOTOR-001', NULL, 'FULL', 'V1', 'RELEASED', CURRENT_DATE - INTERVAL 90 DAY, 'quality'
FROM sys_tenant t WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE plan_name=VALUES(plan_name), status=VALUES(status), version=VALUES(version), effective_from=VALUES(effective_from);

INSERT INTO qm_inspection_plan_item(tenant_id, plan_id, characteristic_code, characteristic_name, result_type, standard_text, lower_limit, upper_limit, unit, required_flag, sort_no)
SELECT t.id, p.id, i.characteristic_code, i.characteristic_name, i.result_type, i.standard_text, i.lower_limit, i.upper_limit, i.unit, 1, i.sort_no
FROM sys_tenant t JOIN qm_inspection_plan p ON p.tenant_id=t.id AND p.plan_code='IQC-MOTOR-001'
JOIN (SELECT 'VOLTAGE' characteristic_code, '额定电压' characteristic_name, 'QUANTITATIVE' result_type, '9 ~ 11' standard_text, 9 lower_limit, 11 upper_limit, 'V' unit, 10 sort_no
      UNION ALL SELECT 'APPEARANCE', '外观', 'QUALITATIVE', '无裂纹、无磕碰', NULL, NULL, NULL, 20) i
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE characteristic_name=VALUES(characteristic_name), result_type=VALUES(result_type), standard_text=VALUES(standard_text), lower_limit=VALUES(lower_limit), upper_limit=VALUES(upper_limit), unit=VALUES(unit), sort_no=VALUES(sort_no);

INSERT INTO qm_supplier_evaluation(tenant_id, evaluation_no, supplier_code, supplier_name, evaluation_period, delivery_score, quality_score, service_score, price_score, total_score, grade, status, owner_code, evaluated_at, remark, created_by)
SELECT t.id, 'SUP-EVAL-2026-Q3-001', 'SUP-DG-001', '东莞精工电子', '2026-Q3', 96, 94, 90, 88, 92.6, 'A', 'SUBMITTED', 'quality', CURRENT_DATE, '交付稳定，建议继续纳入核心供应商池。', 'quality'
FROM sys_tenant t WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE supplier_name=VALUES(supplier_name), total_score=VALUES(total_score), grade=VALUES(grade), status=VALUES(status), remark=VALUES(remark);

INSERT INTO qm_avl_entry(tenant_id, material_code, material_name, supplier_code, supplier_name, supplier_part_no, approval_status, valid_from, valid_to, last_evaluation_score, approved_by, approved_at, remark, created_by)
SELECT t.id, 'RM-MOTOR-001', '无刷电机', 'SUP-DG-001', '东莞精工电子', 'MTR-2208', 'APPROVED', CURRENT_DATE - INTERVAL 30 DAY, CURRENT_DATE + INTERVAL 335 DAY, 92.6, 'quality', CURRENT_TIMESTAMP, '主供物料，需按 IQC-MOTOR-001 检验。', 'quality'
FROM sys_tenant t WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE material_name=VALUES(material_name), supplier_name=VALUES(supplier_name), approval_status=VALUES(approval_status), valid_to=VALUES(valid_to), last_evaluation_score=VALUES(last_evaluation_score), remark=VALUES(remark);

INSERT INTO qm_ipqc_record(tenant_id, ipqc_no, line_code, work_order_no, process_code, process_name, product_code, product_name, batch_no, sample_qty, inspected_qty, defect_qty, first_piece_status, status, inspector, started_at, remark, created_by)
SELECT t.id, 'IPQC-20260821-001', 'LINE-01', 'WO-20260821-001', 'ASM', '总装首件', 'FG-MOTOR-001', '无刷电机总成', 'B20260821001', 5, 3, 0, 'PASS', 'IN_PROGRESS', 'quality', CURRENT_TIMESTAMP, '首件已确认，等待完成本轮巡检。', 'quality'
FROM sys_tenant t WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE status=VALUES(status), inspected_qty=VALUES(inspected_qty), first_piece_status=VALUES(first_piece_status), remark=VALUES(remark);

INSERT INTO report_definition(tenant_id, report_code, report_name, source_table, chart_type, config_json)
SELECT t.id, r.report_code, r.report_name, r.source_table, r.chart_type, r.config_json
FROM sys_tenant t JOIN (SELECT 'RPT-WORK-STATUS' report_code, '工单状态分布' report_name, 'work_order' source_table, 'PIE' chart_type, '{"dimension":"status","metric":"count(*)","filters":["deleted=0"]}' config_json
                        UNION ALL SELECT 'RPT-INVENTORY-ALERT', '库存预警清单', 'inventory', 'TABLE', '{"columns":["material_code","material_name","available_qty","safety_stock"],"filters":["available_qty < safety_stock"]}') r
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE report_name=VALUES(report_name), config_json=VALUES(config_json);

INSERT INTO lowcode_page(tenant_id, page_code, page_name, page_type, schema_json, status)
SELECT t.id, p.page_code, p.page_name, 'FORM', p.schema_json, p.status FROM sys_tenant t
JOIN (SELECT 'PAGE-RECEIPT' page_code, '收料单快速录入' page_name, '{"fields":[{"name":"materialCode","label":"物料编码","type":"barcode"},{"name":"warehouseCode","label":"仓库编码","type":"text"},{"name":"locationCode","label":"库位编码","type":"text"},{"name":"quantity","label":"数量","type":"number"},{"name":"batchNo","label":"批次","type":"text"}],"submitApi":"/api/warehouse/transactions"}' schema_json, 'PUBLISHED' status
      UNION ALL SELECT 'PAGE-QUALITY', '来料检验登记', '{"fields":[{"name":"sourceDocNo","label":"送货单号","type":"text"},{"name":"result","label":"检验结论","type":"select"}]}', 'DRAFT') p
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE page_name=VALUES(page_name), schema_json=VALUES(schema_json);

INSERT INTO dashboard_config(tenant_id, dashboard_code, dashboard_name, layout_json, theme)
SELECT t.id, 'DSH-FACTORY', '工厂运营驾驶舱', '{"widgets":["kpi","order-status","inventory-alert","output-trend"],"refreshSeconds":60}', 'dark-blue'
FROM sys_tenant t WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE dashboard_name=VALUES(dashboard_name), layout_json=VALUES(layout_json);

INSERT INTO sys_permission(tenant_id, role_code, resource_type, resource_code, action_code, field_mask_json)
SELECT t.id, p.role_code, p.resource_type, p.resource_code, p.action_code, p.field_mask_json
FROM sys_tenant t JOIN (SELECT 'admin' role_code, 'MENU' resource_type, '*' resource_code, '*' action_code, '{}' field_mask_json
      UNION ALL SELECT 'planner', 'MENU', 'manufacturing', 'VIEW', '{}'
      UNION ALL SELECT 'planner', 'API', '/api/manufacturing/**', 'WRITE', '{}'
      UNION ALL SELECT 'warehouse', 'MENU', 'warehouse', 'VIEW', '{}'
      UNION ALL SELECT 'warehouse', 'API', '/api/warehouse/**', 'WRITE', '{"cost_price":"DENY"}'
      UNION ALL SELECT 'quality', 'MENU', 'quality', 'VIEW', '{}'
      UNION ALL SELECT 'quality', 'API', '/api/quality/**', 'WRITE', '{}'
      UNION ALL SELECT 'operator', 'MENU', 'work-order', 'VIEW', '{"salary":"DENY"}'
      UNION ALL SELECT 'operator', 'API', '/api/manufacturing/work-orders/*/report', 'WRITE', '{"cost_price":"DENY"}') p
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE action_code=VALUES(action_code), field_mask_json=VALUES(field_mask_json);

INSERT INTO sys_menu(tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, icon, sort_no)
SELECT t.id, p.id, 'operations-control', '现场控制塔', 'MENU', '/manufacturing/operations', 'activity', 24
FROM sys_tenant t JOIN sys_menu p ON p.tenant_id=t.id AND p.menu_code='manufacturing'
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), route_path=VALUES(route_path), icon=VALUES(icon);

INSERT INTO mfg_equipment(tenant_id, equipment_code, equipment_name, work_center, model, status, health_score, current_work_order, next_maintenance_at, remark)
SELECT t.id, e.equipment_code, e.equipment_name, e.work_center, e.model, e.status, e.health_score, e.current_work_order, CURRENT_TIMESTAMP + INTERVAL e.maintenance_days DAY, e.remark
FROM sys_tenant t CROSS JOIN (SELECT 'ASM-01' equipment_code, '一线总装工位' equipment_name, '装配一线' work_center, 'FANUC-ASM' model, 'RUNNING' status, 96 health_score, 'WO-20260819-001' current_work_order, 12 maintenance_days, '关键装配工位' remark
                       UNION ALL SELECT 'TEST-02', '电机性能测试台', '测试中心', 'TEST-BENCH-2', 'IDLE', 88, NULL, 5, '测试数据需与质量批次关联'
                       UNION ALL SELECT 'PACK-01', '成品包装线', '包装一线', 'PACK-AUTO-01', 'MAINTENANCE', 72, NULL, 1, '待更换封箱刀片') e
WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE equipment_name=VALUES(equipment_name), work_center=VALUES(work_center), status=VALUES(status), health_score=VALUES(health_score), current_work_order=VALUES(current_work_order), remark=VALUES(remark);

INSERT INTO mfg_exception(tenant_id, exception_no, category, priority, source_type, source_ref, equipment_code, work_center, work_order_no, title, description, impact_qty, owner_code, due_at, status, created_by)
SELECT t.id, 'EXC-20260820-001', 'MATERIAL', 'HIGH', 'WORK_ORDER', 'WO-20260819-001', NULL, '装配一线', 'WO-20260819-001', '电池组齐套不足', '工单剩余 24 台，电池组可用量低于齐套需求，计划员需要在发料前确认替代料或补货。', 24, 'planner', CURRENT_TIMESTAMP + INTERVAL 4 HOUR, 'ACKNOWLEDGED', 'planner'
FROM sys_tenant t WHERE t.tenant_code='demo'
ON DUPLICATE KEY UPDATE title=VALUES(title), description=VALUES(description), status=VALUES(status), due_at=VALUES(due_at);

INSERT INTO mfg_exception_action(tenant_id, exception_id, action_type, action_description, owner_code, due_at, status)
SELECT t.id, e.id, 'CONTAINMENT', '核对南方二厂库存并确认跨仓调拨可行性', 'warehouse', CURRENT_TIMESTAMP + INTERVAL 2 HOUR, 'OPEN'
FROM sys_tenant t JOIN mfg_exception e ON e.tenant_id=t.id AND e.exception_no='EXC-20260820-001'
WHERE t.tenant_code='demo'
  AND NOT EXISTS (SELECT 1 FROM mfg_exception_action a WHERE a.tenant_id=t.id AND a.exception_id=e.id);
