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

    /** Đếm hồ sơ đang xử lý theo dịch vụ — dùng để chặn xóa dịch vụ */
    long countByServiceTypeIdAndStatusIn(Long serviceTypeId, Collection<ApplicationStatus> statuses);

    /**
     * Batch count active applications grouped by serviceTypeId.
     */
    @Query("""
        SELECT a.serviceType.id, COUNT(a) FROM Application a
        WHERE a.serviceType.id IN :serviceTypeIds AND a.status IN :statuses
        GROUP BY a.serviceType.id
        """)
    List<Object[]> countActiveByServiceTypeIdIn(
            @Param("serviceTypeIds") Collection<Long> serviceTypeIds,
            @Param("statuses") Collection<ApplicationStatus> statuses);

    /**
     * Batch count active applications grouped by assignedStaff (User) ID.
     */
    @Query("""
        SELECT a.assignedStaff.id, COUNT(a) FROM Application a
        WHERE a.assignedStaff.id IN :userIds AND a.status IN :statuses
        GROUP BY a.assignedStaff.id
        """)
    List<Object[]> countActiveByAssignedStaffIdIn(
            @Param("userIds") Collection<Long> userIds,
            @Param("statuses") Collection<ApplicationStatus> statuses);

    long countByCitizenIdAndSubmittedAtBetween(Long citizenId, LocalDateTime from, LocalDateTime to);

    long countByStatusIn(Collection<ApplicationStatus> statuses);

    /**
     * Đếm hồ sơ quá hạn: chưa hoàn thành và đã qua processingDeadline.
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

    /**
     * Phân bố hồ sơ theo lĩnh vực dịch vụ (dashboard bar chart).
     * Trả về List<Object[]>: [categoryName, count]
     */
    @Query("""
        SELECT a.serviceType.category.name, COUNT(a)
        FROM Application a
        GROUP BY a.serviceType.category.name
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> countGroupByCategory();

    /**
     * Phân bố hồ sơ theo trạng thái (dashboard donut chart).
     * Trả về List<Object[]>: [status, count]
     */
    @Query("""
        SELECT a.status, COUNT(a)
        FROM Application a
        GROUP BY a.status
        """)
    List<Object[]> countGroupByStatus();

    /**
     * Đếm tổng hồ sơ theo từng citizen — dùng cho CSV export công dân.
     * row[0] = citizenId (Long), row[1] = count (Long)
     */
    @Query("SELECT a.citizen.id, COUNT(a) FROM Application a GROUP BY a.citizen.id")
    List<Object[]> countAllGroupByCitizenId();

    /**
     * Fetch toàn bộ applications kèm associations cần thiết cho CSV export.
     * JOIN FETCH tránh N+1 khi truy cập các trường lồng nhau.
     */
    @Query("""
        SELECT a FROM Application a
        JOIN FETCH a.citizen c
        JOIN FETCH c.user cu
        JOIN FETCH a.serviceType st
        LEFT JOIN FETCH a.assignedStaff
        ORDER BY a.submittedAt DESC
        """)
    List<Application> findAllForExport();
}
