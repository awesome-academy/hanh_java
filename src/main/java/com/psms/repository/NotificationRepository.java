package com.psms.repository;

import com.psms.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Danh sách thông báo của user, filter isRead (null = tất cả), sort DESC. */
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId " +
           "AND (:isRead IS NULL OR n.isRead = :isRead) ORDER BY n.createdAt DESC")
    Page<Notification> findByUser(
            @Param("userId") Long userId,
            @Param("isRead") Boolean isRead,
            Pageable pageable);

    /** Đếm thông báo chưa đọc — dùng cho badge trên topbar. */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.isRead = false")
    long countUnread(@Param("userId") Long userId);

    /** Tìm notification theo id + userId để chống IDOR. */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** Mark toàn bộ unread của 1 user thành đã đọc. */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId);
}

