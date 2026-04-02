package com.psms.scheduler;

import com.psms.repository.RefreshTokenRepository;
import com.psms.repository.RevokedAccessTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Cleanup scheduler — chạy mỗi 1 giờ xóa token đã hết hạn khỏi DB.
 *
 * <p>Đảm bảo 2 bảng không phình to theo thời gian:
 * <ul>
 *   <li>{@code refresh_tokens} — token 7 ngày: sau 7 ngày tự bị xóa</li>
 *   <li>{@code revoked_access_tokens} — JTI 1 giờ: sau 1 giờ không cần giữ nữa</li>
 * </ul>
 */
@EnableScheduling
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedAccessTokenRepository revokedTokenRepository;

    /**
     * Chạy mỗi 1 giờ (3600000 ms) — xóa token đã hết hạn.
     * fixedDelay: chờ task trước xong mới đếm delay → tránh overlap.
     */
    @Scheduled(fixedDelay = 3_600_000L)
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();

        int deletedRefresh = refreshTokenRepository.deleteAllExpiredBefore(now);
        int deletedRevoked = revokedTokenRepository.deleteAllExpiredBefore(now);

        if (deletedRefresh > 0 || deletedRevoked > 0) {
            log.info("Token cleanup: deleted {} refresh tokens, {} revoked access tokens",
                    deletedRefresh, deletedRevoked);
        }
    }
}

