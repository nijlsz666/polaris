package com.polaris.mes.service.impl;

import com.polaris.mes.annotation.AuditOperation;
import com.polaris.mes.annotation.RequireRole;
import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.ErpService;
import com.polaris.mes.service.MrpService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MRP application service.  The calculation deliberately snapshots every
 * supply source used by the run, so a planner can explain why a shortage was
 * created after inventory or purchase orders change.
 */
@Service
@Transactional
public class MrpServiceImpl implements MrpService {
    private static final Set<String> RUN_STATUSES = Set.of("CALCULATED", "CONFIRMED", "CANCELLED");
    private static final Set<String> SHORTAGE_STATUSES = Set.of("OPEN", "CALLED", "PARTIAL", "RESOLVED", "CANCELLED");
    private static final Set<String> CALL_STATUSES = Set.of("DRAFT", "RELEASED", "IN_PICKING", "PARTIAL", "COMPLETED", "CANCELLED");
    private static final Set<String> ASN_STATUSES = Set.of("DRAFT", "SUBMITTED", "CONFIRMED", "RECEIVING", "RECEIVED", "CANCELLED");
    private final JdbcTemplate jdbc;
    private final ErpService erp;

    public MrpServiceImpl(JdbcTemplate jdbc, ErpService erp) { this.jdbc = jdbc; this.erp = erp; }

