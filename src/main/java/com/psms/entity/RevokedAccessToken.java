package com.psms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Blacklist JTI của access token bị revoke trước hạn (sau khi logout).
 *
 * <p><b>Tại sao chỉ lưu JTI thay vì full token?</b><br>
 * JTI (JWT ID) là UUID 36 ký tự — đủ để định danh duy nhất một token.
 * Lưu full token tốn không gian và không cần thiết.
 *
 * <p><b>Kích thước bảng tự giới hạn:</b><br>
 * Cleanup scheduler xóa các JTI có {@code expires_at < NOW()} mỗi giờ.
 * Bảng chỉ tồn tại tối đa 1h * số logout/giờ rows — không phình to theo thời gian.
 */
@Entity
@Table(name = "revoked_access_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevokedAccessToken {

    /** JWT ID claim (UUID format) — PK O(1) lookup khi validate */
    @Id
    @Column(name = "jti", nullable = false, length = 36)
    private String jti;

    /** Khi access token hết hạn thì không cần giữ JTI nữa — cleanup xóa theo field này */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}

