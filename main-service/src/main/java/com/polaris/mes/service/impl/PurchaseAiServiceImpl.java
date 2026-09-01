package com.polaris.mes.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.mes.annotation.AuditOperation;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.BpmService;
import com.polaris.mes.service.ErpService;
import com.polaris.mes.service.PurchaseAiService;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class PurchaseAiServiceImpl implements PurchaseAiService {
    private static final String MODEL = "polaris-purchase-parser-v0.1";
    private static final String PROMPT_VERSION = "FS-PROC-AI-001-v0.1.0";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern QUANTITY_LINE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(个|件|台|套|箱|包|米|kg|KG|公斤)\\s*([^，。,；;。]+)");
    private static final Pattern PRICE = Pattern.compile("(?:预计)?(?:含税)?单价\\s*(?:约|预计)?\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_DATE = Pattern.compile("(20\\d{2})[-年](\\d{1,2})[-月](\\d{1,2})日?");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})月(?:\\s*(\\d{1,2})日?)?");
    private static final Pattern MATERIAL_CODE = Pattern.compile("(?:物料(?:编码|号)|编码)\\s*[:：]?\\s*([A-Za-z0-9][A-Za-z0-9_-]{3,})");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ErpService erp;
    private final BpmService bpm;

    public PurchaseAiServiceImpl(JdbcTemplate jdbc, ObjectMapper objectMapper, ErpService erp, BpmService bpm) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.erp = erp;
        this.bpm = bpm;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> context() {
        TenantContext.Identity identity = TenantContext.require();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantCode", identity.tenantCode());
        result.put("tenantName", identity.tenantName());
        result.put("requesterCode", identity.username());
        result.put("requesterName", identity.username());
        result.put("organization", Map.of("code", identity.tenantCode(), "name", identity.tenantName()));
        result.put("departmentOptions", List.of(
                Map.of("code", "RND", "name", "研发部"),
                Map.of("code", "PURCHASE", "name", "采购部"),
                Map.of("code", "OPS", "name", "生产运营部")));
        result.put("currencyOptions", List.of("CNY", "USD", "EUR"));
        result.put("materialOptions", materialOptions());
        result.put("writeAllowed", true);
        result.put("routeConfigVersion", "PURCHASE-DEFAULT-v0.1");
        return result;
    }

    @Override
    public Map<String, Object> parse(Map<String, Object> payload) {
        String input = string(payload.get("input"));
        if (input == null || input.isBlank()) throw new IllegalArgumentException("请先描述要采购的物料和用途");
        String sessionId = stringOr(payload.get("sessionId"), newSessionId());
        Map<String, Object> draft = parseDraft(input);
        Map<String, Object> validation = validateDraft(draft);
        List<String> missing = new ArrayList<>(stringList(validation.get("missing")));
        List<Map<String, Object>> risks = mapList(validation.get("riskFlags"));
        List<String> needsConfirmation = stringList(draft.get("needsConfirmation"));
        for (String item : needsConfirmation) if (!missing.contains(item)) missing.add(item);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("draftVersion", 1);
        result.put("intent", draft.get("intent"));
        result.put("confidence", draft.get("confidence"));
        Map<String, Object> previewDraft = castMap(validation.get("normalizedDraft"));
        previewDraft.put("confidence", draft.get("confidence"));
        previewDraft.put("needsConfirmation", needsConfirmation);
        result.put("draft", previewDraft);
        result.put("missing", missing);
        result.put("needsConfirmation", needsConfirmation);
        result.put("riskFlags", risks);
        result.put("evidence", draft.get("evidence"));
        result.put("routeExplanation", validation.get("route"));
        result.put("calculation", validation.get("calculation"));
        result.put("errors", validation.get("errors"));
        result.put("warnings", validation.get("warnings"));

        saveSession(sessionId, input, result, "DRAFT");
        return result;
    }

    @Override
    public Map<String, Object> validate(Map<String, Object> payload) {
        Object rawDraft = payload.get("draft");
        if (!(rawDraft instanceof Map<?, ?>)) throw new IllegalArgumentException("采购申请草稿格式不正确");
        return validateDraft(castMap(rawDraft));
    }

    @Override
    @AuditOperation(action = "AI_PURCHASE_CONFIRM", resource = "ERP_BUSINESS_RECORD")
    public Map<String, Object> confirm(Map<String, Object> payload, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("缺少 Idempotency-Key，无法安全提交采购申请");
        }
        if (!Boolean.parseBoolean(String.valueOf(payload.getOrDefault("routeConfirmation", false)))) {
            throw new IllegalArgumentException("请先确认审批链路");
        }
        String sessionId = string(payload.get("sessionId"));
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("AI 会话不存在，请重新生成草稿");
        Map<String, Object> existing = findSubmission(idempotencyKey.trim());
        if (existing != null) return idempotentResult(existing);

        Object rawDraft = payload.get("draft");
        if (!(rawDraft instanceof Map<?, ?>)) throw new IllegalArgumentException("采购申请草稿格式不正确");
        Map<String, Object> validation = validateDraft(castMap(rawDraft));
        List<Map<String, Object>> errors = mapList(validation.get("errors"));
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.valueOf(errors.get(0).get("message")));
        List<String> missing = stringList(validation.get("missing"));
        if (!missing.isEmpty()) throw new IllegalArgumentException("请补齐提交前必填项：" + String.join("、", missing));
        Map<String, Object> normalizedDraft = castMap(validation.get("normalizedDraft"));
        Map<String, Object> calculation = castMap(validation.get("calculation"));
        List<Map<String, Object>> route = mapList(validation.get("route"));

        try {
            jdbc.update("insert into ai_purchase_submission(tenant_id,idempotency_key,session_id,status) values(?,?,?,?)",
                    tenantId(), idempotencyKey.trim(), sessionId, "CREATING");
        } catch (DuplicateKeyException ex) {
            Map<String, Object> concurrent = findSubmission(idempotencyKey.trim());
            if (concurrent != null) return idempotentResult(concurrent);
            throw new IllegalArgumentException("提交正在处理中，请稍后刷新");
        }

        Map<String, Object> requisition = castMap(normalizedDraft.get("requisition"));
        List<Map<String, Object>> lines = mapList(requisition.get("lines"));
        String reason = stringOr(requisition.get("reason"), "采购申请");
        Map<String, Object> erpPayload = new LinkedHashMap<>();
        erpPayload.put("type", "REQUISITIONS");
        erpPayload.put("name", "AI采购申请 · " + reason);
        erpPayload.put("partner", requisition.get("partner"));
        erpPayload.put("orgCode", requisition.get("orgCode"));
        erpPayload.put("departmentCode", requisition.get("departmentCode"));
        erpPayload.put("requesterCode", TenantContext.require().username());
        erpPayload.put("owner", TenantContext.require().username());
        erpPayload.put("currency", requisition.get("currency"));
        erpPayload.put("businessDate", LocalDate.now().format(DATE_FORMAT));
        erpPayload.put("deliveryDate", earliestDate(lines));
        erpPayload.put("sourceType", "AI_ASSISTANT");
        erpPayload.put("sourceDocNo", firstSourceRef(lines));
        erpPayload.put("status", "REVIEW");
        erpPayload.put("remark", buildRemark(sessionId, reason, requisition.get("remark")));
        erpPayload.put("lines", lines);
        Map<String, Object> record = erp.createRecord("PROCUREMENT", erpPayload);

        Map<String, Object> bpmPayload = new LinkedHashMap<>();
        bpmPayload.put("processCode", "purchaseRequisitionApproval");
        bpmPayload.put("businessType", "ERP_RECORD");
        bpmPayload.put("businessId", String.valueOf(record.get("id")));
        bpmPayload.put("title", record.get("no") + " · " + reason + " 审批");
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("domain", "PROCUREMENT");
        variables.put("recordType", "REQUISITIONS");
        variables.put("purchaseAmount", calculation.get("total"));
        variables.put("purchaseHighRisk", isHighRisk(requisition, calculation));
        variables.put("purchaseManagementRisk", decimalOrZero(calculation.get("total")).compareTo(BigDecimal.valueOf(200000)) > 0);
        bpmPayload.put("variables", variables);
        Map<String, Object> process = bpm.startProcess(bpmPayload, TenantContext.require().username());

        jdbc.update("update ai_purchase_submission set record_id=?,record_no=?,bpm_instance_id=?,status='SUBMITTED',updated_at=current_timestamp where tenant_id=? and idempotency_key=?",
                record.get("id"), record.get("no"), process.get("flowable_instance_id"), tenantId(), idempotencyKey.trim());
        updateSession(sessionId, "CONFIRMED", normalizedDraft);
        saveFieldAudit(sessionId, normalizedDraft);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("recordId", record.get("id"));
        result.put("recordNo", record.get("no"));
        result.put("status", "SUBMITTED");
        result.put("record", record);
        result.put("processInstance", process);
        result.put("route", route);
        result.put("idempotent", false);
        return result;
    }

    private Map<String, Object> parseDraft(String input) {
        TenantContext.Identity identity = TenantContext.require();
        Map<String, Object> requisition = new LinkedHashMap<>();
        requisition.put("reason", extractReason(input));
        requisition.put("orgCode", identity.tenantCode());
        requisition.put("departmentCode", extractDepartment(input));
        requisition.put("requesterCode", identity.username());
        requisition.put("currency", extractCurrency(input));
        requisition.put("projectCode", extractAfter(input, "项目号|项目编码"));
        requisition.put("partner", extractAfter(input, "供应商|供货商"));
        requisition.put("remark", "用户原话：" + input);

        BigDecimal unitPrice = extractPrice(input);
        String materialCode = extractMaterialCode(input);
        List<Map<String, Object>> lines = new ArrayList<>();
        Matcher matcher = QUANTITY_LINE.matcher(input);
        int index = 0;
        while (matcher.find()) {
            String rawName = matcher.group(3).trim();
            if (rawName.isBlank()) continue;
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("materialCode", index == 0 ? materialCode : null);
            line.put("materialName", cleanMaterialName(rawName));
            line.put("specification", extractSpecification(rawName));
            line.put("unit", normalizeUnit(matcher.group(2)));
            line.put("requestedQty", new BigDecimal(matcher.group(1)));
            line.put("unitPrice", index == 0 ? unitPrice : null);
            line.put("taxRate", null);
            line.put("requiredDate", extractDate(input));
            line.put("warehouseCode", null);
            line.put("sourceRef", extractAfter(input, "来源单号|MRP|缺料建议"));
            line.put("remark", "用户原话：" + input);
            lines.add(line);
            index++;
        }
        if (lines.isEmpty()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("materialCode", materialCode);
            line.put("materialName", extractAfter(input, "采购|购买"));
            line.put("specification", null);
            line.put("unit", null);
            line.put("requestedQty", null);
            line.put("unitPrice", unitPrice);
            line.put("taxRate", null);
            line.put("requiredDate", extractDate(input));
            line.put("warehouseCode", null);
            line.put("sourceRef", null);
            line.put("remark", "用户原话：" + input);
            lines.add(line);
        }
        requisition.put("lines", lines);

        List<String> needsConfirmation = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Map<String, Object> line = lines.get(i);
            Map<String, Object> material = matchMaterial(line.get("materialCode"), line.get("materialName"));
            if (material == null) needsConfirmation.add("requisition.lines[" + i + "].materialCode");
            else {
                line.put("materialCode", material.get("materialCode"));
                if (string(line.get("unit")) == null) line.put("unit", material.get("unit"));
            }
            if (line.get("requestedQty") == null) needsConfirmation.add("requisition.lines[" + i + "].requestedQty");
            if (line.get("unit") == null) needsConfirmation.add("requisition.lines[" + i + "].unit");
            if (line.get("requiredDate") == null) needsConfirmation.add("requisition.lines[" + i + "].requiredDate");
        }
        if (requisition.get("departmentCode") == null) needsConfirmation.add("requisition.departmentCode");
        if (requisition.get("reason") == null) needsConfirmation.add("requisition.reason");

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("intent", "CREATE_PURCHASE_REQUISITION");
        draft.put("confidence", lines.stream().anyMatch(line -> line.get("materialCode") == null) ? 0.78 : 0.94);
        draft.put("needsConfirmation", needsConfirmation);
        draft.put("requisition", requisition);
        draft.put("routeExplanation", List.of());
        draft.put("riskFlags", List.of());
        draft.put("evidence", evidence(input, lines));
        return draft;
    }

    private Map<String, Object> validateDraft(Map<String, Object> draft) {
        Map<String, Object> source = draft;
        Map<String, Object> requisition = castMap(source.get("requisition"));
        Map<String, Object> normalized = new LinkedHashMap<>(requisition);
        normalized.put("requesterCode", TenantContext.require().username());
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<Map<String, Object>> riskFlags = new ArrayList<>();
        if (!"CREATE_PURCHASE_REQUISITION".equals(string(source.get("intent")))) {
            addError(errors, "intent", "当前内容不是创建采购申请意图，请切换到采购申请功能");
        }
        String reason = string(requisition.get("reason"));
        if (reason == null || reason.isBlank()) addMissing(missing, "requisition.reason", "采购原因");
        if (string(requisition.get("orgCode")) == null) addMissing(missing, "requisition.orgCode", "申请组织");
        if (string(requisition.get("departmentCode")) == null) addMissing(missing, "requisition.departmentCode", "申请部门");
        String currency = stringOr(requisition.get("currency"), "CNY").toUpperCase(Locale.ROOT);
        normalized.put("currency", currency);
        if (!List.of("CNY", "USD", "EUR").contains(currency)) {
            addError(errors, "requisition.currency", "币种不受支持，请选择 CNY、USD 或 EUR");
        }
        if (!"CNY".equals(currency)) addRisk(riskFlags, "NON_CNY", "非 CNY 币种，需要财务审批");

        List<Map<String, Object>> rawLines = mapList(requisition.get("lines"));
        List<Map<String, Object>> normalizedLines = new ArrayList<>();
        if (rawLines.isEmpty()) addMissing(missing, "requisition.lines", "至少一条物料明细");
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        for (int i = 0; i < rawLines.size(); i++) {
            Map<String, Object> raw = rawLines.get(i);
            Map<String, Object> line = new LinkedHashMap<>(raw);
            String prefix = "requisition.lines[" + i + "].";
            String materialName = string(raw.get("materialName"));
            if (materialName == null || materialName.isBlank()) addError(errors, prefix + "materialName", "第 " + (i + 1) + " 行物料名称不能为空");
            BigDecimal quantity = decimalOrNull(raw.get("requestedQty"));
            if (quantity == null) addMissing(missing, prefix + "requestedQty", "第 " + (i + 1) + " 行数量");
            else if (quantity.compareTo(BigDecimal.ZERO) <= 0) addError(errors, prefix + "requestedQty", "第 " + (i + 1) + " 行数量必须大于 0");
            String unit = string(raw.get("unit"));
            if (unit == null || unit.isBlank()) addMissing(missing, prefix + "unit", "第 " + (i + 1) + " 行单位");
            LocalDate requiredDate = parseDate(raw.get("requiredDate"));
            if (requiredDate == null) addMissing(missing, prefix + "requiredDate", "第 " + (i + 1) + " 行需求日期");
            else if (requiredDate.isBefore(LocalDate.now())) addError(errors, prefix + "requiredDate", "第 " + (i + 1) + " 行需求日期不能早于今天");

            Map<String, Object> material = matchMaterial(raw.get("materialCode"), materialName);
            if (material == null) addError(errors, prefix + "materialCode", "第 " + (i + 1) + " 行未找到对应物料，请补充有效物料编码");
            else {
                line.put("materialCode", material.get("materialCode"));
                if (unit == null || unit.isBlank()) line.put("unit", material.get("unit"));
                if (unit != null && material.get("unit") != null && !unit.equals(material.get("unit"))) {
                    warnings.add(message(prefix + "unit", "系统主数据单位为 " + material.get("unit") + "，请确认当前单位"));
                }
            }
            BigDecimal unitPrice = decimalOrNull(raw.get("unitPrice"));
            BigDecimal taxRate = decimalOrNull(raw.get("taxRate"));
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) unitPrice = BigDecimal.ZERO;
            if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) < 0) taxRate = BigDecimal.ZERO;
            BigDecimal amount = quantity == null ? BigDecimal.ZERO : quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTax = amount.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            line.put("requestedQty", quantity);
            line.put("unitPrice", unitPrice);
            line.put("taxRate", taxRate);
            line.put("amountValue", amount);
            line.put("taxAmount", lineTax);
            line.put("requiredDate", requiredDate == null ? null : requiredDate.format(DATE_FORMAT));
            normalizedLines.add(line);
            subtotal = subtotal.add(amount);
            taxAmount = taxAmount.add(lineTax);
            if (unitPrice.compareTo(BigDecimal.ZERO) == 0) warnings.add(message(prefix + "unitPrice", "尚未填写预计单价，金额将按 0 计算"));
        }
        normalized.put("lines", normalizedLines);
        BigDecimal total = subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> calculation = new LinkedHashMap<>();
        calculation.put("subtotal", subtotal.setScale(2, RoundingMode.HALF_UP));
        calculation.put("taxAmount", taxAmount.setScale(2, RoundingMode.HALF_UP));
        calculation.put("total", total);
        calculation.put("currency", currency);

        if (total.compareTo(BigDecimal.valueOf(50000)) > 0) addRisk(riskFlags, "HIGH_AMOUNT", "预计金额超过 50,000，需增加财务审批");
        if (total.compareTo(BigDecimal.valueOf(200000)) > 0) addRisk(riskFlags, "MANAGEMENT_AMOUNT", "预计金额超过 200,000，需增加管理者审批");
        if (warnings.stream().anyMatch(item -> String.valueOf(item.get("message")).contains("金额"))) addRisk(riskFlags, "PRICE_MISSING", "存在缺少预计单价的明细");
        if (!rawLines.isEmpty() && errors.stream().anyMatch(item -> String.valueOf(item.get("field")).endsWith("materialCode"))) addRisk(riskFlags, "MATERIAL_UNMATCHED", "存在未匹配主数据的物料");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("normalizedDraft", Map.of("intent", "CREATE_PURCHASE_REQUISITION", "requisition", normalized));
        result.put("calculation", calculation);
        result.put("route", route(total, currency));
        result.put("missing", missing);
        result.put("errors", errors);
        result.put("warnings", warnings);
        result.put("riskFlags", riskFlags);
        return result;
    }

    private List<Map<String, Object>> route(BigDecimal total, String currency) {
        ensureDefaultRules();
        List<Map<String, Object>> rules = jdbc.queryForList(
                "select rule_code,min_amount,max_amount,currency_code,nodes_json from purchase_approval_rule where tenant_id=? and status='ACTIVE' order by priority asc, id asc",
                tenantId());
        Map<String, Object> selected = rules.stream()
                .filter(rule -> matchesRule(rule, total, currency))
                .findFirst()
                .orElse(null);
        List<String> codes = selected == null
                ? fallbackRouteCodes(total, currency)
                : parseNodes(string(selected.get("nodes_json")));
        if (codes.isEmpty()) codes = fallbackRouteCodes(total, currency);
        if (!"CNY".equalsIgnoreCase(currency) && !codes.contains("FINANCE")) codes.add("FINANCE");
        List<Map<String, Object>> result = new ArrayList<>();
        for (String code : codes) {
            result.add(node(code, nodeName(code), candidateGroup(code), candidateRole(code), nodeCondition(code, total, currency)));
        }
        return result;
    }

    private boolean matchesRule(Map<String, Object> rule, BigDecimal total, String currency) {
        String ruleCurrency = string(rule.get("currency_code"));
        if (ruleCurrency != null && !ruleCurrency.isBlank() && !ruleCurrency.equalsIgnoreCase(currency)) return false;
        BigDecimal min = decimalOrNull(rule.get("min_amount"));
        BigDecimal max = decimalOrNull(rule.get("max_amount"));
        return (min == null || total.compareTo(min) >= 0) && (max == null || total.compareTo(max) <= 0);
    }

    private List<String> parseNodes(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<String>>() { }));
        } catch (JsonProcessingException ignored) {
            return new ArrayList<>();
        }
    }

    private List<String> fallbackRouteCodes(BigDecimal total, String currency) {
        List<String> codes = new ArrayList<>(List.of("DEPT_MANAGER", "PROCUREMENT_MANAGER"));
        if (total.compareTo(BigDecimal.valueOf(50000)) > 0 || !"CNY".equals(currency)) codes.add("FINANCE");
        if (total.compareTo(BigDecimal.valueOf(200000)) > 0) codes.add("MANAGEMENT");
        return codes;
    }

    private String nodeName(String code) {
        return switch (code) {
            case "DEPT_MANAGER" -> "部门负责人审批";
            case "PROCUREMENT_MANAGER" -> "采购经理审批";
            case "FINANCE" -> "财务审批";
            case "MANAGEMENT" -> "管理者审批";
            default -> code + "审批";
        };
    }

    private String candidateGroup(String code) {
        return List.of("DEPT_MANAGER", "PROCUREMENT_MANAGER").contains(code) ? "planner" : "admin";
    }

    private String candidateRole(String code) {
        return switch (code) {
            case "DEPT_MANAGER" -> "申请部门负责人（当前租户配置）";
            case "PROCUREMENT_MANAGER" -> "采购经理角色（当前租户配置）";
            case "FINANCE" -> "财务角色（当前租户配置）";
            case "MANAGEMENT" -> "管理者角色（当前租户配置）";
            default -> "系统配置角色（当前租户配置）";
        };
    }

    private String nodeCondition(String code, BigDecimal total, String currency) {
        return switch (code) {
            case "DEPT_MANAGER" -> "所有采购申请";
            case "PROCUREMENT_MANAGER" -> total.compareTo(BigDecimal.valueOf(50000)) <= 0 && "CNY".equals(currency)
                    ? "采购金额 ≤ 50,000 CNY" : "采购金额规则";
            case "FINANCE" -> "金额 > 50,000 或非 CNY";
            case "MANAGEMENT" -> "金额 > 200,000";
            default -> "采购审批规则";
        };
    }

    private Map<String, Object> node(String code, String name, String group, String candidateRole, String condition) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeCode", code);
        node.put("nodeName", name);
        node.put("candidateGroup", group);
        node.put("candidateRole", candidateRole);
        node.put("condition", condition);
        node.put("status", "CONFIGURED");
        return node;
    }

    private Map<String, Object> idempotentResult(Map<String, Object> submission) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", submission.get("session_id"));
        result.put("recordId", submission.get("record_id"));
        result.put("recordNo", submission.get("record_no"));
        result.put("status", submission.get("status"));
        result.put("idempotent", true);
        if (submission.get("record_id") != null) {
            try { result.put("record", erp.detailRecord(((Number) submission.get("record_id")).longValue())); } catch (RuntimeException ignored) { }
        }
        if (submission.get("bpm_instance_id") != null) {
            try { result.put("processInstance", bpm.instanceDetail(String.valueOf(submission.get("bpm_instance_id"))).get("instance")); } catch (RuntimeException ignored) { }
        }
        return result;
    }

    private void saveSession(String sessionId, String input, Map<String, Object> result, String status) {
        String json = json(result);
        try {
            jdbc.update("insert into ai_generation_session(tenant_id,session_id,user_id,intent,input_hash,input_text,draft_json,model,prompt_version,status,created_by) values(?,?,?,?,?,?,?,?,?,?,?)",
                    tenantId(), sessionId, TenantContext.require().userId(), result.get("intent"), sha256(input), input, json, MODEL, PROMPT_VERSION, status, TenantContext.require().username());
        } catch (DuplicateKeyException ex) {
            jdbc.update("update ai_generation_session set draft_json=?,status=?,updated_at=current_timestamp where tenant_id=? and session_id=?", json, status, tenantId(), sessionId);
        }
    }

    private void updateSession(String sessionId, String status, Map<String, Object> draft) {
        jdbc.update("update ai_generation_session set draft_json=?,status=?,updated_at=current_timestamp where tenant_id=? and session_id=?", json(draft), status, tenantId(), sessionId);
    }

    private void saveFieldAudit(String sessionId, Map<String, Object> draft) {
        Map<String, Object> requisition = castMap(draft.get("requisition"));
        for (String field : List.of("reason", "orgCode", "departmentCode", "requesterCode", "currency", "projectCode")) {
            if (requisition.get(field) != null) insertAudit(sessionId, "requisition." + field, "USER_OR_RULE", null, 1.0, null, requisition.get(field));
        }
        for (int i = 0; i < mapList(requisition.get("lines")).size(); i++) {
            Map<String, Object> line = mapList(requisition.get("lines")).get(i);
            for (String field : List.of("materialCode", "materialName", "requestedQty", "unit", "unitPrice", "requiredDate")) {
                if (line.get(field) != null) insertAudit(sessionId, "requisition.lines[" + i + "]." + field, "USER_OR_RULE", null, 1.0, null, line.get(field));
            }
        }
    }

    private void insertAudit(String sessionId, String path, String sourceType, String sourceText, double confidence, Object before, Object after) {
        jdbc.update("insert into ai_generation_field_audit(tenant_id,session_id,field_path,source_type,source_text,confidence,before_value,after_value,edited_by) values(?,?,?,?,?,?,?,?,?)",
                tenantId(), sessionId, path, sourceType, sourceText, confidence, string(before), string(after), TenantContext.require().username());
    }

    private Map<String, Object> findSubmission(String idempotencyKey) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id,session_id,record_id,record_no,bpm_instance_id,status from ai_purchase_submission where tenant_id=? and idempotency_key=? limit 1", tenantId(), idempotencyKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @PostConstruct
    public void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_generation_session (id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, session_id VARCHAR(100) NOT NULL, user_id BIGINT NOT NULL, intent VARCHAR(80), input_hash VARCHAR(128), input_text TEXT, draft_json LONGTEXT NOT NULL, model VARCHAR(100), prompt_version VARCHAR(100), status VARCHAR(30) NOT NULL, created_by VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id,session_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_generation_field_audit (id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, session_id VARCHAR(100) NOT NULL, field_path VARCHAR(180) NOT NULL, source_type VARCHAR(40) NOT NULL, source_text TEXT, confidence DECIMAL(6,4), before_value TEXT, after_value TEXT, edited_by VARCHAR(64), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_purchase_submission (id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, idempotency_key VARCHAR(180) NOT NULL, session_id VARCHAR(100) NOT NULL, record_id BIGINT NULL, record_no VARCHAR(100), bpm_instance_id VARCHAR(150), status VARCHAR(30) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id,idempotency_key))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS purchase_approval_rule (id BIGINT PRIMARY KEY AUTO_INCREMENT, tenant_id BIGINT NOT NULL, rule_code VARCHAR(80) NOT NULL, priority INT NOT NULL DEFAULT 99, min_amount DECIMAL(18,2) NULL, max_amount DECIMAL(18,2) NULL, currency_code VARCHAR(10) NULL, nodes_json TEXT NOT NULL, version VARCHAR(40) NOT NULL DEFAULT 'v0.1', status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', effective_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id,rule_code,version))");
    }

    private void ensureDefaultRules() {
        Integer count = jdbc.queryForObject("select count(*) from purchase_approval_rule where tenant_id=?", Integer.class, tenantId());
        if (count != null && count > 0) return;
        try {
            insertDefaultRule("PURCHASE_AMOUNT_001", 10, "0", "5000.00", "[\"DEPT_MANAGER\",\"PROCUREMENT_MANAGER\"]");
            insertDefaultRule("PURCHASE_AMOUNT_002", 20, "5000.01", "50000.00", "[\"DEPT_MANAGER\",\"PROCUREMENT_MANAGER\"]");
            insertDefaultRule("PURCHASE_AMOUNT_003", 30, "50000.01", "200000.00", "[\"DEPT_MANAGER\",\"PROCUREMENT_MANAGER\",\"FINANCE\"]");
            insertDefaultRule("PURCHASE_AMOUNT_004", 40, "200000.01", null, "[\"DEPT_MANAGER\",\"PROCUREMENT_MANAGER\",\"FINANCE\",\"MANAGEMENT\"]");
        } catch (DuplicateKeyException ignored) {
            // Another request seeded this tenant's defaults concurrently.
        }
    }

    private void insertDefaultRule(String code, int priority, String min, String max, String nodes) {
        jdbc.update("insert into purchase_approval_rule(tenant_id,rule_code,priority,min_amount,max_amount,currency_code,nodes_json,version,status) values(?,?,?,?,?,?,?,?,?)",
                tenantId(), code, priority, new BigDecimal(min), max == null ? null : new BigDecimal(max), null, nodes, "v0.1", "ACTIVE");
    }

    private List<Map<String, Object>> materialOptions() {
        try {
            return jdbc.queryForList("select material_code materialCode, material_name materialName, unit from wh_material where tenant_id=? and status='ACTIVE' order by material_code limit 100", tenantId());
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private Map<String, Object> matchMaterial(Object code, Object name) {
        String materialCode = string(code);
        String materialName = string(name);
        try {
            List<Map<String, Object>> rows;
            if (materialCode != null && !materialCode.isBlank()) {
                rows = jdbc.queryForList("select material_code materialCode,material_name materialName,unit from wh_material where tenant_id=? and status='ACTIVE' and material_code=? limit 2", tenantId(), materialCode.trim());
            } else if (materialName != null && !materialName.isBlank()) {
                rows = jdbc.queryForList("select material_code materialCode,material_name materialName,unit from wh_material where tenant_id=? and status='ACTIVE' and material_name=? limit 2", tenantId(), materialName.trim());
            } else return null;
            return rows.size() == 1 ? rows.get(0) : null;
        } catch (DataAccessException ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> evidence(String input, List<Map<String, Object>> lines) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Map<String, Object> line = lines.get(i);
            addEvidence(evidence, "requisition.lines[" + i + "].requestedQty", "user_text", quote(input, string(line.get("requestedQty")) + " " + string(line.get("unit"))));
            addEvidence(evidence, "requisition.lines[" + i + "].materialName", "user_text", string(line.get("materialName")));
        }
        if (input.contains("单价")) addEvidence(evidence, "requisition.lines[0].unitPrice", "user_text", quote(input, "单价"));
        if (input.contains("月") || input.matches(".*20\\d{2}[-年].*")) addEvidence(evidence, "requisition.lines[0].requiredDate", "user_text", quote(input, "月"));
        return evidence;
    }

    private void addEvidence(List<Map<String, Object>> evidence, String field, String source, String quote) {
        if (quote == null || quote.isBlank()) return;
        evidence.add(Map.of("field", field, "source", source, "quote", quote));
    }

    private String extractReason(String input) {
        String reason = extractAfter(input, "用于|为了|因|因需");
        if (reason != null) return reason.replaceAll("^(采购|购买)", "").split("采购")[0].trim();
        Matcher matcher = Pattern.compile("(?:的|本次)\\s*([^，。,；;。]{2,12})\\s*采购").matcher(input);
        if (matcher.find()) return matcher.group(1).trim() + "备料";
        return null;
    }

    private String extractDepartment(String input) {
        for (Map<String, String> item : List.of(Map.of("name", "研发部", "code", "RND"), Map.of("name", "采购部", "code", "PURCHASE"), Map.of("name", "生产运营部", "code", "OPS"))) {
            if (input.contains(String.valueOf(item.get("name")))) return String.valueOf(item.get("code"));
        }
        return null;
    }

    private String extractCurrency(String input) {
        if (input.contains("美元") || input.toUpperCase(Locale.ROOT).contains("USD")) return "USD";
        if (input.contains("欧元") || input.toUpperCase(Locale.ROOT).contains("EUR")) return "EUR";
        return "CNY";
    }

    private BigDecimal extractPrice(String input) {
        Matcher matcher = PRICE.matcher(input);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    private String extractMaterialCode(String input) {
        Matcher matcher = MATERIAL_CODE.matcher(input);
        if (matcher.find()) return matcher.group(1);
        matcher = Pattern.compile("\\b[A-Z]{2,}(?:-[A-Z0-9]+)+\\b").matcher(input);
        return matcher.find() ? matcher.group(0) : null;
    }

    private String extractAfter(String input, String labels) {
        Matcher matcher = Pattern.compile("(?:" + labels + ")\\s*[:：]?\\s*([^，。,；;。]+)").matcher(input);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractDate(String input) {
        Matcher iso = ISO_DATE.matcher(input);
        if (iso.find()) return LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3))).format(DATE_FORMAT);
        if (input.contains("下个月")) return YearMonth.from(LocalDate.now().plusMonths(1)).atDay(1).format(DATE_FORMAT);
        Matcher month = MONTH_DAY.matcher(input);
        if (month.find()) {
            int monthValue = Integer.parseInt(month.group(1));
            int day = month.group(2) == null ? 1 : Integer.parseInt(month.group(2));
            try { return LocalDate.of(LocalDate.now().getYear(), monthValue, day).format(DATE_FORMAT); } catch (DateTimeException ignored) { return null; }
        }
        return null;
    }

    private String cleanMaterialName(String raw) {
        return raw.replaceAll("^(的|为|采购|购买)\\s*", "").replaceFirst("^[A-Za-z]{2,}[-A-Za-z0-9]+\\s+", "").replaceAll("(?:，|,)?\\s*(?:预计)?(?:含税)?单价.*$", "").trim();
    }

    private String extractSpecification(String name) {
        Matcher matcher = Pattern.compile("([A-Za-z]\\d+(?:[×xX*]\\d+)*)").matcher(name);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String normalizeUnit(String unit) {
        return "公斤".equalsIgnoreCase(unit) || "kg".equalsIgnoreCase(unit) ? "kg" : unit;
    }

    private String quote(String input, String needle) {
        if (needle == null || needle.isBlank()) return null;
        int index = input.indexOf(needle);
        return index < 0 ? needle : input.substring(Math.max(0, index - 10), Math.min(input.length(), index + needle.length() + 16));
    }

    private String earliestDate(List<Map<String, Object>> lines) {
        return lines.stream().map(line -> parseDate(line.get("requiredDate"))).filter(date -> date != null).min(Comparator.naturalOrder()).map(DATE_FORMAT::format).orElse(null);
    }

    private String firstSourceRef(List<Map<String, Object>> lines) {
        return lines.stream().map(line -> string(line.get("sourceRef"))).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }

    private String buildRemark(String sessionId, String reason, Object remark) {
        String raw = string(remark);
        String value = "AI会话 " + sessionId + "；采购原因：" + reason;
        if (raw != null && !raw.isBlank()) value += "；" + raw;
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private boolean isHighRisk(Map<String, Object> requisition, Map<String, Object> calculation) {
        return !"CNY".equalsIgnoreCase(stringOr(requisition.get("currency"), "CNY")) || decimalOrZero(calculation.get("total")).compareTo(BigDecimal.valueOf(50000)) > 0;
    }

    private void addMissing(List<String> list, String field, String label) { if (!list.contains(field)) list.add(field); }

    private void addError(List<Map<String, Object>> errors, String field, String message) { errors.add(message(field, message)); }

    private void addRisk(List<Map<String, Object>> risks, String code, String message) { if (risks.stream().noneMatch(item -> code.equals(item.get("code")))) risks.add(Map.of("code", code, "level", "WARNING", "message", message)); }

    private Map<String, Object> message(String field, String message) { return Map.of("field", field, "message", message); }

    private static Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) if (item instanceof Map<?, ?>) result.add(castMap(item));
        return result;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        return list.stream().map(String::valueOf).toList().stream().collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private static BigDecimal decimalOrNull(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return new BigDecimal(String.valueOf(value).replaceAll("[^0-9.-]", "")); } catch (NumberFormatException ex) { return null; }
    }

    private static BigDecimal decimalOrZero(Object value) { return decimalOrNull(value) == null ? BigDecimal.ZERO : decimalOrNull(value); }

    private static LocalDate parseDate(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return LocalDate.parse(String.valueOf(value).substring(0, 10)); } catch (DateTimeParseException | IndexOutOfBoundsException ignored) { return null; }
    }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }

    private static String stringOr(Object value, String fallback) { String valueText = string(value); return valueText == null || valueText.isBlank() ? fallback : valueText; }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new IllegalArgumentException("AI 草稿序列化失败"); }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("系统不支持 SHA-256"); }
    }

    private String newSessionId() { return "aig-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 8); }

    private long tenantId() { return TenantContext.require().tenantId(); }
}
