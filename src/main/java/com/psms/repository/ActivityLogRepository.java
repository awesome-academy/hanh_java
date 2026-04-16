package com.psms.repository;

import com.psms.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ActivityLogRepository
        extends JpaRepository<ActivityLog, Long>, JpaSpecificationExecutor<ActivityLog> {

    /**
     * Logs của một user cụ thể (dùng cho admin view filter by user).
     * JOIN FETCH user để tránh N+1 khi cần hiển thị tên user trong list.
     */
    @Query("SELECT l FROM ActivityLog l LEFT JOIN FETCH l.user WHERE l.user.id = :userId")
    Page<ActivityLog> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Xóa logs cũ hơn cutoff — dùng cho purge job.
     * Bulk DELETE qua JPQL, không load entity vào memory → O(N) trực tiếp trên DB.
     */
    @Modifying
    @Query("DELETE FROM ActivityLog l WHERE l.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Paged query with Specification, always fetches user relation to avoid N+1.
     */
    @Override
    @EntityGraph(attributePaths = "user")
    Page<ActivityLog> findAll(Specification<ActivityLog> spec, Pageable pageable);
}
