package com.psms.repository;

import com.psms.entity.ApplicationDocument;
import com.psms.enums.ValidationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

    /**
     * Tài liệu citizen nộp (is_response=false) hoặc phản hồi cán bộ (is_response=true),
     * chỉ lấy chưa bị xóa.
     */
    @Query("SELECT d FROM ApplicationDocument d WHERE d.application.id = :applicationId AND d.isResponse = :isResponse AND d.isDeleted = false ORDER BY d.uploadedAt ASC")
    List<ApplicationDocument> findByApplicationAndResponse(
            @Param("applicationId") Long applicationId,
            @Param("isResponse") boolean isResponse);

    /** Tìm file theo path để kiểm tra ownership khi download (chỉ file chưa xóa). */
    Optional<ApplicationDocument> findByFilePathAndIsDeletedFalse(String filePath);

    /** Tìm document theo id để thực hiện soft delete. */
    Optional<ApplicationDocument> findByIdAndIsDeletedFalse(Long id);

}