    @Override
    @RequireRole({"admin", "planner"})
    @AuditOperation(action = "MRP_RUN", resource = "MRP_RUN")
    public Map<String, Object> run(Map<String, Object> payload) {
        String productCode = required(payload, "productCode", "产品编码");
        BigDecimal planQty = positiveDecimal(payload.get("planQty"), "计划数量");
        LocalDate planDate = date(payload.get("planDate"), LocalDate.now());
        Map<String, Object> bom = one("select id, bom_code, product_code, product_name, version from bom where tenant_id=? and product_code=? and status='RELEASED' and deleted=0 order by id desc limit 1", tenantId(), productCode);
        if (bom == null) throw new IllegalArgumentException("未找到产品对应的已发布 BOM");

        String runNo = generatedNo("MRP");
        jdbc.update("insert into mrp_run(tenant_id, run_no, product_code, product_name, plan_qty, plan_date, bom_id, bom_code, bom_version, source_type, source_doc_no, priority, status, created_by) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                tenantId(), runNo, productCode, bom.get("product_name"), planQty, planDate, bom.get("id"), bom.get("bom_code"), bom.get("version"),
                string(payload.get("sourceType")), string(payload.get("sourceDocNo")), stringOr(payload.get("priority"), "NORMAL").toUpperCase(Locale.ROOT), "CALCULATED", actor());
        long runId = ((Number) scalar("select id from mrp_run where tenant_id=? and run_no=?", tenantId(), runNo)).longValue();

        List<Map<String, Object>> requirements = new ArrayList<>();
        List<Map<String, Object>> items = normalizedRows(jdbc.queryForList("select id, material_code, material_name, quantity, unit, loss_rate, issue_method from bom_item where tenant_id=? and bom_id=? order by id", tenantId(), bom.get("id")));
        int lineNo = 1;
        for (Map<String, Object> item : items) {
            BigDecimal quantityPer = decimal(item.get("quantity"));
            BigDecimal lossRate = decimal(item.get("loss_rate"));
            BigDecimal gross = quantityPer.multiply(planQty).multiply(BigDecimal.ONE.add(lossRate.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)));
            BigDecimal requiredQty = ceil(gross);
            Map<String, Object> supply = supply(item.get("material_code"));
            BigDecimal safetyStock = safetyStock(item.get("material_code"), supply);
            BigDecimal availableQty = decimal(supply.get("availableQty"));
            BigDecimal reservedQty = decimal(supply.get("reservedQty"));
            BigDecimal lockedQty = decimal(supply.get("lockedQty"));
            BigDecimal inTransitQty = decimal(supply.get("inTransitQty"));
            BigDecimal openPoQty = decimal(supply.get("openPoQty"));
            // Inventory.available_qty is already reduced by reservation/lock in WMS.
            // They are reported separately for visibility, but are not subtracted twice.
            BigDecimal netShortage = requiredQty.add(safetyStock).subtract(availableQty).subtract(inTransitQty).subtract(openPoQty);
            if (netShortage.compareTo(BigDecimal.ZERO) < 0) netShortage = BigDecimal.ZERO;
            BigDecimal coveredQty = requiredQty.add(safetyStock).subtract(netShortage);
            if (coveredQty.compareTo(BigDecimal.ZERO) < 0) coveredQty = BigDecimal.ZERO;
            String shortageStatus = netShortage.signum() > 0 ? "OPEN" : "RESOLVED";
            jdbc.update("insert into mrp_requirement(tenant_id, run_id, line_no, material_code, material_name, unit, quantity_per, loss_rate, gross_required_qty, safety_stock_qty, required_qty, available_qty, reserved_qty, locked_qty, in_transit_qty, open_po_qty, covered_qty, net_shortage_qty, due_date, issue_method, shortage_status) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    tenantId(), runId, lineNo++, item.get("material_code"), item.get("material_name"), item.get("unit"), quantityPer, lossRate, gross, safetyStock, requiredQty,
                    availableQty, reservedQty, lockedQty, inTransitQty, openPoQty, coveredQty, netShortage, planDate, item.get("issue_method"), shortageStatus);
            long requirementId = ((Number) scalar("select id from mrp_requirement where tenant_id=? and run_id=? and line_no=?", tenantId(), runId, lineNo - 1)).longValue();
            Map<String, Object> requirement = requirement(item, quantityPer, lossRate, gross, safetyStock, requiredQty, supply, coveredQty, netShortage, planDate);
            requirement.put("id", requirementId);
            requirement.put("lineNo", lineNo - 1);
            requirement.put("suggestion", netShortage.signum() > 0 ? "采购 / 委外 / 调拨" : "库存可满足");
            requirements.add(requirement);
            if (netShortage.signum() > 0) {
                String shortageNo = generatedNo("SH");
                jdbc.update("insert into mrp_shortage(tenant_id, shortage_no, run_id, requirement_id, material_code, material_name, unit, shortage_qty, resolved_qty, required_date, priority, source_type, source_doc_no, status, owner_code) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        tenantId(), shortageNo, runId, requirementId, item.get("material_code"), item.get("material_name"), item.get("unit"), netShortage, BigDecimal.ZERO, planDate,
                        stringOr(payload.get("priority"), "NORMAL").toUpperCase(Locale.ROOT), string(payload.get("sourceType")), string(payload.get("sourceDocNo")), "OPEN", actor());
            }
        }
        return runResult(runId, bom, planQty, planDate, requirements);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRuns(String status) {
        String sql = "select id, run_no, product_code, product_name, plan_qty, plan_date, bom_code, bom_version, source_type, source_doc_no, priority, status, created_by, created_at from mrp_run where tenant_id=?";
        List<Object> args = new ArrayList<>(List.of(tenantId()));
        if (notBlank(status)) { sql += " and status=?"; args.add(status.toUpperCase(Locale.ROOT)); }
        sql += " order by id desc";
        return normalizedRows(jdbc.queryForList(sql, args.toArray()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> detailRun(long id) {
        Map<String, Object> run = one("select id, run_no, product_code, product_name, plan_qty, plan_date, bom_id, bom_code, bom_version, source_type, source_doc_no, priority, status, created_by, created_at from mrp_run where tenant_id=? and id=?", tenantId(), id);
        if (run == null) throw new IllegalArgumentException("MRP 运算批次不存在");
        Map<String, Object> result = new LinkedHashMap<>(run);
        List<Map<String, Object>> requirements = normalizedRows(jdbc.queryForList("select id, line_no, material_code, material_name, unit, quantity_per, loss_rate, gross_required_qty, safety_stock_qty, required_qty, available_qty, reserved_qty, locked_qty, in_transit_qty, open_po_qty, covered_qty, net_shortage_qty, due_date, issue_method, shortage_status from mrp_requirement where tenant_id=? and run_id=? order by line_no", tenantId(), id));
        result.put("requirements", requirements.stream().map(this::requirementRow).toList());
        result.put("shortageCount", scalar("select count(*) from mrp_shortage where tenant_id=? and run_id=? and status<>'RESOLVED' and status<>'CANCELLED'", tenantId(), id));
        result.put("shortageQty", scalar("select coalesce(sum(shortage_qty-resolved_qty),0) from mrp_shortage where tenant_id=? and run_id=? and status<>'RESOLVED' and status<>'CANCELLED'", tenantId(), id));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listShortages(String status, String keyword) {
        StringBuilder sql = new StringBuilder("select id, shortage_no, run_id, requirement_id, material_code, material_name, unit, shortage_qty, resolved_qty, required_date, priority, source_type, source_doc_no, procurement_record_no, status, owner_code, created_at, updated_at from mrp_shortage where tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(tenantId()));
        if (notBlank(status)) { sql.append(" and status=?"); args.add(status.toUpperCase(Locale.ROOT)); }
        if (notBlank(keyword)) { sql.append(" and (shortage_no like ? or material_code like ? or material_name like ? or coalesce(source_doc_no,'') like ?)"); String like = "%" + keyword.trim() + "%"; args.add(like); args.add(like); args.add(like); args.add(like); }
        sql.append(" order by case priority when 'URGENT' then 0 when 'HIGH' then 1 else 2 end, required_date, id desc");
        return normalizedRows(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @Override
    @RequireRole({"admin", "planner"})
    @AuditOperation(action = "MRP_SHORTAGE_TO_PR", resource = "MRP_SHORTAGE")
    public Map<String, Object> createPurchaseRequisition(long shortageId, Map<String, Object> payload) {
        payload = payload == null ? Map.of() : payload;
        Map<String, Object> shortage = shortage(shortageId);
        if (Set.of("RESOLVED", "CANCELLED").contains(String.valueOf(shortage.get("status")))) throw new IllegalArgumentException("该缺料已关闭，不能转采购");
        if (notBlank(string(shortage.get("procurement_record_no")))) return erp.detailRecord("procurement", ((Number) scalar("select id from erp_business_record where tenant_id=? and record_no=?", tenantId(), shortage.get("procurement_record_no"))).longValue());
        BigDecimal remaining = decimal(shortage.get("shortage_qty")).subtract(decimal(shortage.get("resolved_qty")));
        if (remaining.signum() <= 0) throw new IllegalArgumentException("该缺料没有可采购数量");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", "REQUISITIONS"); request.put("name", "MRP 缺料采购申请 - " + shortage.get("material_name")); request.put("partner", stringOr(payload.get("supplierName"), "待采购员询价"));
        request.put("departmentCode", stringOr(payload.get("departmentCode"), "PURCHASE")); request.put("requesterCode", actor()); request.put("deliveryDate", shortage.get("required_date")); request.put("sourceType", "MRP_SHORTAGE"); request.put("sourceDocNo", shortage.get("shortage_no"));
        request.put("remark", stringOr(payload.get("remark"), "由 MRP 缺料单自动生成"));
        request.put("lines", List.of(Map.of("materialCode", shortage.get("material_code"), "materialName", shortage.get("material_name"), "unit", shortage.get("unit"), "requestedQty", remaining, "requiredDate", shortage.get("required_date"), "sourceRef", shortage.get("shortage_no"))));
        Map<String, Object> created = erp.createRecord("procurement", request);
        jdbc.update("update mrp_shortage set procurement_record_no=?, owner_code=?, updated_at=current_timestamp where tenant_id=? and id=?", created.get("no"), actor(), tenantId(), shortageId);
        return created;
    }

    @Override
    @RequireRole({"admin", "planner", "operator", "warehouse"})
    @AuditOperation(action = "MATERIAL_CALL_CREATE", resource = "MATERIAL_CALL")
    public Map<String, Object> createMaterialCall(long shortageId, Map<String, Object> payload, String actor) {
        Map<String, Object> shortage = shortage(shortageId);
        String currentStatus = String.valueOf(shortage.get("status"));
        if (Set.of("RESOLVED", "CANCELLED").contains(currentStatus)) throw new IllegalArgumentException("该缺料已关闭，不能叫料");
        BigDecimal alreadyCalled = decimal(scalar("select coalesce(sum(requested_qty),0) from material_call where tenant_id=? and shortage_id=? and status not in ('COMPLETED','CANCELLED')", tenantId(), shortageId));
        BigDecimal remaining = decimal(shortage.get("shortage_qty")).subtract(decimal(shortage.get("resolved_qty"))).subtract(alreadyCalled);
        if (remaining.signum() <= 0) throw new IllegalArgumentException("该缺料已有未完成的叫料单，请先处理现有叫料");
        BigDecimal requested = payload != null && payload.get("requestedQty") != null ? positiveDecimal(payload.get("requestedQty"), "叫料数量") : remaining;
        if (requested.compareTo(remaining) > 0) throw new IllegalArgumentException("叫料数量不能超过当前未解决缺料");
        String callNo = generatedNo("CALL");
        jdbc.update("insert into material_call(tenant_id, call_no, shortage_id, work_order_no, material_code, material_name, unit, requested_qty, issued_qty, required_at, priority, from_warehouse_code, to_warehouse_code, requested_by, assigned_to, status, remark) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                tenantId(), callNo, shortageId, string(payload == null ? null : payload.get("workOrderNo")), shortage.get("material_code"), shortage.get("material_name"), shortage.get("unit"), requested, BigDecimal.ZERO,
                dateTime(payload == null ? null : payload.get("requiredAt"), LocalDateTime.now()), stringOr(payload == null ? null : payload.get("priority"), String.valueOf(shortage.get("priority"))).toUpperCase(Locale.ROOT),
                string(payload == null ? null : payload.get("fromWarehouseCode")), string(payload == null ? null : payload.get("toWarehouseCode")), actor, string(payload == null ? null : payload.get("assignedTo")), "DRAFT", string(payload == null ? null : payload.get("remark")));
        jdbc.update("update mrp_shortage set status='CALLED', owner_code=?, updated_at=current_timestamp where tenant_id=? and id=? and status not in ('RESOLVED','CANCELLED')", actor, tenantId(), shortageId);
        long id = ((Number) scalar("select id from material_call where tenant_id=? and call_no=?", tenantId(), callNo)).longValue();
        return call(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMaterialCalls(String status, String keyword) {
        StringBuilder sql = new StringBuilder("select id, call_no, shortage_id, work_order_no, material_code, material_name, unit, requested_qty, issued_qty, required_at, priority, from_warehouse_code, to_warehouse_code, requested_by, assigned_to, status, remark, created_at, updated_at from material_call where tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(tenantId()));
        if (notBlank(status)) { sql.append(" and status=?"); args.add(status.toUpperCase(Locale.ROOT)); }
        if (notBlank(keyword)) { sql.append(" and (call_no like ? or work_order_no like ? or material_code like ? or material_name like ?)"); String like = "%" + keyword.trim() + "%"; args.add(like); args.add(like); args.add(like); args.add(like); }
        sql.append(" order by case priority when 'URGENT' then 0 when 'HIGH' then 1 else 2 end, required_at, id desc");
        return normalizedRows(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @Override
    @RequireRole({"admin", "planner", "operator", "warehouse"})
    @AuditOperation(action = "MATERIAL_CALL_TRANSITION", resource = "MATERIAL_CALL")
    public Map<String, Object> transitionMaterialCall(long id, Map<String, Object> payload, String actor) {
        Map<String, Object> current = call(id);
        String from = String.valueOf(current.get("status"));
        String to = required(payload, "status", "目标状态").toUpperCase(Locale.ROOT);
        if (!CALL_STATUSES.contains(to) || !callTransition(from, to)) throw new IllegalArgumentException("叫料单状态不能从 " + from + " 流转到 " + to);
        BigDecimal issued = payload.get("issuedQty") == null ? decimal(current.get("requested_qty")) : positiveDecimal(payload.get("issuedQty"), "已发数量");
        if (issued.compareTo(decimal(current.get("requested_qty"))) > 0) throw new IllegalArgumentException("已发数量不能超过叫料数量");
        jdbc.update("update material_call set status=?, issued_qty=?, assigned_to=coalesce(?,assigned_to), updated_at=current_timestamp where tenant_id=? and id=? and status=?", to, issued, string(payload.get("assignedTo")), tenantId(), id, from);
        if ("COMPLETED".equals(to) || "PARTIAL".equals(to)) {
            long shortageId = ((Number) current.get("shortage_id")).longValue();
            Map<String, Object> shortage = shortage(shortageId);
            BigDecimal resolved = decimal(scalar("select coalesce(sum(issued_qty),0) from material_call where tenant_id=? and shortage_id=? and status in ('PARTIAL','COMPLETED')", tenantId(), shortageId));
            resolved = resolved.min(decimal(shortage.get("shortage_qty")));
            jdbc.update("update mrp_shortage set resolved_qty=?, status=?, updated_at=current_timestamp where tenant_id=? and id=?", resolved, resolved.compareTo(decimal(shortage.get("shortage_qty"))) >= 0 ? "RESOLVED" : "PARTIAL", tenantId(), shortageId);
        }
        if ("CANCELLED".equals(to)) jdbc.update("update mrp_shortage set status=case when resolved_qty>=shortage_qty then 'RESOLVED' else 'OPEN' end, updated_at=current_timestamp where tenant_id=? and id=?", tenantId(), current.get("shortage_id"));
        return call(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAsns(String status, String keyword) {
        StringBuilder sql = new StringBuilder("select id, asn_no, purchase_order_no, supplier_code, supplier_name, expected_arrival, warehouse_code, carrier, tracking_no, status, created_by, submitted_at, received_at, remark, created_at, updated_at from asn where tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(tenantId()));
        if (notBlank(status)) { sql.append(" and status=?"); args.add(status.toUpperCase(Locale.ROOT)); }
        if (notBlank(keyword)) { sql.append(" and (asn_no like ? or purchase_order_no like ? or supplier_code like ? or supplier_name like ?)"); String like = "%" + keyword.trim() + "%"; args.add(like); args.add(like); args.add(like); args.add(like); }
        sql.append(" order by expected_arrival, id desc");
        return normalizedRows(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @Override
    @RequireRole({"admin", "planner"})
    @AuditOperation(action = "ASN_CREATE", resource = "ASN")
    public Map<String, Object> createAsn(Map<String, Object> payload, String actor) {
        String purchaseOrderNo = required(payload, "purchaseOrderNo", "采购订单号");
        String asnNo = stringOr(payload.get("asnNo"), generatedNo("ASN"));
        List<Map<String, Object>> lines = asnLines(payload, purchaseOrderNo);
        if (lines.isEmpty()) throw new IllegalArgumentException("ASN 至少需要一条物料明细");
        try {
            jdbc.update("insert into asn(tenant_id, asn_no, purchase_order_no, supplier_code, supplier_name, expected_arrival, warehouse_code, carrier, tracking_no, status, created_by, remark) values(?,?,?,?,?,?,?,?,?,?,?,?)",
                    tenantId(), asnNo, purchaseOrderNo, string(payload.get("supplierCode")), stringOr(payload.get("supplierName"), "待补充"), date(payload.get("expectedArrival"), LocalDate.now()),
                    stringOr(payload.get("warehouseCode"), "WH-RAW"), string(payload.get("carrier")), string(payload.get("trackingNo")), "DRAFT", actor, string(payload.get("remark")));
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("ASN 编码已存在：" + asnNo); }
        long asnId = ((Number) scalar("select id from asn where tenant_id=? and asn_no=?", tenantId(), asnNo)).longValue();
        int lineNo = 1;
        for (Map<String, Object> line : lines) {
            BigDecimal planned = positiveDecimal(first(line, "plannedQty", "requestedQty", "quantity"), "计划到货数量");
            BigDecimal shipped = line.get("shippedQty") == null ? planned : positiveDecimal(line.get("shippedQty"), "发运数量");
            if (shipped.compareTo(planned) > 0) throw new IllegalArgumentException("ASN 发运数量不能超过计划数量");
            jdbc.update("insert into asn_line(tenant_id, asn_id, line_no, po_line_id, material_code, material_name, unit, planned_qty, shipped_qty, received_qty, batch_no, production_date, expiry_date, quality_status, remark) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    tenantId(), asnId, lineNo++, numberOrNull(line.get("poLineId")), required(line, "materialCode", "ASN 物料编码"), required(line, "materialName", "ASN 物料名称"), stringOr(line.get("unit"), "件"), planned, shipped, BigDecimal.ZERO,
                    string(line.get("batchNo")), date(line.get("productionDate"), null), date(line.get("expiryDate"), null), stringOr(line.get("qualityStatus"), "PENDING"), string(line.get("remark")));
        }
        return detailAsn(asnId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> detailAsn(long id) {
        Map<String, Object> head = one("select id, asn_no, purchase_order_no, supplier_code, supplier_name, expected_arrival, warehouse_code, carrier, tracking_no, status, created_by, submitted_at, received_at, remark, created_at, updated_at from asn where tenant_id=? and id=?", tenantId(), id);
        if (head == null) throw new IllegalArgumentException("ASN 单不存在");
        Map<String, Object> result = new LinkedHashMap<>(head);
        result.put("lines", normalizedRows(jdbc.queryForList("select id, line_no, po_line_id, material_code, material_name, unit, planned_qty, shipped_qty, received_qty, batch_no, production_date, expiry_date, quality_status, remark from asn_line where tenant_id=? and asn_id=? order by line_no", tenantId(), id)));
        return result;
    }

    @Override
    @RequireRole({"admin", "planner", "warehouse"})
    @AuditOperation(action = "ASN_TRANSITION", resource = "ASN")
    public Map<String, Object> transitionAsn(long id, Map<String, Object> payload, String actor) {
        Map<String, Object> current = detailAsn(id);
        String from = String.valueOf(current.get("status"));
        String to = required(payload, "status", "目标状态").toUpperCase(Locale.ROOT);
        if (!ASN_STATUSES.contains(to) || !asnTransition(from, to)) throw new IllegalArgumentException("ASN 状态不能从 " + from + " 流转到 " + to);
        if ("RECEIVED".equals(to)) {
            if (!"warehouse".equals(role()) && !"admin".equals(role())) throw new IllegalArgumentException("只有仓库角色可以确认 ASN 收货");
            receiveAsn(current, payload, actor);
        }
        String timestamp = "SUBMITTED".equals(to) ? ",submitted_at=current_timestamp" : "RECEIVED".equals(to) ? ",received_at=current_timestamp" : "";
        jdbc.update("update asn set status=?" + timestamp + ", updated_at=current_timestamp where tenant_id=? and id=? and status=?", to, tenantId(), id, from);
        return detailAsn(id);
    }

    private void receiveAsn(Map<String, Object> asn, Map<String, Object> payload, String actor) {
        List<Map<String, Object>> lines = normalizedRows(jdbc.queryForList("select id, po_line_id, material_code, material_name, unit, planned_qty, shipped_qty, received_qty, batch_no from asn_line where tenant_id=? and asn_id=? order by line_no", tenantId(), asn.get("id")));
        for (Map<String, Object> line : lines) {
            BigDecimal received = payload != null && payload.get("receivedQtyByLine") instanceof Map<?, ?> map && map.get(String.valueOf(line.get("id"))) != null
                    ? positiveDecimal(map.get(String.valueOf(line.get("id"))), "收货数量") : decimal(line.get("shipped_qty"));
            if (received.compareTo(decimal(line.get("shipped_qty"))) > 0) throw new IllegalArgumentException("收货数量不能超过发运数量");
            jdbc.update("update asn_line set received_qty=?, quality_status=coalesce(?,quality_status) where tenant_id=? and id=?", received, payload == null ? null : string(payload.get("qualityStatus")), tenantId(), line.get("id"));
            if (line.get("po_line_id") != null) jdbc.update("update erp_business_record_line set delivered_qty=least(requested_qty, delivered_qty+?) where tenant_id=? and id=?", received, tenantId(), line.get("po_line_id"));
        }
        syncPurchaseOrder(String.valueOf(asn.get("purchase_order_no")));
    }

    private void syncPurchaseOrder(String purchaseOrderNo) {
        Map<String, Object> po = one("select id from erp_business_record where tenant_id=? and domain='PROCUREMENT' and record_no=?", tenantId(), purchaseOrderNo);
        if (po == null) return;
        Number open = (Number) scalar("select count(*) from erp_business_record_line where tenant_id=? and record_id=? and delivered_qty<requested_qty", tenantId(), po.get("id"));
        Number delivered = (Number) scalar("select count(*) from erp_business_record_line where tenant_id=? and record_id=? and delivered_qty>0", tenantId(), po.get("id"));
        String status = open.intValue() == 0 ? "RECEIVED" : delivered.intValue() > 0 ? "IN_TRANSIT" : "ORDERED";
        jdbc.update("update erp_business_record set status=?, updated_at=current_timestamp where tenant_id=? and id=? and domain='PROCUREMENT'", status, tenantId(), po.get("id"));
    }

    private List<Map<String, Object>> asnLines(Map<String, Object> payload, String purchaseOrderNo) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object raw = payload.get("lines");
        if (raw instanceof List<?> list) for (Object item : list) if (item instanceof Map<?, ?> source) {
            Map<String, Object> line = new LinkedHashMap<>(); source.forEach((key, value) -> line.put(String.valueOf(key), value)); result.add(line);
        }
        if (!result.isEmpty()) return result;
        Map<String, Object> po = one("select id from erp_business_record where tenant_id=? and record_no=? and domain='PROCUREMENT'", tenantId(), purchaseOrderNo);
        if (po == null) throw new IllegalArgumentException("采购订单不存在：" + purchaseOrderNo);
        return normalizedRows(jdbc.queryForList("select id po_line_id, material_code, material_name, unit, requested_qty planned_qty, requested_qty-delivered_qty open_qty from erp_business_record_line where tenant_id=? and record_id=? and requested_qty>delivered_qty order by line_no", tenantId(), po.get("id"))).stream().map(row -> {
            Map<String, Object> line = new LinkedHashMap<>(); line.put("poLineId", row.get("po_line_id")); line.put("materialCode", row.get("material_code")); line.put("materialName", row.get("material_name")); line.put("unit", row.get("unit")); line.put("plannedQty", row.get("open_qty")); return line;
        }).toList();
    }

    private Map<String, Object> runResult(long runId, Map<String, Object> bom, BigDecimal planQty, LocalDate planDate, List<Map<String, Object>> requirements) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", runId); result.put("runId", runId); result.put("runNo", scalar("select run_no from mrp_run where tenant_id=? and id=?", tenantId(), runId));
        result.put("productCode", bom.get("product_code")); result.put("productName", bom.get("product_name")); result.put("planQty", planQty); result.put("planDate", planDate);
        result.put("bomCode", bom.get("bom_code")); result.put("bomVersion", bom.get("version")); result.put("materialCount", requirements.size());
        long shortageCount = requirements.stream().filter(row -> decimal(row.get("netShortageQty")).signum() > 0).count();
        BigDecimal shortageQty = requirements.stream().map(row -> decimal(row.get("netShortageQty"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("shortageCount", shortageCount); result.put("shortageQty", shortageQty); result.put("netShortageQty", shortageQty); result.put("requirements", requirements);
        result.put("status", "CALCULATED"); return result;
    }

    private Map<String, Object> requirement(Map<String, Object> item, BigDecimal quantityPer, BigDecimal lossRate, BigDecimal gross, BigDecimal safety, BigDecimal required, Map<String, Object> supply, BigDecimal covered, BigDecimal shortage, LocalDate dueDate) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("materialCode", item.get("material_code")); row.put("materialName", item.get("material_name")); row.put("unit", item.get("unit")); row.put("quantityPer", quantityPer); row.put("lossRate", lossRate); row.put("grossRequiredQty", gross); row.put("safetyStockQty", safety); row.put("requiredQty", required);
        row.put("availableQty", supply.get("availableQty")); row.put("reservedQty", supply.get("reservedQty")); row.put("lockedQty", supply.get("lockedQty")); row.put("inTransitQty", supply.get("inTransitQty")); row.put("openPoQty", supply.get("openPoQty")); row.put("coveredQty", covered); row.put("netShortageQty", shortage); row.put("shortageQty", shortage); row.put("dueDate", dueDate); row.put("issueMethod", item.get("issue_method")); return row;
    }

    private Map<String, Object> requirementRow(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id")); result.put("lineNo", row.get("line_no")); result.put("materialCode", row.get("material_code")); result.put("materialName", row.get("material_name")); result.put("unit", row.get("unit")); result.put("quantityPer", row.get("quantity_per")); result.put("lossRate", row.get("loss_rate")); result.put("grossRequiredQty", row.get("gross_required_qty")); result.put("safetyStockQty", row.get("safety_stock_qty")); result.put("requiredQty", row.get("required_qty")); result.put("availableQty", row.get("available_qty")); result.put("reservedQty", row.get("reserved_qty")); result.put("lockedQty", row.get("locked_qty")); result.put("inTransitQty", row.get("in_transit_qty")); result.put("openPoQty", row.get("open_po_qty")); result.put("coveredQty", row.get("covered_qty")); result.put("netShortageQty", row.get("net_shortage_qty")); result.put("shortageQty", row.get("net_shortage_qty")); result.put("dueDate", row.get("due_date")); result.put("issueMethod", row.get("issue_method")); result.put("shortageStatus", row.get("shortage_status")); result.put("suggestion", decimal(row.get("net_shortage_qty")).signum() > 0 ? "采购 / 委外 / 调拨" : "库存可满足"); return result;
    }

    private Map<String, Object> supply(Object materialCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> stock = one("select coalesce(sum(available_qty),0) available_qty, coalesce(sum(reserved_qty),0) reserved_qty, coalesce(sum(locked_qty),0) locked_qty, coalesce(sum(in_transit_qty),0) in_transit_qty from inventory where tenant_id=? and material_code=? and stock_status='AVAILABLE'", tenantId(), materialCode);
        result.put("availableQty", stock == null ? BigDecimal.ZERO : decimal(stock.get("available_qty"))); result.put("reservedQty", stock == null ? BigDecimal.ZERO : decimal(stock.get("reserved_qty"))); result.put("lockedQty", stock == null ? BigDecimal.ZERO : decimal(stock.get("locked_qty"))); result.put("inTransitQty", stock == null ? BigDecimal.ZERO : decimal(stock.get("in_transit_qty")));
        result.put("openPoQty", openPoQty(String.valueOf(materialCode))); return result;
    }

    private BigDecimal safetyStock(Object materialCode, Map<String, Object> supply) {
        try { Map<String, Object> row = one("select coalesce(max(safety_stock),0) safety_stock from wh_material where tenant_id=? and material_code=? and status='ACTIVE'", tenantId(), materialCode); if (row != null && row.get("safety_stock") != null) return decimal(row.get("safety_stock")); } catch (DataAccessException ignored) {}
        return decimal(supply.get("safetyStockQty"));
    }

    private BigDecimal openPoQty(String materialCode) {
        try { Map<String, Object> row = one("select coalesce(sum(greatest(l.requested_qty-l.delivered_qty,0)),0) open_qty from erp_business_record r join erp_business_record_line l on l.tenant_id=r.tenant_id and l.record_id=r.id where r.tenant_id=? and r.domain='PROCUREMENT' and r.record_type in ('ORDERS','PURCHASE_ORDERS') and r.status in ('APPROVED','ORDERED','IN_TRANSIT') and l.material_code=?", tenantId(), materialCode); return row == null ? BigDecimal.ZERO : decimal(row.get("open_qty")); } catch (DataAccessException ignored) { return BigDecimal.ZERO; }
    }

    private Map<String, Object> shortage(long id) { Map<String, Object> row = one("select id, shortage_no, run_id, requirement_id, material_code, material_name, unit, shortage_qty, resolved_qty, required_date, priority, source_type, source_doc_no, procurement_record_no, status, owner_code from mrp_shortage where tenant_id=? and id=?", tenantId(), id); if (row == null) throw new IllegalArgumentException("缺料记录不存在"); return row; }
    private Map<String, Object> call(long id) { Map<String, Object> row = one("select id, call_no, shortage_id, work_order_no, material_code, material_name, unit, requested_qty, issued_qty, required_at, priority, from_warehouse_code, to_warehouse_code, requested_by, assigned_to, status, remark, created_at, updated_at from material_call where tenant_id=? and id=?", tenantId(), id); if (row == null) throw new IllegalArgumentException("叫料单不存在"); return row; }
    private boolean callTransition(String from, String to) { return switch (from) { case "DRAFT" -> Set.of("RELEASED", "CANCELLED").contains(to); case "RELEASED" -> Set.of("IN_PICKING", "CANCELLED").contains(to); case "IN_PICKING" -> Set.of("PARTIAL", "COMPLETED").contains(to); case "PARTIAL" -> Set.of("COMPLETED").contains(to); default -> false; }; }
    private boolean asnTransition(String from, String to) { return switch (from) { case "DRAFT" -> Set.of("SUBMITTED", "CANCELLED").contains(to); case "SUBMITTED" -> Set.of("CONFIRMED", "CANCELLED").contains(to); case "CONFIRMED" -> Set.of("RECEIVING", "CANCELLED").contains(to); case "RECEIVING" -> Set.of("RECEIVED").contains(to); default -> false; }; }
    private String role() { return TenantContext.require().roleCode(); }
    private String actor() { return TenantContext.require().username(); }
    private long tenantId() { return TenantContext.require().tenantId(); }
    private static String generatedNo(String prefix) { return prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT); }
    private Map<String, Object> one(String sql, Object... args) { List<Map<String, Object>> rows = normalizedRows(jdbc.queryForList(sql, args)); return rows.isEmpty() ? null : rows.get(0); }
    private Object scalar(String sql, Object... args) { return jdbc.queryForObject(sql, Object.class, args); }
    private List<Map<String, Object>> normalizedRows(List<Map<String, Object>> rows) { return rows.stream().map(row -> { Map<String, Object> normalized = new LinkedHashMap<>(); row.forEach((key, value) -> normalized.put(String.valueOf(key).toLowerCase(Locale.ROOT), value)); return normalized; }).toList(); }
    private static BigDecimal decimal(Object value) { if (value == null || String.valueOf(value).isBlank()) return BigDecimal.ZERO; return new BigDecimal(String.valueOf(value)); }
    private static BigDecimal ceil(BigDecimal value) { return value.setScale(0, RoundingMode.CEILING); }
    private static BigDecimal positiveDecimal(Object value, String label) { BigDecimal result = decimal(value); if (result.signum() <= 0) throw new IllegalArgumentException(label + "必须大于 0"); return result; }
    private static String required(Map<String, Object> payload, String key, String label) { String value = string(payload == null ? null : payload.get(key)); if (!notBlank(value)) throw new IllegalArgumentException(label + "不能为空"); return value.trim(); }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static String stringOr(Object value, String fallback) { String result = string(value); return notBlank(result) ? result : fallback; }
    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private static LocalDate date(Object value, LocalDate fallback) { if (value == null || String.valueOf(value).isBlank()) return fallback; try { return LocalDate.parse(String.valueOf(value).substring(0, 10)); } catch (RuntimeException ex) { throw new IllegalArgumentException("日期格式不正确：" + value); } }
    private static LocalDateTime dateTime(Object value, LocalDateTime fallback) { if (value == null || String.valueOf(value).isBlank()) return fallback; try { return LocalDateTime.parse(String.valueOf(value).replace("Z", "")); } catch (RuntimeException ex) { return fallback; } }
    private static Object first(Map<String, Object> row, String... keys) { for (String key : keys) if (row.get(key) != null) return row.get(key); return null; }
    private static Object numberOrNull(Object value) { if (value == null || String.valueOf(value).isBlank()) return null; try { return Long.valueOf(String.valueOf(value)); } catch (NumberFormatException ex) { return null; } }
}
