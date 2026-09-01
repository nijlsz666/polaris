package com.polaris.mes.service.impl;

import com.polaris.mes.common.RequestContext;
import com.polaris.mes.common.TenantContext;
import com.polaris.mes.service.InformationService;
import com.polaris.mes.service.TenantStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class InformationServiceImpl implements InformationService {
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final List<String> DOMAINS = List.of("SALES", "PROCUREMENT", "FINANCE", "MASTER");

    private final JdbcTemplate jdbc;
    private final TenantStorageService storage;
    private final Path root;

    public InformationServiceImpl(JdbcTemplate jdbc, TenantStorageService storage,
                                   @Value("${polaris.file-storage-path:${java.io.tmpdir}/polaris-files}") String storagePath) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.root = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initStorage() {
        try { Files.createDirectories(root); }
        catch (IOException ex) { throw new IllegalStateException("无法创建文件存储目录", ex); }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAnnouncements() {
        boolean platform = isPlatformAdmin();
        String sql = "select a.id, a.title, a.summary, a.content, a.cover_image_url, a.status, a.publish_at, " +
                "a.created_by, a.updated_by, a.created_at, a.updated_at, " +
                "(select count(*) from platform_announcement_attachment x where x.announcement_id=a.id) attachment_count " +
                "from platform_announcement a " +
                (platform ? "" : "where a.status='PUBLISHED' and (a.publish_at is null or a.publish_at<=current_timestamp) ") +
                "order by coalesce(a.publish_at,a.created_at) desc, a.id desc";
        return normalizedRows(jdbc.queryForList(sql));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> announcement(long id) {
        Map<String, Object> row = one("select id, title, summary, content, cover_image_url, status, publish_at, created_by, updated_by, created_at, updated_at from platform_announcement where id=?", id);
        if (row == null || (!isPlatformAdmin() && !published(row))) throw new IllegalArgumentException("公告不存在或尚未发布");
        Map<String, Object> result = normalized(row);
        result.put("attachments", announcementAttachments(id));
        return result;
    }

    @Override
    public Map<String, Object> saveAnnouncement(Map<String, Object> payload, Long id) {
        RequestContext.requirePlatformAdmin();
        String title = required(payload, "title", "公告标题");
        String content = required(payload, "content", "公告内容");
        String status = stringOr(payload.get("status"), "DRAFT").toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "PUBLISHED").contains(status)) throw new IllegalArgumentException("公告状态不正确");
        String actor = TenantContext.require().username();
        if (id == null) {
            jdbc.update("insert into platform_announcement(title, summary, content, cover_image_url, status, publish_at, created_by, updated_by) values(?,?,?,?,?,?,?,?)",
                    title, string(payload.get("summary")), content, string(payload.get("coverImageUrl")), status,
                    timestamp(payload.get("publishAt"), status), actor, actor);
            id = ((Number) jdbc.queryForObject("select id from platform_announcement where created_by=? and title=? order by id desc limit 1", Object.class, actor, title)).longValue();
        } else {
            if (one("select id from platform_announcement where id=?", id) == null) throw new IllegalArgumentException("公告不存在");
            jdbc.update("update platform_announcement set title=?, summary=?, content=?, cover_image_url=?, status=?, publish_at=?, updated_by=?, updated_at=current_timestamp where id=?",
                    title, string(payload.get("summary")), content, string(payload.get("coverImageUrl")), status,
                    timestamp(payload.get("publishAt"), status), actor, id);
        }
        return announcement(id);
    }

    @Override
    public Map<String, Object> uploadAnnouncementAttachments(long id, MultipartFile[] files) {
        RequestContext.requirePlatformAdmin();
        if (one("select id from platform_announcement where id=?", id) == null) throw new IllegalArgumentException("公告不存在");
        List<Path> saved = new ArrayList<>();
        try {
            for (MultipartFile file : filesOrEmpty(files)) {
                Stored stored = store(file, "announcements/" + id);
                saved.add(stored.path());
                jdbc.update("insert into platform_announcement_attachment(announcement_id, original_name, storage_path, content_type, file_size, created_by) values(?,?,?,?,?,?)",
                        id, stored.originalName(), stored.relativePath(), stored.contentType(), stored.size(), actor());
            }
            return announcement(id);
        } catch (RuntimeException ex) {
            saved.forEach(this::deleteQuietly);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDocuments(String category, String keyword) {
        StringBuilder sql = new StringBuilder("select id, title, category, description, original_name, content_type, file_size, uploaded_by, created_at from tenant_document where tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(tenantId()));
        if (!blank(category)) { sql.append(" and category=?"); args.add(category.trim().toUpperCase(Locale.ROOT)); }
        if (!blank(keyword)) { sql.append(" and (title like ? or original_name like ? or description like ?)"); String like = "%" + keyword.trim() + "%"; args.add(like); args.add(like); args.add(like); }
        sql.append(" order by created_at desc, id desc");
        return normalizedRows(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @Override
    public Map<String, Object> uploadDocument(MultipartFile file, String title, String category, String description) {
        requireCustomerTenant();
        String normalizedTitle = blank(title) ? safeOriginalName(file) : title.trim();
        if (normalizedTitle.isBlank()) throw new IllegalArgumentException("资料标题不能为空");
        ensureStorageAvailable(file);
        Stored stored = store(file, "tenants/" + tenantId() + "/documents");
        try {
            jdbc.update("insert into tenant_document(tenant_id, title, category, description, original_name, storage_path, content_type, file_size, uploaded_by) values(?,?,?,?,?,?,?,?,?)",
                    tenantId(), normalizedTitle, stringOr(category, "GENERAL").toUpperCase(Locale.ROOT), description,
                    stored.originalName(), stored.relativePath(), stored.contentType(), stored.size(), actor());
            syncStorage(stored.size());
            Number id = jdbc.queryForObject("select id from tenant_document where tenant_id=? and storage_path=?", Number.class, tenantId(), stored.relativePath());
            return normalized(jdbc.queryForMap("select id, title, category, description, original_name, content_type, file_size, uploaded_by, created_at from tenant_document where tenant_id=? and id=?", tenantId(), id));
        } catch (RuntimeException ex) {
            deleteQuietly(stored.path());
            throw ex;
        }
    }

    @Override
    public void deleteDocument(long id) {
        requireCustomerTenant();
        Map<String, Object> row = one("select id, storage_path, file_size, uploaded_by from tenant_document where tenant_id=? and id=?", tenantId(), id);
        if (row == null) throw new IllegalArgumentException("资料不存在");
        if (!isTenantAdmin() && !actor().equals(String.valueOf(row.get("uploaded_by")))) throw new IllegalArgumentException("只能删除自己上传的资料");
        jdbc.update("delete from tenant_document where tenant_id=? and id=?", tenantId(), id);
        syncStorage(-longNumber(row.get("file_size"), 0));
        deleteQuietly(root.resolve(String.valueOf(row.get("storage_path"))).normalize());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRecordAttachments(String domain, long recordId) {
        String normalizedDomain = domain(domain);
        ensureAccessibleRecord(normalizedDomain, recordId);
        return normalizedRows(jdbc.queryForList("select id, domain, record_id, original_name, content_type, file_size, created_by, created_at from erp_business_record_attachment where tenant_id=? and domain=? and record_id=? order by created_at desc, id desc", tenantId(), normalizedDomain, recordId));
    }

    @Override
    public Map<String, Object> uploadRecordAttachment(String domain, long recordId, MultipartFile file) {
        String normalizedDomain = domain(domain);
        ensureAccessibleRecord(normalizedDomain, recordId);
        ensureStorageAvailable(file);
        Stored stored = store(file, "tenants/" + tenantId() + "/erp/" + normalizedDomain + "/" + recordId);
        try {
            jdbc.update("insert into erp_business_record_attachment(tenant_id, record_id, domain, original_name, storage_path, content_type, file_size, created_by) values(?,?,?,?,?,?,?,?)",
                    tenantId(), recordId, normalizedDomain, stored.originalName(), stored.relativePath(), stored.contentType(), stored.size(), actor());
            syncStorage(stored.size());
            Number id = jdbc.queryForObject("select id from erp_business_record_attachment where tenant_id=? and storage_path=?", Number.class, tenantId(), stored.relativePath());
            return normalized(jdbc.queryForMap("select id, domain, record_id, original_name, content_type, file_size, created_by, created_at from erp_business_record_attachment where tenant_id=? and id=?", tenantId(), id));
        } catch (RuntimeException ex) {
            deleteQuietly(stored.path());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownload downloadAnnouncementAttachment(long announcementId, long attachmentId) {
        Map<String, Object> announcement = one("select id, status, publish_at from platform_announcement where id=?", announcementId);
        if (announcement == null || (!isPlatformAdmin() && !published(announcement))) throw new IllegalArgumentException("公告不存在或尚未发布");
        Map<String, Object> row = one("select original_name, storage_path, content_type, file_size from platform_announcement_attachment where announcement_id=? and id=?", announcementId, attachmentId);
        return file(row, "公告附件不存在");
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownload downloadDocument(long id) {
        Map<String, Object> row = one("select id, title, original_name, storage_path, content_type, file_size, uploaded_by from tenant_document where tenant_id=? and id=?", tenantId(), id);
        if (row == null) throw new IllegalArgumentException("资料不存在");
        return file(row, "资料文件不存在");
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownload downloadRecordAttachment(String domain, long recordId, long attachmentId) {
        String normalizedDomain = domain(domain);
        ensureAccessibleRecord(normalizedDomain, recordId);
        Map<String, Object> row = one("select original_name, storage_path, content_type, file_size from erp_business_record_attachment where tenant_id=? and domain=? and record_id=? and id=?", tenantId(), normalizedDomain, recordId, attachmentId);
        return file(row, "单据附件不存在");
    }

    private void ensureAccessibleRecord(String domain, long id) {
        Map<String, Object> row = one("select id, created_by, owner_code, requester_code from erp_business_record where tenant_id=? and domain=? and id=?", tenantId(), domain, id);
        if (row == null) throw new IllegalArgumentException("业务单据不存在");
        if (!isTenantAdmin() && !matchesActor(row)) throw new IllegalArgumentException("只能查看自己的业务单据");
    }

    private boolean matchesActor(Map<String, Object> row) {
        String current = actor();
        return current.equals(String.valueOf(row.get("created_by"))) || current.equals(String.valueOf(row.get("owner_code"))) || current.equals(String.valueOf(row.get("requester_code")));
    }

    private FileDownload file(Map<String, Object> row, String missingMessage) {
        if (row == null) throw new IllegalArgumentException(missingMessage);
        Path path = root.resolve(String.valueOf(row.get("storage_path"))).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IllegalArgumentException(missingMessage);
        Resource resource = new FileSystemResource(path);
        return new FileDownload(resource, safeFileName(String.valueOf(row.get("original_name"))), stringOr(row.get("content_type"), "application/octet-stream"), longNumber(row.get("file_size"), 0));
    }

    private Stored store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的文件");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("单个文件不能超过 100 MB");
        String original = safeOriginalName(file);
        String key = UUID.randomUUID() + "-" + original;
        Path directory = root.resolve(folder).normalize();
        Path target = directory.resolve(key).normalize();
        if (!directory.startsWith(root) || !target.startsWith(root)) throw new IllegalArgumentException("文件存储路径不合法");
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) { Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING); }
            return new Stored(target, root.relativize(target).toString(), original, stringOr(file.getContentType(), "application/octet-stream"), file.getSize());
        } catch (IOException ex) { throw new IllegalArgumentException("文件保存失败", ex); }
    }

    private void ensureStorageAvailable(MultipartFile file) {
        requireCustomerTenant();
        storage.recordUsage(tenantId(), Map.of("usedBytes", currentStorageUsed(), "reason", "文件存储账户初始化"));
        Map<String, Object> snapshot = storage.snapshot(tenantId());
        long size = file == null ? 0 : file.getSize();
        long remaining = longNumber(snapshot.get("remaining_bytes"), 0);
        if (size > remaining) throw new IllegalArgumentException("租户存储空间不足，请联系总管理员扩容");
    }

    private void syncStorage(long delta) {
        long used = Math.max(0, currentStorageUsed() + delta);
        storage.recordUsage(tenantId(), Map.of("usedBytes", used, "reason", delta >= 0 ? "资料或单据附件上传" : "资料删除释放存储"));
    }

    private long currentStorageUsed() { return longNumber(storage.snapshot(tenantId()).get("used_bytes"), 0); }
    private void requireCustomerTenant() { if (isPlatformAdmin()) throw new IllegalArgumentException("平台总管理员不能直接写入租户资料"); }
    private boolean isPlatformAdmin() { TenantContext.Identity identity = TenantContext.require(); return "polaris-admin".equals(identity.tenantCode()) && "platform_admin".equals(identity.roleCode()); }
    private boolean isTenantAdmin() { String role = TenantContext.require().roleCode(); return "admin".equals(role) || isPlatformAdmin(); }
    private long tenantId() { return TenantContext.require().tenantId(); }
    private String actor() { return TenantContext.require().username(); }
    private static String domain(String value) { String normalized = String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT); if (!DOMAINS.contains(normalized)) throw new IllegalArgumentException("业务模块不受支持：" + value); return normalized; }
    private static boolean published(Map<String, Object> row) {
        if (!"PUBLISHED".equalsIgnoreCase(String.valueOf(row.get("status")))) return false;
        Object publishAt = row.get("publish_at");
        if (publishAt == null) return true;
        if (publishAt instanceof java.util.Date date) return !date.toInstant().isAfter(java.time.Instant.now());
        try { return !java.time.LocalDateTime.parse(String.valueOf(publishAt).replace('T', ' '), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).isAfter(java.time.LocalDateTime.now()); }
        catch (RuntimeException ignored) { return true; }
    }
    private List<Map<String, Object>> announcementAttachments(long id) { return normalizedRows(jdbc.queryForList("select id, original_name, content_type, file_size, created_by, created_at from platform_announcement_attachment where announcement_id=? order by created_at, id", id)); }
    private static MultipartFile[] filesOrEmpty(MultipartFile[] files) { return files == null ? new MultipartFile[0] : files; }
    private static String safeOriginalName(MultipartFile file) { return safeFileName(file == null ? "" : file.getOriginalFilename()); }
    private static String safeFileName(String value) { String name = Paths.get(value == null ? "" : value).getFileName().toString().trim(); return name.isBlank() ? "unnamed-file" : name.replaceAll("[^\\p{L}\\p{N}._()\\- ]", "_"); }
    private static String required(Map<String, Object> payload, String key, String label) { String value = string(payload == null ? null : payload.get(key)); if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空"); return value.trim(); }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static String stringOr(Object value, String fallback) { String result = string(value); return result == null || result.isBlank() ? fallback : result; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static long longNumber(Object value, long fallback) { try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }
    private static Object timestamp(Object value, String status) { return blank(string(value)) ? ("PUBLISHED".equals(status) ? java.sql.Timestamp.from(java.time.Instant.now()) : null) : java.sql.Timestamp.valueOf(String.valueOf(value).replace('T', ' ') + (String.valueOf(value).length() == 16 ? ":00" : "")); }
    private Map<String, Object> one(String sql, Object... args) { List<Map<String, Object>> rows = jdbc.queryForList(sql, args).stream().map(this::normalized).toList(); return rows.isEmpty() ? null : rows.get(0); }
    private Map<String, Object> normalized(Map<String, Object> row) { Map<String, Object> result = new LinkedHashMap<>(); row.forEach((key, value) -> result.put(String.valueOf(key).toLowerCase(Locale.ROOT), value)); return result; }
    private List<Map<String, Object>> normalizedRows(List<Map<String, Object>> rows) { return rows.stream().map(this::normalized).toList(); }
    private void deleteQuietly(Path path) { try { if (path != null) Files.deleteIfExists(path); } catch (IOException ignored) { } }
    private record Stored(Path path, String relativePath, String originalName, String contentType, long size) { }
}
