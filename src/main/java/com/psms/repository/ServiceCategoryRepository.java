package com.psms.repository;

import com.psms.entity.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Integer>, JpaSpecificationExecutor<ServiceCategory> {

    Optional<ServiceCategory> findByCode(String code);

    List<ServiceCategory> findAllByIsActiveTrueOrderBySortOrderAsc();
}

