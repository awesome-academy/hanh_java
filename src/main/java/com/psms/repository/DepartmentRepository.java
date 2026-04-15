package com.psms.repository;

import com.psms.entity.Department;
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

public interface DepartmentRepository extends JpaRepository<Department, Long>, JpaSpecificationExecutor<Department> {

    /**
     * Override JpaSpecificationExecutor.findAll để eager-fetch leader (User) trong 1 query.
     * Tránh N+1 lazy load khi pagination trong AdminDepartmentService.findAll().
     */
    @Override
    @EntityGraph(attributePaths = "leader")
    Page<Department> findAll(Specification<Department> spec, Pageable pageable);

    /**
     * Batch count services grouped by departmentId.
     * row[0]=departmentId (Long), row[1]=count (Long).
     * 1 query thay cho N queries trong AdminDepartmentService.findAll().
     */
    @Query("SELECT s.department.id, COUNT(s) FROM ServiceType s WHERE s.department.id IN :departmentIds GROUP BY s.department.id")
    List<Object[]> countServicesByDepartmentIdIn(@Param("departmentIds") Collection<Long> departmentIds);

    Optional<Department> findByCode(String code);

    boolean existsByCode(String code);

    List<Department> findAllByIsActiveTrueOrderByNameAsc();

    List<Department> findAllByOrderByNameAsc();

    /** Đếm số dịch vụ thuộc phòng ban (dùng trong AdminDepartmentResponse) */
    @Query("SELECT COUNT(s) FROM ServiceType s WHERE s.department.id = :deptId")
    long countServicesByDepartmentId(@Param("deptId") Long deptId);
}

