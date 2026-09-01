package com.polaris.mes.service.impl;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.QualityService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quality application service. Inspection lots are the quality boundary between
 * receiving/production and available stock. Every result and disposition is
 * persisted so a warehouse transaction can be traced back to a quality event.
 */
@Service
public class QualityServiceImpl implements QualityService {
    private static final List<String> INSPECTION_TYPES = List.of("INCOMING", "PROCESS", "FINAL", "STOCK");
    private static final List<String> LOT_STATUSES = List.of("PENDING", "IN_PROGRESS", "PASSED", "FAILED", "CANCELLED");
    private static final List<String> NC_SEVERITIES = List.of("MINOR", "MAJOR", "CRITICAL");
    private static final List<String> DISPOSITIONS = List.of("REWORK", "SCRAP", "RETURN", "HOLD", "USE_AS_IS");

    private final JdbcTemplate jdbc;

    public QualityServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> summary() {
        long tenant = tenantId();
        long totalLots = number(scalar("select count(*) from qm_inspection_lot where tenant_id=? and created_at >= current_date - 30", tenant));
        long passedLots = number(scalar("select count(*) from qm_inspection_lot where tenant_id=? and status='PASSED' and created_at >= current_date - 30", tenant));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pendingLots", scalar("select count(*) from qm_inspection_lot where tenant_id=? and status in ('PENDING','IN_PROGRESS')", tenant));
        result.put("failedLots", scalar("select count(*) from qm_inspection_lot where tenant_id=? and status='FAILED'", tenant));
        result.put("openNonconformances", scalar("select count(*) from qm_nonconformance where tenant_id=? and status<>'CLOSED'", tenant));
        result.put("overdueActions", scalar("select count(*) from qm_corrective_action where tenant_id=? and status<>'CLOSED' and due_date is not null and due_date < current_date", tenant));
        result.put("todayInspections", scalar("select count(*) from qm_inspection_lot where tenant_id=? and date(created_at)=current_date", tenant));
        result.put("last30DaysLots", totalLots);
        result.put("last30DaysPassRate", totalLots == 0 ? 0 : Math.round(passedLots * 10000.0 / totalLots) / 100.0);
        result.put("byType", jdbc.queryForList("select inspection_type as type, status, count(*) as count from qm_inspection_lot where tenant_id=? and created_at >= current_date - 30 group by inspection_type, status order by inspection_type, status", tenant));
        result.put("pendingSupplierEvaluations", scalar("select count(*) from qm_supplier_evaluation where tenant_id=? and status in ('DRAFT','SUBMITTED')", tenant));
        result.put("pendingAvl", scalar("select count(*) from qm_avl_entry where tenant_id=? and approval_status='PENDING'", tenant));
        result.put("openIpqc", scalar("select count(*) from qm_ipqc_record where tenant_id=? and status in ('PENDING','IN_PROGRESS')", tenant));
        return result;
    }

