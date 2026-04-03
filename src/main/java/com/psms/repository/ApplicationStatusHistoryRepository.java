package com.psms.repository;

import com.psms.entity.ApplicationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository cho ApplicationStatusHistory.
 * Không cần JpaSpecificationExecutor vì chỉ dùng simple query theo applicationId.
 */
public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {

    List<ApplicationStatusHistory> findByApplicationIdOrderByChangedAtAsc(Long applicationId);
}
