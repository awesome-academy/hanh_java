package com.psms.service;

import com.psms.dto.response.ApplicationDocumentResponse;
import com.psms.entity.Application;
import com.psms.entity.ApplicationDocument;
import com.psms.entity.ApplicationStatusHistory;
import com.psms.entity.User;
import com.psms.enums.ApplicationStatus;
import com.psms.enums.ValidationStatus;
import com.psms.exception.BusinessException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.mapper.ApplicationDocumentMapper;
import com.psms.repository.ApplicationDocumentRepository;
import com.psms.repository.ApplicationRepository;
import com.psms.repository.ApplicationStatusHistoryRepository;
import com.psms.repository.CitizenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service xử lý nghiệp vụ tài liệu hồ sơ (ApplicationDocument).
 *
 * <p>Tách ra khỏi ApplicationService để đảm bảo Single Responsibility.
 * ApplicationService = vòng đời hồ sơ (status, history).
 * DocumentService = tài liệu đính kèm (upload, download, validate, delete).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final ApplicationDocumentRepository documentRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final CitizenRepository citizenRepository;
    private final ApplicationDocumentMapper documentMapper;
    private final FileStorageService fileStorageService;

    // ─── Save documents khi nộp hồ sơ (hoặc bổ sung) ────────────────────

    /**
     * Lưu nhiều file đính kèm cho 1 hồ sơ.
     * Dùng cho: nộp hồ sơ lần đầu + upload bổ sung từ citizen.
     */
    @Transactional
    public void saveDocuments(Application application, List<MultipartFile> files,
                               User uploadedBy, boolean isResponse) {
        if (files == null || files.isEmpty()) return;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            String relativePath = fileStorageService.store(file, application.getId());
            String ext = extractExtension(file.getOriginalFilename());

            ApplicationDocument doc = ApplicationDocument.builder()
                    .application(application)
                    .fileName(file.getOriginalFilename())
                    .filePath(relativePath)
                    .fileType(ext)
                    .fileSize(file.getSize())
                    .uploadedBy(uploadedBy)
                    .isResponse(isResponse)
                    .validationStatus(ValidationStatus.PENDING)
                    .build();

            documentRepository.save(doc);
            log.debug("Saved document: appId={} path={} isResponse={}", application.getId(), relativePath, isResponse);
        }
    }

    // ─── Upload bổ sung từ citizen (chỉ khi ADDITIONAL_REQUIRED) ─────────

    /**
     * Citizen upload tài liệu bổ sung khi admin yêu cầu.
     *
     * <p>Business rules:
     * <ul>
     *   <li>Chỉ cho phép khi status = ADDITIONAL_REQUIRED</li>
     *   <li>Sau khi upload thành công → auto-transition ADDITIONAL_REQUIRED → SUBMITTED</li>
     *   <li>Ghi ApplicationStatusHistory cho transition này</li>
     *   <li>Ownership: citizen chỉ upload được HS của mình</li>
     * </ul>
     */
    @Transactional
    public void uploadSupplementalDocuments(Long applicationId, Long userId,
                                             List<MultipartFile> files) {
        var citizen = citizenRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin công dân"));

        Application app = applicationRepository.findByIdAndCitizenId(applicationId, citizen.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ không tồn tại hoặc bạn không có quyền xem"));

        if (app.getStatus() != ApplicationStatus.ADDITIONAL_REQUIRED) {
            throw new BusinessException(
                    "Chỉ có thể nộp bổ sung khi hồ sơ ở trạng thái 'Yêu cầu bổ sung'. "
                    + "Trạng thái hiện tại: " + app.getStatus().getLabel());
        }

        if (files == null || files.stream().allMatch(f -> f == null || f.isEmpty())) {
            throw new BusinessException("Vui lòng chọn ít nhất 1 file để nộp bổ sung");
        }

        saveDocuments(app, files, citizen.getUser(), false);

        // Auto-transition: ADDITIONAL_REQUIRED → SUBMITTED + ghi history
        ApplicationStatus oldStatus = app.getStatus();
        app.setStatus(ApplicationStatus.SUBMITTED);
        applicationRepository.save(app);

        historyRepository.save(ApplicationStatusHistory.builder()
                .application(app)
                .oldStatus(oldStatus)
                .newStatus(ApplicationStatus.SUBMITTED)
                .changedBy(citizen.getUser())
                .notes("Công dân nộp bổ sung tài liệu")
                .build());

        log.info("Supplemental docs uploaded: appId={} userId={} → SUBMITTED", applicationId, userId);
    }

    // ─── Admin upload phản hồi ────────────────────────────────────────────

    /**
     * Cán bộ upload tài liệu phản hồi cho hồ sơ (is_response = true).
     */
    @Transactional
    public void uploadResponseDocuments(Long applicationId, List<MultipartFile> files, User admin) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ không tồn tại: " + applicationId));

        if (files == null || files.stream().allMatch(f -> f == null || f.isEmpty())) {
            throw new BusinessException("Vui lòng chọn ít nhất 1 file để upload");
        }

        saveDocuments(app, files, admin, true);
        log.info("Response docs uploaded: appId={} by adminId={}", applicationId, admin.getId());
    }

    // ─── Admin validate tài liệu ──────────────────────────────────────────

    /**
     * Cán bộ đánh dấu tài liệu VALID hoặc INVALID.
     */
    @Transactional
    public ApplicationDocumentResponse validateDocument(Long docId,
                                                        ValidationStatus newStatus,
                                                        User admin) {
        ApplicationDocument doc = documentRepository.findByIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài liệu không tồn tại: " + docId));

        doc.setValidationStatus(newStatus);
        documentRepository.save(doc);

        log.info("Document validated: docId={} status={} by adminId={}", docId, newStatus, admin.getId());

        ApplicationDocumentResponse response = documentMapper.toResponse(doc);
        response.setDownloadUrl("/files/" + doc.getFilePath());
        return response;
    }

    // ─── Xóa tài liệu (soft delete) ──────────────────────────────────────

    /**
     * Xóa mềm tài liệu với kiểm tra phân quyền đầy đủ.
     *
     * <p>Permission matrix:
     * <ul>
     *   <li>CITIZEN — chỉ xóa doc của mình (is_response=false), chỉ khi status=SUBMITTED</li>
     *   <li>STAFF — chỉ xóa tài liệu phản hồi (is_response=true) do chính mình upload</li>
     *   <li>MANAGER / SUPER_ADMIN — xóa bất kỳ doc nào</li>
     * </ul>
     */

    @Transactional
    public void deleteDocument(Long applicationId, Long docId, User currentUser) {
        ApplicationDocument doc = findDocumentOrThrow(docId);
        validateDocumentBelongsToApplication(doc, applicationId);
        validateDeletePermission(doc, currentUser);

        doc.setDeleted(true);
        documentRepository.save(doc);

        log.info("Document soft-deleted: docId={} fileName='{}' by userId={}",
            docId, doc.getFileName(), currentUser.getId());
    }

