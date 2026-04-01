package com.psms.repository;

import com.psms.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long>, JpaSpecificationExecutor<Staff> {

    Optional<Staff> findByUserId(Long userId);

    Optional<Staff> findByStaffCode(String staffCode);

    List<Staff> findAllByDepartmentIdAndIsAvailableTrue(Long departmentId);
}

