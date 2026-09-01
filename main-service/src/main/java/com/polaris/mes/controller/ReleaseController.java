package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.common.RequestContext;
import com.polaris.mes.service.ReleaseApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/releases")
public class ReleaseController {
    private final ReleaseApplicationService releaseService;

    public ReleaseController(ReleaseApplicationService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(releaseService.overview());
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(releaseService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id) {
        return ApiResponse.ok(releaseService.detail(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> generate(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner");
        return ApiResponse.ok(releaseService.generate(payload, RequestContext.actor(request)), "发版包已生成");
    }

    @PostMapping("/{id}/verify")
    public ApiResponse<Map<String, Object>> verify(@PathVariable long id,
                                                   @RequestBody(required = false) Map<String, Object> payload,
                                                   HttpServletRequest request) {
        RequestContext.requireRole("admin", "planner");
        return ApiResponse.ok(releaseService.verify(id, payload == null ? Map.of() : payload, RequestContext.actor(request)), "一致性校验已完成");
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable long id, HttpServletRequest request) {
        RequestContext.requireRole("admin");
        return ApiResponse.ok(releaseService.publish(id, RequestContext.actor(request)), "版本已快速发布");
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable long id) throws IOException {
        Path path = releaseService.packagePath(id);
        FileSystemResource resource = new FileSystemResource(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(path.getFileName().toString(), StandardCharsets.UTF_8)
                .build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().headers(headers).contentLength(resource.contentLength()).body(resource);
    }
}
