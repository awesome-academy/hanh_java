package com.psms.repository;

import com.psms.entity.Application;
import com.psms.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    Optional<Application> findByApplicationCode(String applicationCode);

    /**
     * Lấy application_code lớn nhất theo prefix để sinh số thứ tự tiếp theo.
     * Ví dụ prefix = "HS-20260401-" → tìm "HS-20260401-00099" nếu đã có 99 hồ sơ.
     */
    @Query("""
        SELECT a FROM Application a
        WHERE a.applicationCode LIKE CONCAT(:prefix, '%')
        ORDER BY a.applicationCode DESC
        """)
    List<Application> findLatestByApplicationCodePrefix(@Param("prefix") String prefix, Pageable pageable);

    Optional<Application> findByIdAndCitizenId(Long id, Long citizenId);

    boolean existsByApplicationCode(String applicationCode);

    boolean existsByIdAndCitizenId(Long id, Long citizenId);

    long countByAssignedStaffIdAndStatusIn(Long assignedStaffId, Collection<ApplicationStatus> statuses);

    long countByCitizenIdAndSubmittedAtBetween(Long citizenId, LocalDateTime from, LocalDateTime to);

    long countByStatusIn(Collection<ApplicationStatus> statuses);

    /**
     * Đếm hồ sơ quá hạn: chưa hoàn thành và đã qua processingDeadline.
     * Dùng tham số enum thay vì string literal để tránh silent bug khi đổi tên enum.
     */
    @Query("""
        SELECT COUNT(a) FROM Application a
        WHERE a.status NOT IN :completedStatuses
          AND a.processingDeadline < :today
        """)
    long countOverdue(@Param("completedStatuses") Collection<ApplicationStatus> completedStatuses,
                      @Param("today") LocalDate today);

    /**
     * Lấy danh sách hồ sơ pending mới nhất (dashboard).
     * Dùng tham số enum thay vì string literal.
     */
    @Query("""
        SELECT a FROM Application a
        WHERE a.status IN :pendingStatuses
        ORDER BY a.submittedAt DESC
        """)
    List<Application> findRecentPending(@Param("pendingStatuses") Collection<ApplicationStatus> pendingStatuses,
                                        Pageable pageable);

    /**
     * Danh sách hồ sơ của citizen, filter status tuỳ chọn, phân trang.
     * countQuery tường minh để Spring Data không tự generate (và loại bỏ ORDER BY issue).
     * Sort được xử lý hoàn toàn bởi Pageable.
     */
    @Query(value = """
        SELECT a FROM Application a
        WHERE a.citizen.id = :citizenId
          AND (:status IS NULL OR a.status = :status)
        """,
        countQuery = """
        SELECT COUNT(a) FROM Application a
        WHERE a.citizen.id = :citizenId
          AND (:status IS NULL OR a.status = :status)
        """)
    Page<Application> findByCitizenIdWithStatus(
            @Param("citizenId") Long citizenId,
            @Param("status") ApplicationStatus status,
            Pageable pageable);
}
