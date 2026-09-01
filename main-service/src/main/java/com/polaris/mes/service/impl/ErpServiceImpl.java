package com.polaris.mes.service.impl;

import com.polaris.mes.annotation.AuditOperation;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.ErpService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
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

@Service
@Transactional
public class ErpServiceImpl implements ErpService {
    private static final Set<String> DOMAINS = Set.of("SALES", "PROCUREMENT", "FINANCE", "MASTER");
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.##");
    private final JdbcTemplate jdbc;

    public ErpServiceImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("salesAmount", scalar("select coalesce(sum(amount_value),0) from erp_business_record where tenant_id=? and domain='SALES'", tenantId()));
        result.put("salesOrders", scalar("select count(*) from erp_business_record where tenant_id=? and domain='SALES'", tenantId()));
        result.put("pendingSales", scalar("select count(*) from erp_business_record where tenant_id=? and domain='SALES' and status='REVIEW'", tenantId()));
        result.put("procurementAmount", scalar("select coalesce(sum(amount_value),0) from erp_business_record where tenant_id=? and domain='PROCUREMENT'", tenantId()));
        result.put("pendingProcurement", scalar("select count(*) from erp_business_record where tenant_id=? and domain='PROCUREMENT' and status in ('REVIEW','ORDERED','IN_TRANSIT')", tenantId()));
        result.put("receivableAmount", scalar("select coalesce(sum(amount_value),0) from erp_business_record where tenant_id=? and domain='FINANCE' and record_type='RECEIVABLE' and status<>'PAID'", tenantId()));
        result.put("payableAmount", scalar("select coalesce(sum(amount_value),0) from erp_business_record where tenant_id=? and domain='FINANCE' and record_type='PAYABLE' and status<>'PAID'", tenantId()));
        result.put("financePending", scalar("select count(*) from erp_business_record where tenant_id=? and domain='FINANCE' and status<>'PAID'", tenantId()));
        result.put("masterRecords", scalar("select count(*) from erp_business_record where tenant_id=? and domain='MASTER'", tenantId()));
        result.put("inProgressOrders", safeScalar("select count(*) from work_order where tenant_id=? and deleted=0 and status='IN_PROGRESS'", 0, tenantId()));
        result.put("plannedOrders", safeScalar("select count(*) from work_order where tenant_id=? and deleted=0 and status in ('PLANNED','RELEASED')", 0, tenantId()));
        result.put("recentRecords", recentRecords());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRecords(String domain, String recordType, String keyword, String status) {
        return listRecords(domain, recordType, keyword, status, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRecords(String domain, String recordType, String keyword, String status, String scope, String actor) {
        String normalizedDomain = domain(domain);
        StringBuilder sql = new StringBuilder("select id, domain, record_type, record_no, record_name, partner_name, org_code, department_code, requester_code, currency_code, amount_value, tax_amount, amount_label, business_date, delivery_date, payment_terms, source_type, source_doc_no, owner_code, status, remark, created_by, created_at, updated_at from erp_business_record where tenant_id=? and domain=?");
        List<Object> args = new ArrayList<>(List.of(tenantId(), normalizedDomain));
        if (!isTenantAdmin()) {
            sql.append(" and (created_by=? or owner_code=? or requester_code=?)");
            args.add(TenantContext.require().username()); args.add(TenantContext.require().username()); args.add(TenantContext.require().username());
        }
        if (!blank(recordType)) { sql.append(" and record_type=?"); args.add(recordType.trim().toUpperCase(Locale.ROOT)); }
        if (!blank(status)) { sql.append(" and status=?"); args.add(status.trim().toUpperCase(Locale.ROOT)); }
        if ("DRAFTS".equalsIgnoreCase(scope)) {
            String currentActor = blank(actor) ? TenantContext.require().username() : actor;
            sql.append(" and status='DRAFT' and created_by=?");
            args.add(currentActor);
        }
        if (!blank(keyword)) {
            sql.append(" and (record_no like ? or record_name like ? or partner_name like ? or owner_code like ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value); args.add(value); args.add(value); args.add(value);
        }
        sql.append(" order by business_date desc, id desc");
        return jdbc.queryForList(sql.toString(), args.toArray()).stream().map(row -> normalize(row, normalizedDomain)).toList();
    }

    @Override
    public Map<String, Object> saveDraft(String domain, Map<String, Object> payload) {
        if (!"PROCUREMENT".equals(domain(domain))) throw new IllegalArgumentException("只有采购申请支持保存草稿");
        Map<String, Object> draft = new LinkedHashMap<>();
        if (payload != null) draft.putAll(payload);
        draft.put("status", "DRAFT");
        return createRecord("procurement", draft);
    }

    @Override
    @AuditOperation(action = "ERP_RECORD_CREATE", resource = "ERP_BUSINESS_RECORD")
    public Map<String, Object> createRecord(String domain, Map<String, Object> payload) {
        String normalizedDomain = domain(domain);
        requireWriteRole(normalizedDomain);
        String recordType = required(payload, "type", "业务类型").toUpperCase(Locale.ROOT);
        String recordName = required(payload, "name", "业务内容");
        String partnerName = stringOr(payload.get("partner"), "待补充");
        String recordNo = stringOr(payload.get("no"), generatedNo(normalizedDomain, recordType));
        String ownerCode = stringOr(payload.get("owner"), TenantContext.require().username());
        String status = stringOr(payload.get("status"), defaultStatus(normalizedDomain));
        if (!allowedStatus(normalizedDomain, status)) throw new IllegalArgumentException("业务状态不受支持：" + status);
        List<Map<String, Object>> lines = normalizeLines(payload.get("lines"), recordName, payload.get("amount"), normalizedDomain, recordType);
        if ("PROCUREMENT".equals(normalizedDomain) && "REQUISITIONS".equals(recordType) && lines.isEmpty()) {
            throw new IllegalArgumentException("采购申请至少需要一条行信息");
        }
        BigDecimal amountValue = null;
        String amountLabel = null;
        if ("MASTER".equals(normalizedDomain)) amountLabel = string(payload.get("amount"));
        else amountValue = lines.stream().map(line -> decimal(line.get("amountValue"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!"MASTER".equals(normalizedDomain) && amountValue.compareTo(BigDecimal.ZERO) == 0) amountValue = decimal(payload.get("amount"));
        BigDecimal taxAmount = lines.stream().map(line -> decimal(line.get("taxAmount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        try {
            jdbc.update("insert into erp_business_record(tenant_id,domain,record_type,record_no,record_name,partner_name,org_code,department_code,requester_code,currency_code,amount_value,tax_amount,amount_label,business_date,delivery_date,payment_terms,source_type,source_doc_no,owner_code,status,remark,created_by) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    tenantId(), normalizedDomain, recordType, recordNo, recordName, partnerName,
                    string(payload.get("orgCode")), string(payload.get("departmentCode")), string(payload.get("requesterCode")), stringOr(payload.get("currency"), "CNY"),
                    amountValue, taxAmount, amountLabel, date(payload.get("businessDate"), LocalDate.now()), date(payload.get("deliveryDate"), null),
                    string(payload.get("paymentTerms")), string(payload.get("sourceType")), string(payload.get("sourceDocNo")), ownerCode, status,
                    string(payload.get("remark")), TenantContext.require().username());
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new IllegalArgumentException("单据编码已存在：" + recordNo);
        }
        Map<String, Object> saved = record(recordNo, normalizedDomain);
        long recordId = ((Number) saved.get("id")).longValue();
        for (Map<String, Object> line : lines) insertLine(recordId, line);
        return detailRecord(normalizedDomain, recordId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> detailRecord(String domain, long id) {
        Map<String, Object> row = rawRecord(id, domain(domain));
        return detail(row);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> detailRecord(long id) {
        Map<String, Object> row = one("select id, domain, record_type, record_no, record_name, partner_name, org_code, department_code, requester_code, currency_code, amount_value, tax_amount, amount_label, business_date, delivery_date, payment_terms, source_type, source_doc_no, owner_code, status, remark, created_by, created_at, updated_at from erp_business_record where tenant_id=? and id=?", tenantId(), id);
        if (row == null) throw new IllegalArgumentException("ERP 业务单据不存在");
        ensureRecordAccess(row);
        return detail(row);
    }

    @Override
    @AuditOperation(action = "ERP_RECORD_TRANSITION", resource = "ERP_BUSINESS_RECORD")
    public Map<String, Object> transition(String domain, long id, Map<String, Object> payload) {
        String normalizedDomain = domain(domain);
        requireWriteRole(normalizedDomain);
        Map<String, Object> current = rawRecord(id, normalizedDomain);
        String from = String.valueOf(current.get("status"));
        String to = required(payload, "status", "目标状态").toUpperCase(Locale.ROOT);
        if (!canTransition(normalizedDomain, from, to)) throw new IllegalArgumentException("业务状态不能从 " + from + " 流转到 " + to);
        int updated = jdbc.update("update erp_business_record set status=?,updated_at=current_timestamp where tenant_id=? and domain=? and id=? and status=?",
                to, tenantId(), normalizedDomain, id, from);
        if (updated == 0) throw new IllegalArgumentException("业务单据已被其他人更新，请刷新后重试");
        return record(id, normalizedDomain);
    }

    private Map<String, Object> record(String recordNo, String domain) {
        Map<String, Object> row = one("select id, domain, record_type, record_no, record_name, partner_name, org_code, department_code, requester_code, currency_code, amount_value, tax_amount, amount_label, business_date, delivery_date, payment_terms, source_type, source_doc_no, owner_code, status, remark, created_by, created_at, updated_at from erp_business_record where tenant_id=? and domain=? and record_no=?", tenantId(), domain, recordNo);
        if (row == null) throw new IllegalArgumentException("ERP 业务单据不存在");
        return normalize(row, domain);
    }

    private Map<String, Object> record(long id, String domain) {
        Map<String, Object> row = rawRecord(id, domain);
        return normalize(row, domain);
    }

    private Map<String, Object> rawRecord(long id, String domain) {
        Map<String, Object> row = one("select id, domain, record_type, record_no, record_name, partner_name, org_code, department_code, requester_code, currency_code, amount_value, tax_amount, amount_label, business_date, delivery_date, payment_terms, source_type, source_doc_no, owner_code, status, remark, created_by, created_at, updated_at from erp_business_record where tenant_id=? and domain=? and id=?", tenantId(), domain, id);
        if (row == null) throw new IllegalArgumentException("ERP 业务单据不存在");
        ensureRecordAccess(row);
        return row;
    }

    private List<Map<String, Object>> recentRecords() {
        String sql = "select id, domain, record_type, record_no, record_name, partner_name, org_code, department_code, requester_code, currency_code, amount_value, tax_amount, amount_label, business_date, delivery_date, payment_terms, source_type, source_doc_no, owner_code, status, remark, created_by, created_at, updated_at from erp_business_record where tenant_id=?";
        List<Object> args = new ArrayList<>(List.of(tenantId()));
        if (!isTenantAdmin()) {
            sql += " and (created_by=? or owner_code=? or requester_code=?)";
            args.add(TenantContext.require().username()); args.add(TenantContext.require().username()); args.add(TenantContext.require().username());
        }
        sql += " order by id desc limit 8";
        return jdbc.queryForList(sql, args.toArray())
                .stream().map(row -> normalize(row, String.valueOf(row.get("domain")))).toList();
    }

    private Map<String, Object> detail(Map<String, Object> row) {
        ensureRecordAccess(row);
        String normalizedDomain = String.valueOf(row.get("domain"));
        Map<String, Object> result = normalize(row, normalizedDomain);
        result.put("lines", jdbc.queryForList("select id, line_no, material_code, material_name, specification, unit, requested_qty, delivered_qty, unit_price, tax_rate, amount_value, required_date, warehouse_code, cost_center, project_code, source_ref, remark from erp_business_record_line where tenant_id=? and record_id=? order by line_no", tenantId(), row.get("id")).stream().map(this::normalizeLine).toList());
        return result;
    }

    private void insertLine(long recordId, Map<String, Object> line) {
        jdbc.update("insert into erp_business_record_line(tenant_id,record_id,line_no,material_code,material_name,specification,unit,requested_qty,delivered_qty,unit_price,tax_rate,amount_value,required_date,warehouse_code,cost_center,project_code,source_ref,remark) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                tenantId(), recordId, line.get("lineNo"), line.get("materialCode"), line.get("materialName"), line.get("specification"), line.get("unit"),
                line.get("requestedQty"), line.get("deliveredQty"), line.get("unitPrice"), line.get("taxRate"), line.get("amountValue"), line.get("requiredDate"),
                line.get("warehouseCode"), line.get("costCenter"), line.get("projectCode"), line.get("sourceRef"), line.get("remark"));
    }

    private List<Map<String, Object>> normalizeLines(Object raw, String fallbackName, Object fallbackAmount, String domain, String type) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            int lineNo = 1;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> source)) continue;
                String materialName = stringOr(first(source, "materialName", "name", "productName"), "");
                if (materialName.isBlank()) throw new IllegalArgumentException("第 " + lineNo + " 行物料名称不能为空");
                BigDecimal quantity = decimal(first(source, "requestedQty", "quantity", "qty"));
                if (quantity.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("第 " + lineNo + " 行数量必须大于 0");
                BigDecimal unitPrice = decimal(first(source, "unitPrice", "price"));
                BigDecimal taxRate = decimal(first(source, "taxRate", "tax"));
                BigDecimal amount = decimal(first(source, "amountValue", "amount"));
                if (amount.compareTo(BigDecimal.ZERO) == 0 && unitPrice.compareTo(BigDecimal.ZERO) > 0) amount = quantity.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal taxAmount = amount.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                result.add(line(lineNo++, source, materialName, quantity, unitPrice, taxRate, amount, taxAmount));
            }
        }
        if (result.isEmpty() && !fallbackName.isBlank() && !("PROCUREMENT".equals(domain) && "REQUISITIONS".equals(type))) {
            BigDecimal amount = decimal(fallbackAmount);
            result.add(line(1, Map.of(), fallbackName, BigDecimal.ONE, amount, BigDecimal.ZERO, amount, BigDecimal.ZERO));
        }
        return result;
    }

    private Map<String, Object> line(int lineNo, Map<?, ?> source, String name, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate, BigDecimal amount, BigDecimal taxAmount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lineNo", lineNo); result.put("materialCode", string(first(source, "materialCode", "code"))); result.put("materialName", name);
        result.put("specification", string(first(source, "specification", "spec"))); result.put("unit", stringOr(first(source, "unit"), "件"));
        result.put("requestedQty", quantity); result.put("deliveredQty", decimal(first(source, "deliveredQty", "deliveredQuantity")));
        result.put("unitPrice", unitPrice); result.put("taxRate", taxRate); result.put("amountValue", amount); result.put("taxAmount", taxAmount);
        result.put("requiredDate", date(first(source, "requiredDate", "deliveryDate"), null)); result.put("warehouseCode", string(first(source, "warehouseCode", "warehouse")));
        result.put("costCenter", string(first(source, "costCenter"))); result.put("projectCode", string(first(source, "projectCode")));
        result.put("sourceRef", string(first(source, "sourceRef", "sourceDocNo"))); result.put("remark", string(first(source, "remark")));
        return result;
    }

    private Map<String, Object> normalizeLine(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id")); result.put("lineNo", row.get("line_no")); result.put("materialCode", row.get("material_code"));
        result.put("materialName", row.get("material_name")); result.put("specification", row.get("specification")); result.put("unit", row.get("unit"));
        result.put("requestedQty", row.get("requested_qty")); result.put("deliveredQty", row.get("delivered_qty")); result.put("unitPrice", row.get("unit_price"));
        result.put("taxRate", row.get("tax_rate")); result.put("amountValue", row.get("amount_value")); result.put("requiredDate", row.get("required_date"));
        result.put("warehouseCode", row.get("warehouse_code")); result.put("costCenter", row.get("cost_center")); result.put("projectCode", row.get("project_code"));
        result.put("sourceRef", row.get("source_ref")); result.put("remark", row.get("remark"));
        return result;
    }

    private Map<String, Object> normalize(Map<String, Object> row, String domain) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("domain", row.get("domain"));
        result.put("type", row.get("record_type"));
        result.put("no", row.get("record_no"));
        result.put("name", row.get("record_name"));
        result.put("partner", row.get("partner_name"));
        result.put("orgCode", row.get("org_code"));
        result.put("departmentCode", row.get("department_code"));
        result.put("requesterCode", row.get("requester_code"));
        result.put("currency", stringOr(row.get("currency_code"), "CNY"));
        result.put("taxAmount", row.get("tax_amount"));
        Object amountValue = row.get("amount_value");
        Object amountLabel = row.get("amount_label");
        result.put("amount", "MASTER".equals(domain) ? stringOr(amountLabel, "-") : amountValue == null ? "-" : "¥ " + AMOUNT_FORMAT.format(new BigDecimal(String.valueOf(amountValue))));
        result.put("amountValue", amountValue);
        result.put("date", row.get("business_date"));
        result.put("deliveryDate", row.get("delivery_date"));
        result.put("paymentTerms", row.get("payment_terms"));
        result.put("sourceType", row.get("source_type"));
        result.put("sourceDocNo", row.get("source_doc_no"));
        result.put("owner", row.get("owner_code"));
        result.put("status", row.get("status"));
        result.put("lineCount", safeScalar("select count(*) from erp_business_record_line where tenant_id=? and record_id=?", 0, tenantId(), row.get("id")));
        result.put("remark", row.get("remark"));
        result.put("createdBy", row.get("created_by"));
        result.put("createdAt", row.get("created_at"));
        result.put("updatedAt", row.get("updated_at"));
        return result;
    }

    private void requireWriteRole(String domain) {
        switch (domain) {
            case "SALES", "PROCUREMENT" -> RequestContext.requireRole("admin", "planner");
            case "FINANCE" -> RequestContext.requireRole("admin");
            case "MASTER" -> RequestContext.requireRole("admin", "planner");
            default -> throw new IllegalArgumentException("ERP 业务域不受支持");
        }
    }

    private boolean isTenantAdmin() {
        return "admin".equals(TenantContext.require().roleCode());
    }

    private void ensureRecordAccess(Map<String, Object> row) {
        if (isTenantAdmin()) return;
        String actor = TenantContext.require().username();
        boolean own = actor.equals(String.valueOf(row.get("created_by")))
                || actor.equals(String.valueOf(row.get("owner_code")))
                || actor.equals(String.valueOf(row.get("requester_code")));
        if (!own) throw new IllegalArgumentException("只能查看自己的业务单据");
    }

    private static boolean allowedStatus(String domain, String status) {
        return switch (domain) {
            case "SALES" -> Set.of("REVIEW", "CONFIRMED", "DELIVERING", "COMPLETED").contains(status);
            case "PROCUREMENT" -> Set.of("DRAFT", "REVIEW", "APPROVED", "ORDERED", "IN_TRANSIT", "RECEIVED").contains(status);
            case "FINANCE" -> Set.of("PENDING", "INVOICED", "PAID").contains(status);
            case "MASTER" -> Set.of("REVIEW", "ACTIVE").contains(status);
            default -> false;
        };
    }

    private static boolean canTransition(String domain, String from, String to) {
        if (!allowedStatus(domain, to)) return false;
        return switch (domain) {
            case "SALES" -> Map.of("REVIEW", "CONFIRMED", "CONFIRMED", "DELIVERING", "DELIVERING", "COMPLETED").getOrDefault(from, "").equals(to);
            case "PROCUREMENT" -> Map.of("REVIEW", "ORDERED", "ORDERED", "IN_TRANSIT", "IN_TRANSIT", "RECEIVED").getOrDefault(from, "").equals(to);
            case "FINANCE" -> Map.of("PENDING", "INVOICED", "INVOICED", "PAID").getOrDefault(from, "").equals(to);
            case "MASTER" -> "REVIEW".equals(from) && "ACTIVE".equals(to);
            default -> false;
        };
    }

    private static String defaultStatus(String domain) { return "FINANCE".equals(domain) ? "PENDING" : "MASTER".equals(domain) ? "REVIEW" : "REVIEW"; }
    private static String domain(String value) { String normalized = String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT); if (!DOMAINS.contains(normalized)) throw new IllegalArgumentException("ERP 业务域不受支持：" + value); return normalized; }
    private long tenantId() { return TenantContext.require().tenantId(); }
    private Map<String, Object> one(String sql, Object... args) { List<Map<String, Object>> rows = jdbc.queryForList(sql, args).stream().map(row -> { Map<String, Object> normalized = new LinkedHashMap<>(); row.forEach((key, value) -> normalized.put(String.valueOf(key).toLowerCase(Locale.ROOT), value)); return normalized; }).toList(); return rows.isEmpty() ? null : rows.get(0); }
    private static String generatedNo(String domain, String type) { String prefix = domain.substring(0, Math.min(3, domain.length())); return prefix + "-" + type + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT); }
    private static String required(Map<String, Object> payload, String key, String label) { String value = string(payload.get(key)); if (blank(value)) throw new IllegalArgumentException(label + "不能为空"); return value.trim(); }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static String stringOr(Object value, String fallback) { String result = string(value); return blank(result) ? fallback : result; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static BigDecimal decimal(Object value) { if (value == null || String.valueOf(value).isBlank()) return BigDecimal.ZERO; try { return new BigDecimal(String.valueOf(value).replaceAll("[^0-9.-]", "")); } catch (NumberFormatException ex) { throw new IllegalArgumentException("金额格式不正确"); } }
    private static LocalDate date(Object value, LocalDate fallback) { if (value == null || String.valueOf(value).isBlank()) return fallback; try { return LocalDate.parse(String.valueOf(value).substring(0, 10)); } catch (RuntimeException ex) { throw new IllegalArgumentException("日期格式不正确：" + value); } }
    private static Object first(Map<?, ?> source, String... keys) { for (String key : keys) if (source.containsKey(key) && source.get(key) != null) return source.get(key); return null; }
    private Object scalar(String sql, Object... args) { return jdbc.queryForObject(sql, Object.class, args); }
    private Object safeScalar(String sql, Object fallback, Object... args) { try { return scalar(sql, args); } catch (RuntimeException ignored) { return fallback; } }
}