// ── private helpers ───────────────────────────────────────────────────────────

    private ApplicationDocument findDocumentOrThrow(Long docId) {
        return documentRepository.findByIdAndIsDeletedFalse(docId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Tài liệu không tồn tại hoặc đã bị xóa"));
    }


    private void validateDocumentBelongsToApplication(ApplicationDocument doc,
                                                      Long applicationId) {
        if (!doc.getApplication().getId().equals(applicationId)) {
            throw new BusinessException("Tài liệu không thuộc hồ sơ này");
        }
    }

    private void validateDeletePermission(ApplicationDocument doc, User currentUser) {
        if (hasRole(currentUser, "SUPER_ADMIN") || hasRole(currentUser, "MANAGER")) {
            return; // không giới hạn
        }
        if (hasRole(currentUser, "STAFF")) {
            validateStaffDeletePermission(doc, currentUser);
            return;
        }
        if (hasRole(currentUser, "CITIZEN")) {
            validateCitizenDeletePermission(doc, currentUser);
            return;
        }
        throw new BusinessException("Không có quyền xóa tài liệu này");
    }

    private void validateStaffDeletePermission(ApplicationDocument doc, User currentUser) {
        // STAFF chỉ xóa tài liệu phản hồi (is_response=true) do chính mình upload
        if (!doc.isResponse()) {
            throw new BusinessException(
                "Cán bộ chỉ có thể xóa tài liệu phản hồi. Đây là tài liệu do công dân nộp, không có quyền xóa.");
        }
        if (!doc.getUploadedBy().getId().equals(currentUser.getId())) {
            throw new BusinessException(
                "Cán bộ chỉ có thể xóa tài liệu phản hồi do chính mình upload.");
        }
    }

    private void validateCitizenDeletePermission(ApplicationDocument doc,
                                                 User currentUser) {
        if (doc.isResponse()) {
            throw new BusinessException("Đây là tài liệu phản hồi từ cán bộ, công dân không có quyền xóa.");
        }

        Application app = doc.getApplication();

        if (app.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new BusinessException(
                "Chỉ có thể xóa tài liệu khi hồ sơ ở trạng thái 'Đã nộp'. "
                    + "Trạng thái hiện tại: " + app.getStatus().getLabel());
        }

        Long citizenId = citizenRepository.findByUserId(currentUser.getId())
            .orElseThrow(() -> new BusinessException("Không có quyền xóa tài liệu này"))
            .getId();

        if (!app.getCitizen().getId().equals(citizenId)) {
            throw new BusinessException("Không có quyền xóa tài liệu này");
        }
    }

    // ─── Query ────────────────────────────────────────────────────────────

    /** Tài liệu citizen nộp (is_response=false), chưa bị xóa. */
    public List<ApplicationDocumentResponse> findCitizenDocuments(Long applicationId) {
        return toResponsesWithUrl(
                documentRepository.findByApplicationAndResponse(applicationId, false));
    }

    /** Tài liệu phản hồi cán bộ (is_response=true), chưa bị xóa. */
    public List<ApplicationDocumentResponse> findStaffDocuments(Long applicationId) {
        return toResponsesWithUrl(
                documentRepository.findByApplicationAndResponse(applicationId, true));
    }

    // ─── Download authorization ────────────────────────────────────────────

    /**
     * Kiểm tra quyền download.
     *
     * <p>Trả về entity thay vì void/String để tránh query DB thứ hai ở controller.
     *
     * <p>Rules:
     * <ul>
     *   <li>STAFF / MANAGER / SUPER_ADMIN — download tất cả</li>
     *   <li>CITIZEN — chỉ download file thuộc hồ sơ của mình</li>
     *   <li>File đã xóa mềm (is_deleted=true) → 404</li>
     * </ul>
     */
    public ApplicationDocument authorizeDownload(String relativePath, User currentUser) {
        ApplicationDocument doc = documentRepository.findByFilePathAndIsDeletedFalse(relativePath)
                .orElseThrow(() -> new ResourceNotFoundException("File không tồn tại"));

        boolean isAdminOrStaff = currentUser.getRoles().stream()
                .anyMatch(r -> r.getName().name().equals("STAFF")
                        || r.getName().name().equals("MANAGER")
                        || r.getName().name().equals("SUPER_ADMIN"));

        if (isAdminOrStaff) return doc;

        // Citizen chỉ download HS của mình
        Long citizenId = citizenRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new BusinessException("Không có quyền truy cập file này"))
                .getId();

        if (!doc.getApplication().getCitizen().getId().equals(citizenId)) {
            throw new BusinessException("Không có quyền truy cập file này");
        }

        return doc;
    }

    // ─── Private helpers ──────────────────────────────────────────────────

    private List<ApplicationDocumentResponse> toResponsesWithUrl(List<ApplicationDocument> docs) {
        return docs.stream().map(doc -> {
            ApplicationDocumentResponse r = documentMapper.toResponse(doc);
            // /files/** dùng MVC filter chain (session-based) thay vì /api/files/** (JWT-only)
            r.setDownloadUrl("/files/" + doc.getFilePath());
            return r;
        }).toList();
    }

    private String extractExtension(String filename) {
        if (filename == null) return "bin";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "bin";
    }

    /** Kiểm tra user có role cụ thể không. */
    private boolean hasRole(User user, String roleName) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().name().equals(roleName));
    }
}

