package com.polaris.mes.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Announcement, tenant document and business-record attachment boundary. */
public interface InformationService {
    List<Map<String, Object>> listAnnouncements();
    Map<String, Object> announcement(long id);
    Map<String, Object> saveAnnouncement(Map<String, Object> payload, Long id);
    Map<String, Object> uploadAnnouncementAttachments(long id, MultipartFile[] files);
    List<Map<String, Object>> listDocuments(String category, String keyword);
    Map<String, Object> uploadDocument(MultipartFile file, String title, String category, String description);
    void deleteDocument(long id);
    List<Map<String, Object>> listRecordAttachments(String domain, long recordId);
    Map<String, Object> uploadRecordAttachment(String domain, long recordId, MultipartFile file);
    FileDownload downloadAnnouncementAttachment(long announcementId, long attachmentId);
    FileDownload downloadDocument(long id);
    FileDownload downloadRecordAttachment(String domain, long recordId, long attachmentId);

    record FileDownload(Resource resource, String fileName, String contentType, long size) { }
}
