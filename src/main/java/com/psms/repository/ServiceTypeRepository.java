package com.psms.repository;

import com.psms.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long>, JpaSpecificationExecutor<ServiceType> {

    Optional<ServiceType> findByCode(String code);

    Optional<ServiceType> findByIdAndIsActiveTrue(Long id);

    boolean existsByCode(String code);
}