    public List<Map<String, Object>> listPlans(String status, String keyword) {
        StringBuilder sql = new StringBuilder("select p.id, p.plan_code, p.plan_name, p.inspection_type, p.material_code, p.product_code, p.sampling_method, p.version, p.status, p.effective_from, p.effective_to, p.created_by, p.created_at, p.updated_at, (select count(*) from qm_inspection_plan_item i where i.tenant_id=p.tenant_id and i.plan_id=p.id) as item_count from qm_inspection_plan p where p.tenant_id=?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId());
        if (!blank(status)) { sql.append(" and p.status=?"); args.add(status); }
        if (!blank(keyword)) { String like = "%" + keyword.trim() + "%"; sql.append(" and (p.plan_code like ? or p.plan_name like ? or coalesce(p.material_code,'') like ? or coalesce(p.product_code,'') like ?)"); args.add(like); args.add(like); args.add(like); args.add(like); }
        sql.append(" order by p.updated_at desc, p.id desc");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> getPlan(long id) {
        Map<String, Object> plan = one("select id, plan_code, plan_name, inspection_type, material_code, product_code, sampling_method, version, status, effective_from, effective_to, created_by, created_at, updated_at from qm_inspection_plan where tenant_id=? and id=?", tenantId(), id);
        if (plan == null) throw new IllegalArgumentException("检验计划不存在");
        plan.put("items", listPlanItems(id));
        return plan;
    }

    private List<Map<String, Object>> listPlanItems(long planId) {
        return jdbc.queryForList("select id, plan_id, characteristic_code, characteristic_name, result_type, standard_text, lower_limit, upper_limit, unit, required_flag, sort_no from qm_inspection_plan_item where tenant_id=? and plan_id=? order by sort_no, id", tenantId(), planId);
    }

    @Transactional
    public Map<String, Object> savePlan(Map<String, Object> payload, long id, String actor) {
        String code = required(payload, "planCode", "检验计划编码");
        String name = required(payload, "planName", "检验计划名称");
        String type = stringOr(payload.get("inspectionType"), "INCOMING").toUpperCase();
        if (!INSPECTION_TYPES.contains(type)) throw new IllegalArgumentException("不支持的检验类型: " + type);
        long planId = id;
        if (planId > 0) {
            int updated = jdbc.update("update qm_inspection_plan set plan_code=?, plan_name=?, inspection_type=?, material_code=?, product_code=?, sampling_method=?, version=?, status=?, effective_from=?, effective_to=? where tenant_id=? and id=?", code, name, type, string(payload.get("materialCode")), string(payload.get("productCode")), stringOr(payload.get("samplingMethod"), "FULL"), stringOr(payload.get("version"), "V1"), stringOr(payload.get("status"), "DRAFT"), date(payload.get("effectiveFrom")), date(payload.get("effectiveTo")), tenantId(), planId);
            if (updated == 0) throw new IllegalArgumentException("检验计划不存在");
        } else {
            jdbc.update("insert into qm_inspection_plan(tenant_id, plan_code, plan_name, inspection_type, material_code, product_code, sampling_method, version, status, effective_from, effective_to, created_by) values(?,?,?,?,?,?,?,?,?,?,?,?)", tenantId(), code, name, type, string(payload.get("materialCode")), string(payload.get("productCode")), stringOr(payload.get("samplingMethod"), "FULL"), stringOr(payload.get("version"), "V1"), stringOr(payload.get("status"), "DRAFT"), date(payload.get("effectiveFrom")), date(payload.get("effectiveTo")), actor);
            planId = number(scalar("select id from qm_inspection_plan where tenant_id=? and plan_code=?", tenantId(), code));
        }
        Object itemValue = payload.get("items");
        if (itemValue instanceof List<?> rawItems) {
            jdbc.update("delete from qm_inspection_plan_item where tenant_id=? and plan_id=?", tenantId(), planId);
            int sort = 10;
            for (Object raw : rawItems) {
                if (!(raw instanceof Map<?, ?>)) continue;
                Map<String, Object> item = map(raw);
                String itemCode = required(item, "characteristicCode", "检验特性编码");
                String itemName = required(item, "characteristicName", "检验特性名称");
                jdbc.update("insert into qm_inspection_plan_item(tenant_id, plan_id, characteristic_code, characteristic_name, result_type, standard_text, lower_limit, upper_limit, unit, required_flag, sort_no) values(?,?,?,?,?,?,?,?,?,?,?)", tenantId(), planId, itemCode, itemName, stringOr(item.get("resultType"), "QUALITATIVE"), string(item.get("standardText")), decimal(item.get("lowerLimit")), decimal(item.get("upperLimit")), string(item.get("unit")), flag(item.get("requiredFlag"), true), number(item.get("sortNo"), sort));
                sort += 10;
            }
        }
        return getPlan(planId);
    }

    public List<Map<String, Object>> listLots(String status, String keyword, String inspectionType) {
        StringBuilder sql = new StringBuilder("select l.id, l.lot_no, l.plan_id, p.plan_code, l.inspection_type, l.source_type, l.source_doc_no, l.work_order_no, l.material_code, l.material_name, l.batch_no, l.warehouse_code, l.location_code, l.sample_qty, l.inspected_qty, l.status, l.inspector, l.started_at, l.completed_at, l.remark, l.created_at, l.updated_at from qm_inspection_lot l left join qm_inspection_plan p on p.tenant_id=l.tenant_id and p.id=l.plan_id where l.tenant_id=?");
        List<Object> args = new ArrayList<>(); args.add(tenantId());
        if (!blank(status)) { sql.append(" and l.status=?"); args.add(status); }
        if (!blank(inspectionType)) { sql.append(" and l.inspection_type=?"); args.add(inspectionType); }
        if (!blank(keyword)) { String like = "%" + keyword.trim() + "%"; sql.append(" and (l.lot_no like ? or l.material_code like ? or coalesce(l.material_name,'') like ? or coalesce(l.batch_no,'') like ? or coalesce(l.source_doc_no,'') like ?)"); for (int i = 0; i < 5; i++) args.add(like); }
        sql.append(" order by l.id desc");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> createLot(Map<String, Object> payload, String actor) {
        String type = stringOr(payload.get("inspectionType"), "INCOMING").toUpperCase();
        if (!INSPECTION_TYPES.contains(type)) throw new IllegalArgumentException("不支持的检验类型: " + type);
        String materialCode = required(payload, "materialCode", "物料编码");
        String lotNo = stringOr(payload.get("lotNo"), "IQ-" + TenantContext.require().tenantCode() + "-" + UUID.randomUUID());
        long planId = number(payload.get("planId"), 0);
        String productCode = string(payload.get("productCode"));
        if (planId > 0 && one("select id from qm_inspection_plan where tenant_id=? and id=? and inspection_type=? and status='RELEASED' and (effective_from is null or effective_from<=current_date) and (effective_to is null or effective_to>=current_date) and (material_code=? or material_code is null) and (product_code=? or product_code is null)", tenantId(), planId, type, materialCode, productCode) == null) throw new IllegalArgumentException("检验计划不存在、未发布或不适用于当前物料");
        if (planId == 0) planId = findApplicablePlan(type, materialCode, productCode);
        if (planId == 0) throw new IllegalArgumentException("未找到适用的已发布检验计划");
        jdbc.update("insert into qm_inspection_lot(tenant_id, lot_no, plan_id, inspection_type, source_type, source_doc_no, work_order_no, material_code, material_name, batch_no, warehouse_code, location_code, sample_qty, inspected_qty, status, remark) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", tenantId(), lotNo, planId == 0 ? null : planId, type, string(payload.get("sourceType")), string(payload.get("sourceDocNo")), string(payload.get("workOrderNo")), materialCode, string(payload.get("materialName")), string(payload.get("batchNo")), string(payload.get("warehouseCode")), string(payload.get("locationCode")), number(payload.get("sampleQty"), number(payload.get("quantity"), 0)), 0, "PENDING", string(payload.get("remark")));
        long lotId = number(scalar("select id from qm_inspection_lot where tenant_id=? and lot_no=?", tenantId(), lotNo));
        return getLot(lotId);
    }

    public Map<String, Object> getLot(long id) {
        Map<String, Object> lot = one("select l.id, l.lot_no, l.plan_id, p.plan_code, l.inspection_type, l.source_type, l.source_doc_no, l.work_order_no, l.material_code, l.material_name, l.batch_no, l.warehouse_code, l.location_code, l.sample_qty, l.inspected_qty, l.status, l.inspector, l.started_at, l.completed_at, l.remark, l.created_at, l.updated_at from qm_inspection_lot l left join qm_inspection_plan p on p.tenant_id=l.tenant_id and p.id=l.plan_id where l.tenant_id=? and l.id=?", tenantId(), id);
        if (lot == null) throw new IllegalArgumentException("检验批不存在");
        lot.put("items", listLotItems(id));
        lot.put("nonconformances", listNonconformances(null, String.valueOf(lot.get("lot_no"))));
        return lot;
    }

    private List<Map<String, Object>> listLotItems(long lotId) {
        return jdbc.queryForList("select i.id as item_id, i.plan_id, i.characteristic_code, i.characteristic_name, i.result_type, i.standard_text, i.lower_limit, i.upper_limit, i.unit, i.required_flag, i.sort_no, r.id as result_id, r.result_value, r.result_text, coalesce(r.result_status,'PENDING') as result_status, r.inspector, r.remark, r.inspected_at from qm_inspection_lot l join qm_inspection_plan_item i on i.tenant_id=l.tenant_id and i.plan_id=l.plan_id left join qm_inspection_result r on r.tenant_id=l.tenant_id and r.lot_id=l.id and r.item_id=i.id where l.tenant_id=? and l.id=? order by i.sort_no, i.id", tenantId(), lotId);
    }

    @Transactional
    public Map<String, Object> startLot(long id, String actor) {
        int updated = jdbc.update("update qm_inspection_lot set status='IN_PROGRESS', inspector=?, started_at=coalesce(started_at,current_timestamp) where tenant_id=? and id=? and status='PENDING'", actor, tenantId(), id);
        if (updated == 0) throw new IllegalArgumentException("检验批不存在或当前状态不能开始检验");
        return getLot(id);
    }

    @Transactional
    public Map<String, Object> saveResults(long lotId, Map<String, Object> payload, String actor) {
        Map<String, Object> lot = one("select id, plan_id, status from qm_inspection_lot where tenant_id=? and id=?", tenantId(), lotId);
        if (lot == null) throw new IllegalArgumentException("检验批不存在");
        if (List.of("PASSED", "FAILED", "CANCELLED").contains(String.valueOf(lot.get("status")))) throw new IllegalArgumentException("检验批已结束，不能修改检验结果");
        Object value = payload.get("items");
        if (!(value instanceof List<?> items)) throw new IllegalArgumentException("检验结果不能为空");
        for (Object raw : items) {
            if (!(raw instanceof Map<?, ?>)) continue;
            Map<String, Object> item = map(raw);
            long itemId = number(item.get("itemId"), number(item.get("item_id"), 0));
            if (itemId == 0 || one("select id from qm_inspection_plan_item where tenant_id=? and plan_id=? and id=?", tenantId(), lot.get("plan_id"), itemId) == null) throw new IllegalArgumentException("检验项目不属于当前检验计划");
            Map<String, Object> definition = one("select result_type, lower_limit, upper_limit from qm_inspection_plan_item where tenant_id=? and id=?", tenantId(), itemId);
            BigDecimal resultValue = decimal(item.get("resultValue"), item.get("result_value"));
            String resultText = string(item.get("resultText"));
            String resultStatus = resultStatus(definition, resultValue, resultText, string(item.get("resultStatus")));
            Map<String, Object> existing = one("select id from qm_inspection_result where tenant_id=? and lot_id=? and item_id=?", tenantId(), lotId, itemId);
            if (existing == null) jdbc.update("insert into qm_inspection_result(tenant_id, lot_id, item_id, result_value, result_text, result_status, inspector, remark, inspected_at) values(?,?,?,?,?,?,?,?,current_timestamp)", tenantId(), lotId, itemId, resultValue, resultText, resultStatus, actor, string(item.get("remark")));
            else jdbc.update("update qm_inspection_result set result_value=?, result_text=?, result_status=?, inspector=?, remark=?, inspected_at=current_timestamp where tenant_id=? and lot_id=? and item_id=?", resultValue, resultText, resultStatus, actor, string(item.get("remark")), tenantId(), lotId, itemId);
        }
        return getLot(lotId);
    }

    @Transactional
    public Map<String, Object> completeLot(long id, String actor) {
        Map<String, Object> lot = one("select id, material_code, batch_no, status from qm_inspection_lot where tenant_id=? and id=?", tenantId(), id);
        if (lot == null) throw new IllegalArgumentException("检验批不存在");
        if (!List.of("PENDING", "IN_PROGRESS").contains(String.valueOf(lot.get("status")))) throw new IllegalArgumentException("当前检验批不能完成");
        List<Map<String, Object>> items = listLotItems(id);
        for (Map<String, Object> item : items) if (number(item.get("required_flag"), 1) == 1 && "PENDING".equals(String.valueOf(item.get("result_status")))) throw new IllegalArgumentException("必检项目尚未完成: " + item.get("characteristic_name"));
        boolean failed = items.stream().anyMatch(item -> "FAIL".equals(String.valueOf(item.get("result_status"))));
        String status = failed ? "FAILED" : "PASSED";
        int inspectedQty = number(scalar("select coalesce(sum(case when result_status<>'PENDING' then 1 else 0 end),0) from qm_inspection_result where tenant_id=? and lot_id=?", tenantId(), id), 0);
        jdbc.update("update qm_inspection_lot set status=?, inspected_qty=?, inspector=?, completed_at=current_timestamp where tenant_id=? and id=?", status, inspectedQty, actor, tenantId(), id);
        String materialCode = string(lot.get("material_code")); String batchNo = string(lot.get("batch_no"));
        if (!blank(batchNo)) {
            jdbc.update("update wh_batch set quality_status=? where tenant_id=? and material_code=? and batch_no=?", status, tenantId(), materialCode, batchNo);
            try {
                jdbc.update("update inventory set stock_status=? where tenant_id=? and material_code=? and batch_no=?", failed ? "HOLD" : "AVAILABLE", tenantId(), materialCode, batchNo);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // Inventory is optional in lightweight quality-only deployments and tests.
            }
        }
        if (failed && one("select id from qm_nonconformance where tenant_id=? and lot_id=? and status<>'CLOSED'", tenantId(), id) == null) createNonconformance(Map.of("lotId", id, "defectCode", "INSPECTION_FAIL", "defectName", "检验未通过", "severity", "MAJOR", "defectQty", number(lot.get("sample_qty"), 0), "containmentAction", "隔离待处置"), actor);
        return getLot(id);
    }

    public List<Map<String, Object>> listNonconformances(String status, String keyword) {
        StringBuilder sql = new StringBuilder("select n.id, n.nc_no, n.lot_id, l.lot_no, n.source_type, n.source_doc_no, n.material_code, n.batch_no, n.defect_code, n.defect_name, n.severity, n.defect_qty, n.status, n.disposition, n.containment_action, n.root_cause, n.corrective_action, n.owner_code, n.due_date, n.closed_by, n.closed_at, n.created_by, n.created_at, n.updated_at from qm_nonconformance n left join qm_inspection_lot l on l.tenant_id=n.tenant_id and l.id=n.lot_id where n.tenant_id=?");
        List<Object> args = new ArrayList<>(); args.add(tenantId());
        if (!blank(status)) { sql.append(" and n.status=?"); args.add(status); }
        if (!blank(keyword)) { String like = "%" + keyword.trim() + "%"; sql.append(" and (n.nc_no like ? or n.material_code like ? or n.defect_code like ? or n.defect_name like ? or coalesce(n.source_doc_no,'') like ? or coalesce(l.lot_no,'') like ?)"); for (int i = 0; i < 6; i++) args.add(like); }
        sql.append(" order by n.id desc");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> createNonconformance(Map<String, Object> payload, String actor) {
        long lotId = number(payload.get("lotId"), 0);
        Map<String, Object> lot = lotId == 0 ? null : one("select lot_no, source_doc_no, material_code, batch_no from qm_inspection_lot where tenant_id=? and id=?", tenantId(), lotId);
        if (lotId > 0 && lot == null) throw new IllegalArgumentException("关联检验批不存在");
        String defectCode = required(payload, "defectCode", "缺陷编码");
        String defectName = required(payload, "defectName", "缺陷名称");
        String severity = stringOr(payload.get("severity"), "MINOR").toUpperCase();
        if (!NC_SEVERITIES.contains(severity)) throw new IllegalArgumentException("不支持的严重等级: " + severity);
        String ncNo = stringOr(payload.get("ncNo"), "NC-" + TenantContext.require().tenantCode() + "-" + UUID.randomUUID());
        jdbc.update("insert into qm_nonconformance(tenant_id, nc_no, lot_id, source_type, source_doc_no, material_code, batch_no, defect_code, defect_name, severity, defect_qty, status, disposition, containment_action, owner_code, due_date, created_by) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", tenantId(), ncNo, lotId == 0 ? null : lotId, stringOr(payload.get("sourceType"), "INSPECTION"), lot == null ? string(payload.get("sourceDocNo")) : lot.get("source_doc_no"), lot == null ? string(payload.get("materialCode")) : lot.get("material_code"), lot == null ? string(payload.get("batchNo")) : lot.get("batch_no"), defectCode, defectName, severity, number(payload.get("defectQty"), 0), "OPEN", string(payload.get("disposition")), string(payload.get("containmentAction")), string(payload.get("ownerCode")), date(payload.get("dueDate")), actor);
        long id = number(scalar("select id from qm_nonconformance where tenant_id=? and nc_no=?", tenantId(), ncNo));
        return one("select id, nc_no, lot_id, source_type, source_doc_no, material_code, batch_no, defect_code, defect_name, severity, defect_qty, status, disposition, containment_action, root_cause, corrective_action, owner_code, due_date, closed_by, closed_at, created_by, created_at from qm_nonconformance where tenant_id=? and id=?", tenantId(), id);
    }

    @Transactional
    public Map<String, Object> updateDisposition(long id, Map<String, Object> payload, String actor) {
        String disposition = stringOr(payload.get("disposition"), "").toUpperCase();
        if (!DISPOSITIONS.contains(disposition)) throw new IllegalArgumentException("不支持的不合格处置方式: " + disposition);
        int updated = jdbc.update("update qm_nonconformance set disposition=?, containment_action=?, owner_code=?, due_date=?, status='CONTAINED', updated_at=current_timestamp where tenant_id=? and id=? and status<>'CLOSED'", disposition, string(payload.get("containmentAction")), string(payload.get("ownerCode")), date(payload.get("dueDate")), tenantId(), id);
        if (updated == 0) throw new IllegalArgumentException("不合格单不存在或已关闭");
        return one("select id, nc_no, status, disposition, containment_action, owner_code, due_date, updated_at from qm_nonconformance where tenant_id=? and id=?", tenantId(), id);
    }

    @Transactional
    public Map<String, Object> closeNonconformance(long id, Map<String, Object> payload, String actor) {
        String rootCause = required(payload, "rootCause", "根因分析");
        String corrective = required(payload, "correctiveAction", "纠正预防措施");
        int updated = jdbc.update("update qm_nonconformance set root_cause=?, corrective_action=?, status='CLOSED', closed_by=?, closed_at=current_timestamp, updated_at=current_timestamp where tenant_id=? and id=? and status<>'CLOSED'", rootCause, corrective, actor, tenantId(), id);
        if (updated == 0) throw new IllegalArgumentException("不合格单不存在或已关闭");
        return one("select id, nc_no, status, disposition, root_cause, corrective_action, closed_by, closed_at from qm_nonconformance where tenant_id=? and id=?", tenantId(), id);
    }

    public List<Map<String, Object>> listActions(long ncId) {
        return jdbc.queryForList("select id, nc_id, action_type, action_description, owner_code, due_date, status, completed_at, completed_by, created_at from qm_corrective_action where tenant_id=? and nc_id=? order by id", tenantId(), ncId);
    }

    @Transactional
    public Map<String, Object> createAction(long ncId, Map<String, Object> payload) {
        if (one("select id from qm_nonconformance where tenant_id=? and id=?", tenantId(), ncId) == null) throw new IllegalArgumentException("不合格单不存在");
        String description = required(payload, "actionDescription", "措施描述");
        jdbc.update("insert into qm_corrective_action(tenant_id, nc_id, action_type, action_description, owner_code, due_date, status) values(?,?,?,?,?,?,?)", tenantId(), ncId, stringOr(payload.get("actionType"), "CORRECTIVE"), description, string(payload.get("ownerCode")), date(payload.get("dueDate")), "OPEN");
        List<Map<String, Object>> actions = listActions(ncId);
        return actions.get(actions.size() - 1);
    }

    @Transactional
    public Map<String, Object> completeAction(long actionId, String actor) {
        int updated = jdbc.update("update qm_corrective_action set status='CLOSED', completed_by=?, completed_at=current_timestamp where tenant_id=? and id=? and status<>'CLOSED'", actor, tenantId(), actionId);
        if (updated == 0) throw new IllegalArgumentException("整改措施不存在或已完成");
        return one("select id, nc_id, action_type, action_description, owner_code, due_date, status, completed_at, completed_by from qm_corrective_action where tenant_id=? and id=?", tenantId(), actionId);
    }

    public List<Map<String, Object>> listSupplierEvaluations(String status, String keyword) {
        StringBuilder sql = new StringBuilder("select id, evaluation_no, supplier_code, supplier_name, evaluation_period, delivery_score, quality_score, service_score, price_score, total_score, grade, status, owner_code, evaluated_at, remark, created_by, created_at, updated_at from qm_supplier_evaluation where tenant_id=?");
        List<Object> args = new ArrayList<>(); args.add(tenantId());
        if (!blank(status)) { sql.append(" and status=?"); args.add(status); }
        if (!blank(keyword)) { String like = "%" + keyword.trim() + "%"; sql.append(" and (evaluation_no like ? or supplier_code like ? or supplier_name like ? or evaluation_period like ?)"); for (int i = 0; i < 4; i++) args.add(like); }
        sql.append(" order by evaluated_at desc, id desc");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> saveSupplierEvaluation(Map<String, Object> payload, long id, String actor) {
        String supplierCode = required(payload, "supplierCode", "供应商编码");
        String supplierName = required(payload, "supplierName", "供应商名称");
        String period = required(payload, "evaluationPeriod", "考评周期");
        BigDecimal delivery = score(payload.get("deliveryScore"));
        BigDecimal quality = score(payload.get("qualityScore"));
        BigDecimal service = score(payload.get("serviceScore"));
        BigDecimal price = score(payload.get("priceScore"));
        BigDecimal total = delivery.multiply(new BigDecimal("0.25"))
                .add(quality.multiply(new BigDecimal("0.40")))
                .add(service.multiply(new BigDecimal("0.15")))
                .add(price.multiply(new BigDecimal("0.20")))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        String grade = grade(total);
        String status = stringOr(payload.get("status"), "DRAFT").toUpperCase();
        if (!List.of("DRAFT", "SUBMITTED").contains(status)) throw new IllegalArgumentException("供应商考评只能保存为草稿或已提交");
        long evaluationId = id;
        if (evaluationId > 0) {
            int updated = jdbc.update("update qm_supplier_evaluation set supplier_code=?, supplier_name=?, evaluation_period=?, delivery_score=?, quality_score=?, service_score=?, price_score=?, total_score=?, grade=?, status=?, owner_code=?, evaluated_at=?, remark=?, updated_at=current_timestamp where tenant_id=? and id=?", supplierCode, supplierName, period, delivery, quality, service, price, total, grade, status, string(payload.get("ownerCode")), date(payload.get("evaluatedAt")), string(payload.get("remark")), tenantId(), evaluationId);
            if (updated == 0) throw new IllegalArgumentException("供应商考评不存在");
        } else {
            String evaluationNo = stringOr(payload.get("evaluationNo"), "SUP-EVAL-" + TenantContext.require().tenantCode() + "-" + UUID.randomUUID());
            jdbc.update("insert into qm_supplier_evaluation(tenant_id, evaluation_no, supplier_code, supplier_name, evaluation_period, delivery_score, quality_score, service_score, price_score, total_score, grade, status, owner_code, evaluated_at, remark, created_by) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", tenantId(), evaluationNo, supplierCode, supplierName, period, delivery, quality, service, price, total, grade, status, string(payload.get("ownerCode")), date(payload.get("evaluatedAt")), string(payload.get("remark")), actor);
            evaluationId = number(scalar("select id from qm_supplier_evaluation where tenant_id=? and evaluation_no=?", tenantId(), evaluationNo));
        }
        return one("select id, evaluation_no, supplier_code, supplier_name, evaluation_period, delivery_score, quality_score, service_score, price_score, total_score, grade, status, owner_code, evaluated_at, remark, created_by, created_at, updated_at from qm_supplier_evaluation where tenant_id=? and id=?", tenantId(), evaluationId);
    }

    @Transactional
    public Map<String, Object> submitSupplierEvaluation(long id, String actor) {
        int updated = jdbc.update("update qm_supplier_evaluation set status='SUBMITTED', updated_at=current_timestamp where tenant_id=? and id=? and status='DRAFT'", tenantId(), id);
        if (updated == 0) throw new IllegalArgumentException("供应商考评不存在或已提交");
        return one("select id, evaluation_no, supplier_code, supplier_name, total_score, grade, status, evaluated_at from qm_supplier_evaluation where tenant_id=? and id=?", tenantId(), id);
    }

    public List<Map<String, Object>> listAvl(String status, String keyword) {
        StringBuilder sql = new StringBuilder("select id, material_code, material_name, supplier_code, supplier_name, supplier_part_no, approval_status, valid_from, valid_to, last_evaluation_score, approved_by, approved_at, remark, created_by, created_at, updated_at from qm_avl_entry where tenant_id=?");
        List<Object> args = new ArrayList<>(); args.add(tenantId());
        if (!blank(status)) { sql.append(" and approval_status=?"); args.add(status); }
        if (!blank(keyword)) { String like = "%" + keyword.trim() + "%"; sql.append(" and (material_code like ? or coalesce(material_name,'') like ? or supplier_code like ? or supplier_name like ? or coalesce(supplier_part_no,'') like ?)"); for (int i = 0; i < 5; i++) args.add(like); }
        sql.append(" order by updated_at desc, id desc");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> saveAvl(Map<String, Object> payload, long id, String actor) {
        String materialCode = required(payload, "materialCode", "物料编码");
        String supplierCode = required(payload, "supplierCode", "供应商编码");
        String supplierName = required(payload, "supplierName", "供应商名称");
        String status = stringOr(payload.get("approvalStatus"), "PENDING").toUpperCase();
        if (!List.of("PENDING", "APPROVED", "SUSPENDED", "EXPIRED").contains(status)) throw new IllegalArgumentException("不支持的 AVL 状态");
        long avlId = id;
        if (avlId > 0) {
            int updated = jdbc.update("update qm_avl_entry set material_code=?, material_name=?, supplier_code=?, supplier_name=?, supplier_part_no=?, approval_status=?, valid_from=?, valid_to=?, last_evaluation_score=?, remark=?, updated_at=current_timestamp where tenant_id=? and id=?", materialCode, string(payload.get("materialName")), supplierCode, supplierName, string(payload.get("supplierPartNo")), status, date(payload.get("validFrom")), date(payload.get("validTo")), decimal(payload.get("lastEvaluationScore")), string(payload.get("remark")), tenantId(), avlId);
            if (updated == 0) throw new IllegalArgumentException("AVL 记录不存在");
        } else {
            jdbc.update("insert into qm_avl_entry(tenant_id, material_code, material_name, supplier_code, supplier_name, supplier_part_no, approval_status, valid_from, valid_to, last_evaluation_score, remark, created_by) values(?,?,?,?,?,?,?,?,?,?,?,?)", tenantId(), materialCode, string(payload.get("materialName")), supplierCode, supplierName, string(payload.get("supplierPartNo")), status, date(payload.get("validFrom")), date(payload.get("validTo")), decimal(payload.get("lastEvaluationScore")), string(payload.get("remark")), actor);
            avlId = number(scalar("select id from qm_avl_entry where tenant_id=? and material_code=? and supplier_code=? and (supplier_part_no=? or (supplier_part_no is null and ? is null))", tenantId(), materialCode, supplierCode, string(payload.get("supplierPartNo")), string(payload.get("supplierPartNo"))));
        }
        return one("select id, material_code, material_name, supplier_code, supplier_name, supplier_part_no, approval_status, valid_from, valid_to, last_evaluation_score, approved_by, approved_at, remark, created_by, created_at, updated_at from qm_avl_entry where tenant_id=? and id=?", tenantId(), avlId);
    }

    @Transactional
    public Map<String, Object> updateAvlStatus(long id, Map<String, Object> payload, String actor) {
        String status = stringOr(payload.get("approvalStatus"), "PENDING").toUpperCase();
        if (!List.of("PENDING", "APPROVED", "SUSPENDED", "EXPIRED").contains(status)) throw new IllegalArgumentException("不支持的 AVL 状态");
        int updated = jdbc.update("update qm_avl_entry set approval_status=?, approved_by=?, approved_at=?, remark=coalesce(?, remark), updated_at=current_timestamp where tenant_id=? and id=?", status, "APPROVED".equals(status) ? actor : null, "APPROVED".equals(status) ? new java.sql.Timestamp(System.currentTimeMillis()) : null, string(payload.get("remark")), tenantId(), id);
        if (updated == 0) throw new IllegalArgumentException("AVL 记录不存在");
        return one("select id, material_code, material_name, supplier_code, supplier_name, supplier_part_no, approval_status, valid_from, valid_to, last_evaluation_score, approved_by, approved_at, remark from qm_avl_entry where tenant_id=? and id=?", tenantId(), id);
    }

    public List<Map<String, Object>> listIpqc(String status, String keyword, String lineCode) {
        StringBuilder sql = new StringBuilder("select id, ipqc_no, line_code, work_order_no, process_code, process_name, product_code, product_name, batch_no, sample_qty, inspected_qty, defect_qty, first_piece_status, status, inspector, started_at, completed_at, remark, created_by, created_at, updated_at from qm_ipqc_record where tenant_id=?");
        List<Object> args = new ArrayList<>(); args.add(tenantId());
        if (!blank(status)) { sql.append(" and status=?"); args.add(status); }
        if (!blank(lineCode)) { sql.append(" and line_code=?"); args.add(lineCode); }
        if (!blank(keyword)) { String like = "%" + keyword.trim() + "%"; sql.append(" and (ipqc_no like ? or line_code like ? or coalesce(work_order_no,'') like ? or process_name like ? or coalesce(product_code,'') like ? or coalesce(batch_no,'') like ?)"); for (int i = 0; i < 6; i++) args.add(like); }
        sql.append(" order by id desc");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> getIpqc(long id) {
        Map<String, Object> result = one("select id, ipqc_no, line_code, work_order_no, process_code, process_name, product_code, product_name, batch_no, sample_qty, inspected_qty, defect_qty, first_piece_status, status, inspector, started_at, completed_at, remark, created_by, created_at, updated_at from qm_ipqc_record where tenant_id=? and id=?", tenantId(), id);
        if (result == null) throw new IllegalArgumentException("IPQC 记录不存在");
        return result;
    }

    @Transactional
    public Map<String, Object> createIpqc(Map<String, Object> payload, String actor) {
        String lineCode = required(payload, "lineCode", "产线编码");
        String processName = required(payload, "processName", "工序名称");
        String ipqcNo = stringOr(payload.get("ipqcNo"), "IPQC-" + TenantContext.require().tenantCode() + "-" + UUID.randomUUID());
        jdbc.update("insert into qm_ipqc_record(tenant_id, ipqc_no, line_code, work_order_no, process_code, process_name, product_code, product_name, batch_no, sample_qty, inspected_qty, defect_qty, first_piece_status, status, inspector, started_at, remark, created_by) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", tenantId(), ipqcNo, lineCode, string(payload.get("workOrderNo")), string(payload.get("processCode")), processName, string(payload.get("productCode")), string(payload.get("productName")), string(payload.get("batchNo")), number(payload.get("sampleQty"), 0), 0, 0, "PENDING", "PENDING", actor, new java.sql.Timestamp(System.currentTimeMillis()), string(payload.get("remark")), actor);
        long id = number(scalar("select id from qm_ipqc_record where tenant_id=? and ipqc_no=?", tenantId(), ipqcNo));
        return getIpqc(id);
    }

    @Transactional
    public Map<String, Object> saveIpqcResult(long id, Map<String, Object> payload, String actor) {
        Map<String, Object> record = one("select id, sample_qty, status from qm_ipqc_record where tenant_id=? and id=?", tenantId(), id);
        if (record == null) throw new IllegalArgumentException("IPQC 记录不存在");
        if (List.of("CLOSED").contains(String.valueOf(record.get("status")))) throw new IllegalArgumentException("IPQC 已关闭，不能修改");
        int inspectedQty = number(payload.get("inspectedQty"), 0);
        int defectQty = number(payload.get("defectQty"), 0);
        String firstPieceStatus = stringOr(payload.get("firstPieceStatus"), "PENDING").toUpperCase();
        if (!List.of("PENDING", "PASS", "FAIL").contains(firstPieceStatus)) throw new IllegalArgumentException("首件状态不正确");
        String status = string(payload.get("status"));
        if (blank(status)) status = defectQty > 0 || "FAIL".equals(firstPieceStatus) ? "FAILED" : inspectedQty >= number(record.get("sample_qty"), 0) ? "PASSED" : "IN_PROGRESS";
        status = status.toUpperCase();
        if (!List.of("PENDING", "IN_PROGRESS", "PASSED", "FAILED", "CLOSED").contains(status)) throw new IllegalArgumentException("IPQC 状态不正确");
        boolean completed = List.of("PASSED", "FAILED", "CLOSED").contains(status);
        int updated = jdbc.update("update qm_ipqc_record set inspected_qty=?, defect_qty=?, first_piece_status=?, status=?, inspector=?, started_at=coalesce(started_at,current_timestamp), completed_at=?, remark=?, updated_at=current_timestamp where tenant_id=? and id=?", inspectedQty, defectQty, firstPieceStatus, status, actor, completed ? new java.sql.Timestamp(System.currentTimeMillis()) : null, string(payload.get("remark")), tenantId(), id);
        if (updated == 0) throw new IllegalArgumentException("IPQC 记录不存在");
        return getIpqc(id);
    }

    private static BigDecimal score(Object value) {
        BigDecimal score = value == null || String.valueOf(value).isBlank() ? BigDecimal.ZERO : decimal(value);
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("考评分数应在 0-100 之间");
        return score;
    }

    private static String grade(BigDecimal total) {
        if (total.compareTo(new BigDecimal("90")) >= 0) return "A";
        if (total.compareTo(new BigDecimal("80")) >= 0) return "B";
        if (total.compareTo(new BigDecimal("70")) >= 0) return "C";
        return "D";
    }

    private String resultStatus(Map<String, Object> definition, BigDecimal value, String text, String requested) {
        if (!blank(requested) && List.of("PASS", "FAIL").contains(requested.toUpperCase())) return requested.toUpperCase();
        if ("QUANTITATIVE".equalsIgnoreCase(String.valueOf(definition.get("result_type")))) {
            if (value == null) throw new IllegalArgumentException("定量检验必须填写数值");
            BigDecimal lower = decimal(definition.get("lower_limit")); BigDecimal upper = decimal(definition.get("upper_limit"));
            if (lower != null && value.compareTo(lower) < 0 || upper != null && value.compareTo(upper) > 0) return "FAIL";
            return "PASS";
        }
        if (blank(text)) throw new IllegalArgumentException("定性检验必须填写结果");
        return "PASS".equalsIgnoreCase(text) || "合格".equals(text) ? "PASS" : "FAIL";
    }

    private long findApplicablePlan(String type, String materialCode, String productCode) {
        Map<String, Object> plan = one("select id from qm_inspection_plan where tenant_id=? and inspection_type=? and status='RELEASED' and (effective_from is null or effective_from<=current_date) and (effective_to is null or effective_to>=current_date) and (material_code=? or material_code is null) and (product_code=? or product_code is null) order by material_code is null, product_code is null, updated_at desc limit 1", tenantId(), type, materialCode, productCode);
        return plan == null ? 0 : number(plan.get("id"), 0);
    }

    private Map<String, Object> one(String sql, Object... args) { List<Map<String, Object>> rows = jdbc.queryForList(sql, args); return rows.isEmpty() ? null : rows.get(0); }
    private Object scalar(String sql, Object... args) { return jdbc.queryForObject(sql, Object.class, args); }
    private long tenantId() { return TenantContext.require().tenantId(); }
    private static Map<String, Object> map(Object raw) { Map<String, Object> result = new LinkedHashMap<>(); ((Map<?, ?>) raw).forEach((key, value) -> result.put(String.valueOf(key), value)); return result; }
    private static String required(Map<String, Object> payload, String key, String label) { String value = string(payload.get(key)); if (blank(value)) throw new IllegalArgumentException(label + "不能为空"); return value; }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static String stringOr(Object value, String fallback) { String result = string(value); return blank(result) ? fallback : result; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean flag(Object value, boolean fallback) { return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)) || "1".equals(String.valueOf(value)); }
    private static int number(Object value, int fallback) { if (value == null) return fallback; try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }
    private static long number(Object value) { if (value == null) return 0; try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return 0; } }
    private static BigDecimal decimal(Object value) { return decimal(value, null); }
    private static BigDecimal decimal(Object first, Object second) { Object value = first == null ? second : first; if (value == null || String.valueOf(value).isBlank()) return null; try { return new BigDecimal(String.valueOf(value)); } catch (NumberFormatException ex) { throw new IllegalArgumentException("检验数值格式不正确"); } }
    private static Date date(Object value) { if (value == null || String.valueOf(value).isBlank()) return null; try { return Date.valueOf(LocalDate.parse(String.valueOf(value))); } catch (RuntimeException ex) { throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd"); } }
}
