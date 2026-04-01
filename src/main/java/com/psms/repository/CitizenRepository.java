package com.psms.repository;

import com.psms.entity.Citizen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CitizenRepository extends JpaRepository<Citizen, Long>, JpaSpecificationExecutor<Citizen> {

    Optional<Citizen> findByUserId(Long userId);

    boolean existsByNationalId(String nationalId);

    boolean existsByUserId(Long userId);
}

