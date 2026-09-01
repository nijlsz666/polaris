package com.polaris.mes.service.impl;

import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.OperationsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coordinates the floor-control workflows that usually get lost between MES,
 * QMS and maintenance systems.  All reads and writes are tenant-bound and all
 * important transitions are checked here instead of trusting the UI.
 */
@Service
@Transactional
public class OperationsServiceImpl implements OperationsService {
    private static final List<String> EQUIPMENT_STATUSES = List.of("RUNNING", "IDLE", "DOWN", "MAINTENANCE", "RETIRED");
    private static final List<String> EXCEPTION_STATUSES = List.of("OPEN", "ACKNOWLEDGED", "CONTAINED", "RESOLVED", "CLOSED");
    private static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            "OPEN", List.of("ACKNOWLEDGED", "CONTAINED"),
            "ACKNOWLEDGED", List.of("CONTAINED"),
            "CONTAINED", List.of("RESOLVED"),
            "RESOLVED", List.of("CLOSED"),
            "CLOSED", List.of());

    private final JdbcTemplate jdbc;

    public OperationsServiceImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openExceptions", scalar("select count(*) from mfg_exception where tenant_id=? and status<>'CLOSED'", tenantId()));
        result.put("criticalExceptions", scalar("select count(*) from mfg_exception where tenant_id=? and status<>'CLOSED' and priority='CRITICAL'", tenantId()));
        result.put("overdueActions", scalar("select count(*) from mfg_exception_action where tenant_id=? and status<>'COMPLETED' and due_at is not null and due_at<current_timestamp", tenantId()));
        result.put("downtimeMinutesToday", scalar("select coalesce(sum(duration_minutes),0) from mfg_downtime_event where tenant_id=? and started_at>=current_date", tenantId()));
        result.put("openDowntime", scalar("select count(*) from mfg_downtime_event where tenant_id=? and status='OPEN'", tenantId()));
        result.put("equipmentAtRisk", scalar("select count(*) from mfg_equipment where tenant_id=? and (status in ('DOWN','MAINTENANCE') or (next_maintenance_at is not null and next_maintenance_at<current_timestamp))", tenantId()));
        result.put("equipmentCount", scalar("select count(*) from mfg_equipment where tenant_id=? and status<>'RETIRED'", tenantId()));
        result.put("recentExceptions", listExceptions(null, null, null).stream().limit(6).toList());
        result.put("equipment", listEquipment(null, null).stream().limit(8).toList());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEquipment(String status, String keyword) {
        StringBuilder sql = new StringBuilder("select id, equipment_code, equipment_name, work_center, model, status, health_score, current_work_order, last_maintenance_at, next_maintenance_at, last_heartbeat_at, remark, created_at, updated_at from mfg_equipment where tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(tenantId()));
        if (!blank(status)) { sql.append(" and status=?"); args.add(status.toUpperCase()); }
        if (!blank(keyword)) { sql.append(" and (equipment_code like ? or equipment_name like ? or work_center like ?)"); String value = "%" + keyword.trim() + "%"; args.add(value); args.add(value); args.add(value); }
        sql.append(" order by status='DOWN' desc, next_maintenance_at is not null desc, equipment_code");
        return rows(sql.toString(), args.toArray());
    }

    @Override
    public Map<String, Object> saveEquipment(Map<String, Object> payload) {
        String code = required(payload, "equipmentCode", "设备编码");
        String name = required(payload, "equipmentName", "设备名称");
        String status = stringOr(payload.get("status"), "RUNNING").toUpperCase();
        if (!EQUIPMENT_STATUSES.contains(status)) throw new IllegalArgumentException("设备状态不受支持");
        int health = number(payload.get("healthScore"), 100);
        if (health < 0 || health > 100) throw new IllegalArgumentException("健康度必须在 0 到 100 之间");
        Map<String, Object> existing = one("select id from mfg_equipment where tenant_id=? and equipment_code=?", tenantId(), code);
        if (existing == null) {
            jdbc.update("insert into mfg_equipment(tenant_id,equipment_code,equipment_name,work_center,model,status,health_score,current_work_order,next_maintenance_at,remark) values(?,?,?,?,?,?,?,?,?,?)",
                    tenantId(), code, name, string(payload.get("workCenter")), string(payload.get("model")), status, health,
                    string(payload.get("currentWorkOrder")), timestamp(payload.get("nextMaintenanceAt"), null), string(payload.get("remark")));
        } else {
            jdbc.update("update mfg_equipment set equipment_name=?,work_center=?,model=?,status=?,health_score=?,current_work_order=?,next_maintenance_at=?,remark=?,updated_at=current_timestamp where tenant_id=? and equipment_code=?",
                    name, string(payload.get("workCenter")), string(payload.get("model")), status, health,
                    string(payload.get("currentWorkOrder")), timestamp(payload.get("nextMaintenanceAt"), null), string(payload.get("remark")), tenantId(), code);
        }
        return equipment(code);
    }

    @Override
    public Map<String, Object> heartbeat(long id, Map<String, Object> payload) {
        Map<String, Object> equipment = equipment(id);
        String status = stringOr(payload.get("status"), String.valueOf(equipment.get("status"))).toUpperCase();
        if (!EQUIPMENT_STATUSES.contains(status)) throw new IllegalArgumentException("设备状态不受支持");
        int health = number(payload.get("healthScore"), number(equipment.get("health_score"), 100));
        if (health < 0 || health > 100) throw new IllegalArgumentException("健康度必须在 0 到 100 之间");
        jdbc.update("update mfg_equipment set status=?,health_score=?,current_work_order=?,last_heartbeat_at=current_timestamp,updated_at=current_timestamp where tenant_id=? and id=?",
                status, health, stringOr(payload.get("currentWorkOrder"), string(equipment.get("current_work_order"))), tenantId(), id);
        return equipment(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDowntime(String status, String equipmentCode) {
        StringBuilder sql = new StringBuilder("select d.id,d.event_no,d.equipment_code,e.equipment_name,d.work_center,d.work_order_no,d.reason_code,d.reason_name,d.severity,d.description,d.started_at,d.ended_at,d.duration_minutes,d.status,d.reported_by,d.created_at from mfg_downtime_event d left join mfg_equipment e on e.tenant_id=d.tenant_id and e.equipment_code=d.equipment_code where d.tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(tenantId()));
        if (!blank(status)) { sql.append(" and d.status=?"); args.add(status.toUpperCase()); }
        if (!blank(equipmentCode)) { sql.append(" and d.equipment_code=?"); args.add(equipmentCode); }
        sql.append(" order by d.status='OPEN' desc,d.started_at desc");
        return rows(sql.toString(), args.toArray());
    }

    @Override
    public Map<String, Object> startDowntime(Map<String, Object> payload, String actor) {
        String equipmentCode = required(payload, "equipmentCode", "设备编码");
        Map<String, Object> equipment = equipment(equipmentCode);
        String reasonCode = required(payload, "reasonCode", "停机原因");
        Timestamp startedAt = timestamp(payload.get("startedAt"), new Timestamp(System.currentTimeMillis()));
        String eventNo = code("DT");
        String severity = stringOr(payload.get("severity"), "MEDIUM").toUpperCase();
        jdbc.update("insert into mfg_downtime_event(tenant_id,event_no,equipment_code,work_center,work_order_no,reason_code,reason_name,severity,description,started_at,status,reported_by) values(?,?,?,?,?,?,?,?,?,?,?,?)",
                tenantId(), eventNo, equipmentCode, string(equipment.get("work_center")), string(payload.get("workOrderNo")), reasonCode,
                stringOr(payload.get("reasonName"), reasonCode), severity, string(payload.get("description")), startedAt, "OPEN", actor);
        jdbc.update("update mfg_equipment set status='DOWN',updated_at=current_timestamp where tenant_id=? and equipment_code=?", tenantId(), equipmentCode);
        String exceptionNo = createInternalException(
                "EQUIPMENT", severityToPriority(severity), "DOWNTIME", eventNo, equipmentCode,
                string(equipment.get("work_center")), string(payload.get("workOrderNo")),
                "设备停机：" + string(equipment.get("equipment_name")),
                stringOr(payload.get("description"), "设备发生停机，请现场确认原因并完成恢复与根因分析"), actor, null);
        Map<String, Object> result = one("select id,event_no,equipment_code,work_center,work_order_no,reason_code,reason_name,severity,description,started_at,status,reported_by,created_at from mfg_downtime_event where tenant_id=? and event_no=?", tenantId(), eventNo);
        result.put("exceptionNo", exceptionNo);
        return result;
    }

    @Override
    public Map<String, Object> resumeDowntime(long id, String actor) {
        Map<String, Object> event = one("select id,event_no,equipment_code,started_at,status from mfg_downtime_event where tenant_id=? and id=?", tenantId(), id);
        if (event == null) throw new IllegalArgumentException("停机事件不存在");
        if (!"OPEN".equals(String.valueOf(event.get("status")))) throw new IllegalArgumentException("停机事件已恢复，无需重复操作");
        Timestamp endedAt = new Timestamp(System.currentTimeMillis());
        long duration = Math.max(0, Duration.between(asDateTime(event.get("started_at")), endedAt.toLocalDateTime()).toMinutes());
        jdbc.update("update mfg_downtime_event set ended_at=?,duration_minutes=?,status='CLOSED' where tenant_id=? and id=? and status='OPEN'", endedAt, duration, tenantId(), id);
        Integer openOther = ((Number) scalar("select count(*) from mfg_downtime_event where tenant_id=? and equipment_code=? and status='OPEN'", tenantId(), event.get("equipment_code"))).intValue();
        if (openOther == 0) jdbc.update("update mfg_equipment set status='RUNNING',last_heartbeat_at=current_timestamp,updated_at=current_timestamp where tenant_id=? and equipment_code=?", tenantId(), event.get("equipment_code"));
        Map<String, Object> result = one("select id,event_no,equipment_code,started_at,ended_at,duration_minutes,status,reported_by from mfg_downtime_event where tenant_id=? and id=?", tenantId(), id);
        result.put("resumedBy", actor);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listExceptions(String status, String priority, String keyword) {
        StringBuilder sql = new StringBuilder("select e.id,e.exception_no,e.category,e.priority,e.source_type,e.source_ref,e.equipment_code,e.work_center,e.work_order_no,e.title,e.description,e.impact_qty,e.owner_code,e.due_at,e.status,e.detected_at,e.acknowledged_at,e.resolved_at,e.closed_at,e.created_by,e.created_at,e.updated_at,(select count(*) from mfg_exception_action a where a.tenant_id=e.tenant_id and a.exception_id=e.id and a.status<>'COMPLETED') open_action_count from mfg_exception e where e.tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(tenantId()));
        if (!blank(status)) { sql.append(" and e.status=?"); args.add(status.toUpperCase()); }
        if (!blank(priority)) { sql.append(" and e.priority=?"); args.add(priority.toUpperCase()); }
        if (!blank(keyword)) { sql.append(" and (e.exception_no like ? or e.title like ? or e.description like ? or e.equipment_code like ? or e.work_order_no like ?)"); String value = "%" + keyword.trim() + "%"; for (int i = 0; i < 5; i++) args.add(value); }
        sql.append(" order by case e.priority when 'CRITICAL' then 1 when 'HIGH' then 2 when 'MEDIUM' then 3 else 4 end,e.status='CLOSED',e.created_at desc");
        return rows(sql.toString(), args.toArray());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getException(long id) {
        Map<String, Object> result = exception(id);
        result.put("actions", listActions(id));
        return result;
    }

    @Override
    public Map<String, Object> createException(Map<String, Object> payload, String actor) {
        String title = required(payload, "title", "异常标题");
        String description = required(payload, "description", "异常描述");
        String idempotencyKey = string(payload.get("idempotencyKey"));
        if (!blank(idempotencyKey)) {
            Map<String, Object> existing = one("select id from mfg_exception where tenant_id=? and idempotency_key=?", tenantId(), idempotencyKey);
            if (existing != null) return getException(number(existing.get("id"), 0));
        }
        String priority = stringOr(payload.get("priority"), "MEDIUM").toUpperCase();
        if (!PRIORITIES.contains(priority)) throw new IllegalArgumentException("异常优先级不受支持");
        String exceptionNo = createInternalException(
                stringOr(payload.get("category"), "PROCESS").toUpperCase(), priority,
                string(payload.get("sourceType")), string(payload.get("sourceRef")), string(payload.get("equipmentCode")),
                string(payload.get("workCenter")), string(payload.get("workOrderNo")), title, description, actor,
                blank(idempotencyKey) ? null : idempotencyKey, payload);
        return getException(number(one("select id from mfg_exception where tenant_id=? and exception_no=?", tenantId(), exceptionNo).get("id"), 0));
    }

    @Override
    public Map<String, Object> transitionException(long id, Map<String, Object> payload, String actor) {
        Map<String, Object> current = exception(id);
        String from = String.valueOf(current.get("status"));
        String to = required(payload, "status", "目标状态").toUpperCase();
        if (!EXCEPTION_STATUSES.contains(to) || !TRANSITIONS.getOrDefault(from, List.of()).contains(to)) {
            throw new IllegalArgumentException("异常状态不能从 " + from + " 流转到 " + to);
        }
        if ("CLOSED".equals(to)) {
            Number openActions = (Number) scalar("select count(*) from mfg_exception_action where tenant_id=? and exception_id=? and status<>'COMPLETED'", tenantId(), id);
            if (openActions != null && openActions.intValue() > 0) throw new IllegalArgumentException("仍有未完成责任动作，不能关闭异常");
        }
        String timeColumn = switch (to) { case "ACKNOWLEDGED" -> "acknowledged_at"; case "RESOLVED" -> "resolved_at"; case "CLOSED" -> "closed_at"; default -> null; };
        String sql = "update mfg_exception set status=?" + (timeColumn == null ? "" : "," + timeColumn + "=current_timestamp") + ",updated_at=current_timestamp where tenant_id=? and id=? and status=?";
        if (jdbc.update(sql, to, tenantId(), id, from) == 0) throw new IllegalArgumentException("异常已被其他人更新，请刷新后重试");
        Map<String, Object> result = exception(id);
        result.put("changedBy", actor);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listActions(long exceptionId) {
        exception(exceptionId);
        return rows("select id,exception_id,action_type,action_description,owner_code,due_at,status,completed_at,completed_by,created_at from mfg_exception_action where tenant_id=? and exception_id=? order by status='COMPLETED',due_at is null,due_at,created_at", tenantId(), exceptionId);
    }

    @Override
    public Map<String, Object> createAction(long exceptionId, Map<String, Object> payload, String actor) {
        exception(exceptionId);
        String description = required(payload, "actionDescription", "责任动作");
        Timestamp dueAt = timestamp(payload.get("dueAt"), new Timestamp(System.currentTimeMillis() + 4 * 60 * 60 * 1000));
        jdbc.update("insert into mfg_exception_action(tenant_id,exception_id,action_type,action_description,owner_code,due_at,status) values(?,?,?,?,?,?,?)",
                tenantId(), exceptionId, stringOr(payload.get("actionType"), "CONTAINMENT").toUpperCase(), description, stringOr(payload.get("ownerCode"), actor), dueAt, "OPEN");
        List<Map<String, Object>> actions = listActions(exceptionId);
        return actions.get(actions.size() - 1);
    }

    @Override
    public Map<String, Object> completeAction(long actionId, String actor) {
        int updated = jdbc.update("update mfg_exception_action set status='COMPLETED',completed_at=current_timestamp,completed_by=? where tenant_id=? and id=? and status<>'COMPLETED'", actor, tenantId(), actionId);
        if (updated == 0) throw new IllegalArgumentException("责任动作不存在或已完成");
        return one("select id,exception_id,action_type,action_description,owner_code,due_at,status,completed_at,completed_by,created_at from mfg_exception_action where tenant_id=? and id=?", tenantId(), actionId);
    }

    private String createInternalException(String category, String priority, String sourceType, String sourceRef, String equipmentCode,
                                           String workCenter, String workOrderNo, String title, String description, String actor,
                                           String idempotencyKey) {
        return createInternalException(category, priority, sourceType, sourceRef, equipmentCode, workCenter, workOrderNo, title, description, actor, idempotencyKey, Map.of());
    }

    private String createInternalException(String category, String priority, String sourceType, String sourceRef, String equipmentCode,
                                           String workCenter, String workOrderNo, String title, String description, String actor,
                                           String idempotencyKey, Map<String, Object> payload) {
        String exceptionNo = code("EXC");
        Timestamp dueAt = timestamp(payload.get("dueAt"), null);
        if (dueAt == null && "CRITICAL".equals(priority)) dueAt = new Timestamp(System.currentTimeMillis() + 2 * 60 * 60 * 1000);
        jdbc.update("insert into mfg_exception(tenant_id,exception_no,idempotency_key,category,priority,source_type,source_ref,equipment_code,work_center,work_order_no,title,description,impact_qty,owner_code,due_at,status,created_by) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                tenantId(), exceptionNo, idempotencyKey, category, priority, sourceType, sourceRef, equipmentCode, workCenter, workOrderNo,
                title, description, number(payload.get("impactQty"), 0), string(payload.get("ownerCode")), dueAt, "OPEN", actor);
        try {
            jdbc.update("insert into platform_notification(tenant_id,notification_type,title,content,level,action_url) values(?,?,?,?,?,?)",
                    tenantId(), "EXCEPTION", "新的现场异常：" + title, description, "CRITICAL".equals(priority) ? "ERROR" : "WARNING", "/manufacturing/exceptions");
        } catch (RuntimeException ignored) {
            // A minimal legacy database may not have notifications yet; the
            // exception itself must not be lost because of an optional alert.
        }
        return exceptionNo;
    }

    private Map<String, Object> equipment(long id) {
        Map<String, Object> row = one("select id,equipment_code,equipment_name,work_center,model,status,health_score,current_work_order,last_maintenance_at,next_maintenance_at,last_heartbeat_at,remark,created_at,updated_at from mfg_equipment where tenant_id=? and id=?", tenantId(), id);
        if (row == null) throw new IllegalArgumentException("设备不存在");
        return row;
    }

    private Map<String, Object> equipment(String code) {
        Map<String, Object> row = one("select id,equipment_code,equipment_name,work_center,model,status,health_score,current_work_order,last_maintenance_at,next_maintenance_at,last_heartbeat_at,remark,created_at,updated_at from mfg_equipment where tenant_id=? and equipment_code=?", tenantId(), code);
        if (row == null) throw new IllegalArgumentException("设备不存在：" + code);
        return row;
    }

    private Map<String, Object> exception(long id) {
        Map<String, Object> row = one("select id,exception_no,category,priority,source_type,source_ref,equipment_code,work_center,work_order_no,title,description,impact_qty,owner_code,due_at,status,detected_at,acknowledged_at,resolved_at,closed_at,created_by,created_at,updated_at,(select count(*) from mfg_exception_action a where a.tenant_id=e.tenant_id and a.exception_id=e.id and a.status<>'COMPLETED') open_action_count from mfg_exception e where e.tenant_id=? and e.id=?", tenantId(), id);
        if (row == null) throw new IllegalArgumentException("现场异常不存在");
        return row;
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().map(row -> {
            Map<String, Object> normalized = new LinkedHashMap<>();
            row.forEach((key, value) -> normalized.put(String.valueOf(key).toLowerCase(java.util.Locale.ROOT), value));
            return normalized;
        }).toList();
    }
    private Map<String, Object> one(String sql, Object... args) { List<Map<String, Object>> result = rows(sql, args); return result.isEmpty() ? null : new LinkedHashMap<>(result.get(0)); }
    private Object scalar(String sql, Object... args) { return jdbc.queryForObject(sql, Object.class, args); }
    private long tenantId() { return TenantContext.require().tenantId(); }
    private static String code(String prefix) { return prefix + "-" + LocalDateTime.now().toString().replaceAll("[^0-9]", "").substring(0, 14) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(); }
    private static String severityToPriority(String value) { return "CRITICAL".equals(value) ? "CRITICAL" : "HIGH".equals(value) ? "HIGH" : "MEDIUM"; }
    private static String required(Map<String, Object> payload, String key, String label) { String value = string(payload.get(key)); if (blank(value)) throw new IllegalArgumentException(label + "不能为空"); return value.trim(); }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static String stringOr(Object value, String fallback) { String result = string(value); return blank(result) ? fallback : result; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static int number(Object value, int fallback) { if (value == null) return fallback; try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }
    private static Timestamp timestamp(Object value, Timestamp fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        if (value instanceof Timestamp timestamp) return timestamp;
        try {
            String raw = String.valueOf(value).trim().replace('T', ' ');
            if (raw.length() == 16) raw += ":00";
            return Timestamp.valueOf(raw);
        } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("时间格式应为 yyyy-MM-dd HH:mm:ss"); }
    }
    private static LocalDateTime asDateTime(Object value) { if (value instanceof Timestamp ts) return ts.toLocalDateTime(); return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T')); }
}
