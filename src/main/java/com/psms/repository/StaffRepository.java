package com.psms.repository;

import com.psms.entity.Staff;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long>, JpaSpecificationExecutor<Staff> {

    Optional<Staff> findByUserId(Long userId);

    Optional<Staff> findByStaffCode(String staffCode);

    /**
     * Load danh sach can bo theo phong ban + available,
     * eager-fetch user de Thymeleaf co the render s.user.fullName
     * ma khong bi LazyInitializationException (OSIV = false).
     */
    @EntityGraph(attributePaths = "user")
    List<Staff> findAllByDepartmentIdAndIsAvailableTrue(Long departmentId);
}

