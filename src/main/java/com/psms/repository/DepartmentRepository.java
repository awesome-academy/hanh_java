package com.psms.repository;

import com.psms.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long>, JpaSpecificationExecutor<Department> {

    Optional<Department> findByCode(String code);

    boolean existsByCode(String code);

    List<Department> findAllByIsActiveTrueOrderByNameAsc();
}

