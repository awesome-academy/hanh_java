package com.psms.repository;

import com.psms.entity.Application;
import com.psms.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    Optional<Application> findByApplicationCode(String applicationCode);

    // Lấy application_code lớn nhất trong ngày để sinh số thứ tự tiếp theo
    // Ví dụ prefix = "HS-20260401-" → tìm "HS-20260401-00099" nếu đã có 99 hồ sơ trong ngày
    @Query("SELECT a FROM Application a WHERE a.applicationCode LIKE :prefix% ORDER BY a.applicationCode DESC LIMIT 1")
    Optional<Application> findLatestByCodePrefix(@Param("prefix") String prefix);

    Optional<Application> findByIdAndCitizenId(Long id, Long citizenId);

    boolean existsByApplicationCode(String applicationCode);

    boolean existsByIdAndCitizenId(Long id, Long citizenId);

    long countByAssignedStaffIdAndStatusIn(Long assignedStaffId, Collection<ApplicationStatus> statuses);

    long countByCitizenIdAndSubmittedAtBetween(Long citizenId, LocalDateTime from, LocalDateTime to);
}

