package com.psms.repository;

import com.psms.entity.Application;
import com.psms.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    Optional<Application> findByApplicationCode(String applicationCode);

    Optional<Application> findTopByApplicationCodeStartingWithOrderByApplicationCodeDesc(String prefix);

    Optional<Application> findByIdAndCitizenId(Long id, Long citizenId);

    boolean existsByApplicationCode(String applicationCode);

    boolean existsByIdAndCitizenId(Long id, Long citizenId);

    long countByAssignedStaffIdAndStatusIn(Long assignedStaffId, Collection<ApplicationStatus> statuses);

    long countByCitizenIdAndSubmittedAtBetween(Long citizenId, LocalDateTime from, LocalDateTime to);
}

