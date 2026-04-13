package com.psms.repository;

import com.psms.entity.Citizen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CitizenRepository extends JpaRepository<Citizen, Long>, JpaSpecificationExecutor<Citizen> {

    Optional<Citizen> findByUserId(Long userId);

    boolean existsByNationalId(String nationalId);

    boolean existsByUserId(Long userId);

    /**
     * Batch-fetch citizen profiles theo danh sách userId.
     * Dùng trong findAll() pagination để tránh N+1.
     * 1 query duy nhất thay vì N query.
     */
    List<Citizen> findByUserIdIn(Collection<Long> userIds);
}

