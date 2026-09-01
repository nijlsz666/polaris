package com.polaris.mes.controller;

import com.polaris.mes.common.ApiResponse;
import com.polaris.mes.service.InformationService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class InformationController {
    private final InformationService information;

    public InformationController(InformationService information) { this.information = information; }

    @GetMapping("/announcements")
    public ApiResponse<List<Map<String, Object>>> announcements() { return ApiResponse.ok(information.listAnnouncements()); }

    @GetMapping("/announcements/{id}")
    public ApiResponse<Map<String, Object>> announcement(@PathVariable long id) { return ApiResponse.ok(information.announcement(id)); }

    @PostMapping("/announcements")
    public ApiResponse<Map<String, Object>> createAnnouncement(@RequestBody Map<String, Object> payload) { return ApiResponse.ok(information.saveAnnouncement(payload, null), "公告已保存"); }

    @PutMapping("/announcements/{id}")
    public ApiResponse<Map<String, Object>> updateAnnouncement(@PathVariable long id, @RequestBody Map<String, Object> payload) { return ApiResponse.ok(information.saveAnnouncement(payload, id), "公告已更新"); }

    @PostMapping("/announcements/{id}/attachments")
    public ApiResponse<Map<String, Object>> announcementAttachments(@PathVariable long id, @RequestPart("files") MultipartFile[] files) { return ApiResponse.ok(information.uploadAnnouncementAttachments(id, files), "公告附件已上传"); }

    @GetMapping("/announcements/{announcementId}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAnnouncementAttachment(@PathVariable long announcementId, @PathVariable long attachmentId) { return download(information.downloadAnnouncementAttachment(announcementId, attachmentId)); }

    @GetMapping("/documents")
    public ApiResponse<List<Map<String, Object>>> documents(@RequestParam(required = false) String category, @RequestParam(required = false) String keyword) { return ApiResponse.ok(information.listDocuments(category, keyword)); }

    @PostMapping("/documents")
    public ApiResponse<Map<String, Object>> uploadDocument(@RequestPart("file") MultipartFile file, @RequestParam(required = false) String title, @RequestParam(required = false) String category, @RequestParam(required = false) String description) { return ApiResponse.ok(information.uploadDocument(file, title, category, description), "资料已上传"); }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable long id) { information.deleteDocument(id); return ApiResponse.ok(null, "资料已删除"); }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable long id) { return download(information.downloadDocument(id)); }

    @GetMapping("/erp/{domain}/records/{recordId}/attachments")
    public ApiResponse<List<Map<String, Object>>> recordAttachments(@PathVariable String domain, @PathVariable long recordId) { return ApiResponse.ok(information.listRecordAttachments(domain, recordId)); }

    @PostMapping("/erp/{domain}/records/{recordId}/attachments")
    public ApiResponse<Map<String, Object>> uploadRecordAttachment(@PathVariable String domain, @PathVariable long recordId, @RequestPart("file") MultipartFile file) { return ApiResponse.ok(information.uploadRecordAttachment(domain, recordId, file), "单据附件已上传"); }

    @GetMapping("/erp/{domain}/records/{recordId}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadRecordAttachment(@PathVariable String domain, @PathVariable long recordId, @PathVariable long attachmentId) { return download(information.downloadRecordAttachment(domain, recordId, attachmentId)); }

    private ResponseEntity<Resource> download(InformationService.FileDownload file) {
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(file.contentType()); } catch (RuntimeException ex) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(mediaType).contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.resource());
    }
}
