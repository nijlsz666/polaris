package com.polaris.mes.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.mes.common.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ReleaseService {
    private static final DateTimeFormatter RELEASE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final List<String> DATA_TABLES = List.of(
            "sys_menu", "sys_permission", "bom", "bom_item", "production_plan", "work_order",
            "inventory", "report_definition", "lowcode_page", "dashboard_config");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;

    public ReleaseService(JdbcTemplate jdbc,
                          ObjectMapper objectMapper,
                          @Value("${polaris.release.storage-path:${java.io.tmpdir}/polaris-releases}") String storagePath) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    public Map<String, Object> overview() {
        return dataOverview();
    }

    public List<Map<String, Object>> list() {
        return dataListReleases().stream().map(this::releaseView).toList();
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> release = dataFindRelease(id);
        if (release == null) throw new IllegalArgumentException("发版版本不存在");
        Map<String, Object> view = releaseView(release);
        view.put("verifications", dataListVerifications(id));
        return view;
    }

    @Transactional
    public Map<String, Object> generate(Map<String, Object> payload, String actor) {
        String version = required(payload, "version", "版本号");
        if (!version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("版本号只能包含字母、数字、点、横线和下划线");
        }
        if (dataFindReleaseByVersion(version) != null) {
            throw new IllegalArgumentException("版本号已存在，请换一个版本号");
        }

        String packageType = upper(payload, "packageType", "DATA");
        if (!List.of("DATA", "DEPLOYMENT").contains(packageType)) {
            throw new IllegalArgumentException("包类型只支持 DATA 或 DEPLOYMENT");
        }
        String sourceEnvironment = upper(payload, "sourceEnvironment", "TEST");
        String targetEnvironment = upper(payload, "targetEnvironment", "PRODUCTION");
        String releaseNo = "REL-" + LocalDateTime.now().format(RELEASE_TIME) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String packageName = "polaris-" + safeFilePart(version) + "-" + packageType.toLowerCase() + ".zip";

        try {
            Map<String, byte[]> entries = buildEntries(packageType);
            Manifest manifest = buildManifest(releaseNo, version, packageType, sourceEnvironment, targetEnvironment, entries);
            byte[] manifestBytes = objectMapper.writeValueAsBytes(manifest.content());
            Path packagePath = writePackage(packageName, entries, manifestBytes, manifest.artifacts());

            Map<String, Object> release = new LinkedHashMap<>();
            release.put("releaseNo", releaseNo);
            release.put("version", version);
            release.put("packageType", packageType);
            release.put("sourceEnvironment", sourceEnvironment);
            release.put("targetEnvironment", targetEnvironment);
            release.put("packageName", packageName);
            release.put("packagePath", packagePath.toString());
            release.put("artifactHash", manifest.artifactHash());
            release.put("manifestJson", new String(manifestBytes, StandardCharsets.UTF_8));
            release.put("status", "GENERATED");
            release.put("verificationStatus", "NOT_VERIFIED");
            release.put("verificationMessage", "等待目标环境校验");
            release.put("createdBy", actor);
            dataInsertRelease(release);
            return releaseView(dataFindReleaseByVersion(version));
        } catch (IOException ex) {
            throw new IllegalArgumentException("发版包生成失败: " + ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> verify(long id, Map<String, Object> payload, String actor) {
        Map<String, Object> release = dataFindRelease(id);
        if (release == null) throw new IllegalArgumentException("发版版本不存在");

        String expectedHash = String.valueOf(release.get("artifact_hash"));
        String actualHash = text(payload, "artifactHash");
        if (actualHash == null || actualHash.isBlank()) {
            actualHash = currentFingerprint(String.valueOf(release.get("package_type")));
        }
        String environment = upper(payload, "environment", String.valueOf(release.get("target_environment")));
        boolean consistent = expectedHash.equalsIgnoreCase(actualHash);
        String status = consistent ? "PASSED" : "FAILED";
        String message = consistent ? "目标环境与发版清单完全一致" : "目标环境与发版清单不一致，请重新生成或检查变更";
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("releaseNo", release.get("release_no"));
        details.put("version", release.get("version"));
        details.put("packageType", release.get("package_type"));
        details.put("entryCount", manifestEntryCount(String.valueOf(release.get("manifest_json"))));
        details.put("checkedAt", LocalDateTime.now().toString());
        dataSaveVerification(id, environment, expectedHash, actualHash, status, json(details), actor, message);

        Map<String, Object> result = releaseView(dataFindRelease(id));
        result.put("consistent", consistent);
        result.put("expectedHash", expectedHash);
        result.put("actualHash", actualHash);
        result.put("message", message);
        return result;
    }

    @Transactional
    public Map<String, Object> publish(long id, String actor) {
        Map<String, Object> release = dataFindRelease(id);
        if (release == null) throw new IllegalArgumentException("发版版本不存在");
        if ("PUBLISHED".equals(String.valueOf(release.get("status")))) return releaseView(release);
        if (!"PASSED".equals(String.valueOf(release.get("verification_status")))) {
            throw new IllegalArgumentException("发布前必须先通过目标环境一致性校验");
        }
        if (dataMarkPublished(id) == 0) {
            throw new IllegalArgumentException("版本状态已变化，请刷新后重试");
        }
        return releaseView(dataFindRelease(id));
    }

    public Path packagePath(long id) {
        Map<String, Object> release = dataFindRelease(id);
        if (release == null) throw new IllegalArgumentException("发版版本不存在");
        Path path = Paths.get(String.valueOf(release.get("package_path"))).toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("发版包文件不存在，请重新生成");
        }
        return path;
    }

    private Map<String, byte[]> buildEntries(String packageType) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        if ("DATA".equals(packageType)) {
            for (String table : DATA_TABLES) {
                List<Map<String, Object>> rows = dataSnapshotTable(table).stream()
                        .map(row -> {
                            Map<String, Object> sorted = new TreeMap<>();
                            sorted.putAll(row);
                            return sorted;
                        })
                        .sorted(Comparator.comparing(this::toJson))
                        .toList();
                Map<String, Object> tableSnapshot = new LinkedHashMap<>();
                tableSnapshot.put("table", table);
                tableSnapshot.put("rows", rows);
                entries.put("data/" + table + ".json", objectMapper.writeValueAsBytes(tableSnapshot));
            }
        } else {
            entries.put("deployment/schema.sql", readClasspath("db/schema.sql"));
            entries.put("deployment/data.sql", readClasspath("db/data.sql"));
            entries.put("deployment/runtime.txt", ("polaris-service\npackageType=DEPLOYMENT\njava=" + System.getProperty("java.version") + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return entries;
    }

    private String currentFingerprint(String packageType) {
        try {
            return buildManifest("VERIFY", "CURRENT", packageType, "CURRENT", "CURRENT", buildEntries(packageType)).artifactHash();
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法读取当前环境清单: " + ex.getMessage());
        }
    }

    private Manifest buildManifest(String releaseNo, String version, String packageType,
                                   String sourceEnvironment, String targetEnvironment,
                                   Map<String, byte[]> entries) {
        List<Map<String, Object>> artifacts = new ArrayList<>();
        entries.forEach((name, bytes) -> {
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("name", name);
            artifact.put("size", bytes.length);
            artifact.put("sha256", sha256(bytes));
            artifacts.add(artifact);
        });
        artifacts.sort(Comparator.comparing(item -> String.valueOf(item.get("name"))));
        String artifactHash = aggregateHash(artifacts);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("releaseNo", releaseNo);
        manifest.put("version", version);
        manifest.put("packageType", packageType);
        manifest.put("sourceEnvironment", sourceEnvironment);
        manifest.put("targetEnvironment", targetEnvironment);
        manifest.put("generatedAt", LocalDateTime.now().toString());
        manifest.put("artifactHash", artifactHash);
        manifest.put("artifacts", artifacts);
        return new Manifest(manifest, artifacts, artifactHash);
    }

    private Path writePackage(String packageName, Map<String, byte[]> entries, byte[] manifestBytes,
                              List<Map<String, Object>> artifacts) throws IOException {
        Files.createDirectories(storageRoot);
        Path path = storageRoot.resolve(packageName).normalize();
        if (!path.startsWith(storageRoot)) throw new IllegalArgumentException("非法的发版包路径");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                put(zip, entry.getKey(), entry.getValue());
            }
            put(zip, "release-manifest.json", manifestBytes);
            String checksums = artifacts.stream()
                    .map(item -> item.get("sha256") + "  " + item.get("name"))
                    .reduce((left, right) -> left + "\n" + right).orElse("") + "\n";
            put(zip, "checksums.sha256", checksums.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private byte[] readClasspath(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream input = resource.getInputStream()) {
            return input.readAllBytes();
        }
    }

    private Map<String, Object> releaseView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (row == null) return view;
        row.forEach((key, value) -> {
            if (!"package_path".equals(key) && !"manifest_json".equals(key) && !"tenant_id".equals(key)) view.put(key, value);
        });
        String manifest = text(row, "manifest_json");
        view.put("artifact_count", manifestEntryCount(manifest));
        view.put("downloadable", row.get("package_path") != null);
        return view;
    }

    private int manifestEntryCount(String manifest) {
        if (manifest == null || manifest.isBlank()) return 0;
        try {
            Map<?, ?> json = objectMapper.readValue(manifest, Map.class);
            Object artifacts = json.get("artifacts");
            return artifacts instanceof List<?> list ? list.size() : 0;
        } catch (JsonProcessingException ex) {
            return 0;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("发版清单序列化失败");
        }
    }

    private String aggregateHash(List<Map<String, Object>> artifacts) {
        String content = artifacts.stream()
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("name"))))
                .map(item -> item.get("name") + ":" + item.get("sha256"))
                .reduce((left, right) -> left + "\n" + right).orElse("");
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }

    private static String safeFilePart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private static String required(Map<String, Object> payload, String key, String label) {
        String value = text(payload, key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }

    private static String upper(Map<String, Object> payload, String key, String fallback) {
        String value = text(payload, key);
        return value == null || value.isBlank() ? fallback.toUpperCase() : value.trim().toUpperCase();
    }

    private static String text(Map<String, Object> payload, String key) {
        if (payload == null || payload.get(key) == null) return null;
        return String.valueOf(payload.get(key));
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("校验明细序列化失败");
        }
    }

    private record Manifest(Map<String, Object> content, List<Map<String, Object>> artifacts, String artifactHash) {
    }

    public List<Map<String, Object>> dataListReleases() {
        return jdbc.queryForList("""
                select id, release_no, version, package_type, source_environment, target_environment,
                       package_name, artifact_hash, status, verification_status, verification_message,
                       created_by, created_at, published_at, verified_at
                  from release_version
                 where tenant_id=?
                 order by id desc
                """, dataTenantId());
    }

    public Map<String, Object> dataOverview() {
        Map<String, Object> dataOverview = new LinkedHashMap<>();
        dataOverview.put("total", dataScalar("select count(*) from release_version where tenant_id=?", dataTenantId()));
        dataOverview.put("generated", dataScalar("select count(*) from release_version where tenant_id=? and status='GENERATED'", dataTenantId()));
        dataOverview.put("published", dataScalar("select count(*) from release_version where tenant_id=? and status='PUBLISHED'", dataTenantId()));
        dataOverview.put("verified", dataScalar("select count(*) from release_version where tenant_id=? and verification_status='PASSED'", dataTenantId()));
        dataOverview.put("failed", dataScalar("select count(*) from release_version where tenant_id=? and verification_status='FAILED'", dataTenantId()));
        List<Map<String, Object>> latest = jdbc.queryForList("""
                select version, package_type, artifact_hash, status, verification_status, created_at
                  from release_version
                 where tenant_id=?
                 order by id desc limit 1
                """, dataTenantId());
        dataOverview.put("latest", latest.isEmpty() ? null : latest.get(0));
        return dataOverview;
    }

    public Map<String, Object> dataFindRelease(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from release_version where tenant_id=? and id=? limit 1", dataTenantId(), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> dataFindReleaseByVersion(String version) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from release_version where tenant_id=? and version=? limit 1", dataTenantId(), version);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> dataSnapshotTable(String table) {
        if (!DATA_TABLES.contains(table)) {
            throw new IllegalArgumentException("不支持的发版数据表: " + table);
        }
        return jdbc.queryForList("select * from " + table + " where tenant_id=?", dataTenantId());
    }

    public int dataInsertRelease(Map<String, Object> release) {
        return jdbc.update("""
                insert into release_version(
                    tenant_id, release_no, version, package_type, source_environment, target_environment,
                    package_name, package_path, artifact_hash, manifest_json, status,
                    verification_status, verification_message, created_by
                ) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                dataTenantId(), release.get("releaseNo"), release.get("version"), release.get("packageType"),
                release.get("sourceEnvironment"), release.get("targetEnvironment"), release.get("packageName"),
                release.get("packagePath"), release.get("artifactHash"), release.get("manifestJson"),
                release.get("status"), release.get("verificationStatus"), release.get("verificationMessage"),
                release.get("createdBy"));
    }

    public int dataMarkPublished(long id) {
        return jdbc.update("update release_version set status='PUBLISHED', published_at=current_timestamp where tenant_id=? and id=? and status='GENERATED' and verification_status='PASSED'", dataTenantId(), id);
    }

    public int dataSaveVerification(long releaseId, String environment, String expectedHash, String actualHash,
                                String status, String detailsJson, String verifiedBy, String message) {
        jdbc.update("""
                insert into release_verification(
                    tenant_id, release_id, environment, expected_hash, actual_hash, status, details_json, verified_by
                ) values(?,?,?,?,?,?,?,?)
                """, dataTenantId(), releaseId, environment, expectedHash, actualHash, status, detailsJson, verifiedBy);
        return jdbc.update("""
                update release_version
                 set verification_status=?, verification_message=?, verified_at=current_timestamp
                 where tenant_id=? and id=?
                """, status, message, dataTenantId(), releaseId);
    }

    public List<Map<String, Object>> dataListVerifications(long releaseId) {
        return jdbc.queryForList("""
                select id, release_id, environment, expected_hash, actual_hash, status, details_json,
                       verified_by, created_at
                  from release_verification
                 where tenant_id=? and release_id=?
                 order by id desc
                """, dataTenantId(), releaseId);
    }

    private Object dataScalar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Object.class, args);
    }

    private long dataTenantId() {
        return TenantContext.require().tenantId();
    }
}
