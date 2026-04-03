package com.psms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Lưu refresh token hợp lệ — hỗ trợ Token Rotation và multi-device logout.
 *
 * <p><b>Flow:</b>
 * <pre>
 *   Login  → INSERT refresh_token
 *   Refresh→ SELECT by token → validate → DELETE old → INSERT new (rotation)
 *   Logout → DELETE by user_id (revoke all sessions)
 * </pre>
 *
 * <p>Token string dài (~200-300 chars nếu dùng JWT), UNIQUE key dùng prefix 255.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → users.id, cascade delete nếu user bị xóa */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** JWT refresh token string (VARCHAR 512, UNIQUE prefix 255) */
    @Column(name = "token", nullable = false, length = 512)
    private String token;

    /** Thời điểm hết hạn — cleanup scheduler xóa khi expires_at < NOW() */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

