package com.psms.repository;

import com.psms.entity.Staff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long>, JpaSpecificationExecutor<Staff> {

    /**
     * Override JpaSpecificationExecutor.findAll để eager-fetch user + department trong 1 query.
     * Tránh N+1 lazy load khi pagination trong AdminStaffService.findAll().
     */
    @Override
    @EntityGraph(attributePaths = {"user", "department"})
    Page<Staff> findAll(Specification<Staff> spec, Pageable pageable);

    /**
     * Batch count staff grouped by departmentId.
     */
    @Query("SELECT s.department.id, COUNT(s) FROM Staff s WHERE s.department.id IN :departmentIds GROUP BY s.department.id")
    List<Object[]> countByDepartmentIdIn(@Param("departmentIds") Collection<Long> departmentIds);

    Optional<Staff> findByUserId(Long userId);

    Optional<Staff> findByStaffCode(String staffCode);

    boolean existsByStaffCode(String staffCode);

    /**
     * Load danh sach can bo theo phong ban + available
     */
    @EntityGraph(attributePaths = "user")
    List<Staff> findAllByDepartmentIdAndIsAvailableTrue(Long departmentId);

    /**
     * Tìm Staff kèm Department trong 1 query — dùng trong AdminUserService.mapToResponse()
     * để tránh lazy-load Department riêng (giảm số query khi render user list).
     */
    @EntityGraph(attributePaths = "department")
    Optional<Staff> findWithDepartmentByUserId(Long userId);

    /**
     * Batch-fetch staff profiles kèm department theo danh sách userId.
     */
    @EntityGraph(attributePaths = "department")
    List<Staff> findWithDepartmentByUserIdIn(Collection<Long> userIds);

    /** Đếm số cán bộ trong phòng ban — dùng để chặn xóa phòng ban */
    long countByDepartmentId(Long departmentId);

    /** Danh sách cán bộ theo phòng ban, kèm user+department*/
    @EntityGraph(attributePaths = {"user", "department"})
    List<Staff> findAllByDepartmentId(Long departmentId);

    /** Tìm Staff kèm user+department theo ID */
    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Staff> findWithDetailsById(Long id);
}

