package com.psms.config;

import com.psms.entity.RefreshToken;
import com.psms.entity.RevokedAccessToken;
import com.psms.repository.RefreshTokenRepository;
import com.psms.repository.RevokedAccessTokenRepository;
import com.psms.repository.UserRepository;
import com.psms.scheduler.TokenCleanupScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cho {@link TokenCleanupScheduler} đảm bảo logic xóa token đã hết hạn hoạt động đúng.
 *
 * <p>Không chờ scheduler tự chạy — gọi trực tiếp {@code cleanupExpiredTokens()}
 * để kiểm tra logic xóa token đã hết hạn.
 *
 * <p>Dùng {@code @SpringBootTest} (không phải {@code @DataJpaTest}) vì cần
 * full context để khởi tạo {@link TokenCleanupScheduler} bean.
 */
@SpringBootTest
@ActiveProfiles("test")
class TokenCleanupTest {

    @Autowired private TokenCleanupScheduler tokenCleanupScheduler;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RevokedAccessTokenRepository revokedTokenRepository;

    @BeforeEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        revokedTokenRepository.deleteAll();
    }

    // ----------------------------------------------------------------
    // #03-23: cleanup expired refresh tokens
    // ----------------------------------------------------------------

    @Test
    @DisplayName("#03-23a: Cleanup xóa refresh token đã hết hạn, giữ token còn hạn")
    @Transactional
    void cleanup_expiredRefreshTokens_deletedOnly() {
        LocalDateTime now = LocalDateTime.now();

        // Tạo 1 token đã hết hạn (expires_at ở quá khứ)
        RefreshToken expired = RefreshToken.builder()
                .userId(999L) // userId không cần tồn tại thật cho test cleanup logic
                .token("expired-refresh-token-123")
                .expiresAt(now.minusHours(2)) // hết hạn 2h trước
                .build();

        // Tạo 1 token còn hạn (expires_at ở tương lai)
        RefreshToken valid = RefreshToken.builder()
                .userId(999L)
                .token("valid-refresh-token-456")
                .expiresAt(now.plusDays(7)) // còn 7 ngày
                .build();

        refreshTokenRepository.save(expired);
        refreshTokenRepository.save(valid);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);

        // Chạy cleanup
        tokenCleanupScheduler.cleanupExpiredTokens();

        // Chỉ token hết hạn bị xóa
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findByToken("valid-refresh-token-456")).isPresent();
        assertThat(refreshTokenRepository.findByToken("expired-refresh-token-123")).isEmpty();
    }

    @Test
    @DisplayName("#03-23b: Cleanup xóa revoked access token (JTI) đã hết hạn, giữ JTI còn hạn")
    @Transactional
    void cleanup_expiredRevokedTokens_deletedOnly() {
        LocalDateTime now = LocalDateTime.now();

        // JTI của token đã hết hạn → không cần giữ nữa
        RevokedAccessToken expiredJti = RevokedAccessToken.builder()
                .jti("expired-jti-uuid-001")
                .expiresAt(now.minusMinutes(30)) // hết hạn 30' trước
                .build();

        // JTI của token chưa hết hạn → vẫn cần giữ để chặn nếu ai dùng lại
        RevokedAccessToken validJti = RevokedAccessToken.builder()
                .jti("valid-jti-uuid-002")
                .expiresAt(now.plusMinutes(30)) // còn 30' nữa
                .build();

        revokedTokenRepository.save(expiredJti);
        revokedTokenRepository.save(validJti);
        assertThat(revokedTokenRepository.count()).isEqualTo(2);

        // Chạy cleanup
        tokenCleanupScheduler.cleanupExpiredTokens();

        // Chỉ JTI hết hạn bị xóa
        assertThat(revokedTokenRepository.count()).isEqualTo(1);
        assertThat(revokedTokenRepository.existsByJti("valid-jti-uuid-002")).isTrue();
        assertThat(revokedTokenRepository.existsByJti("expired-jti-uuid-001")).isFalse();
    }

    @Test
    @DisplayName("#03-23c: Cleanup với DB rỗng → không throw exception")
    void cleanup_emptyDatabase_noException() {
        assertThat(refreshTokenRepository.count()).isZero();
        assertThat(revokedTokenRepository.count()).isZero();

        // Không được throw bất kỳ exception nào
        tokenCleanupScheduler.cleanupExpiredTokens();

        assertThat(refreshTokenRepository.count()).isZero();
        assertThat(revokedTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("#03-23d: Cleanup chạy nhiều lần liên tiếp → idempotent")
    void cleanup_runMultipleTimes_idempotent() {
        LocalDateTime now = LocalDateTime.now();

        RevokedAccessToken expiredJti = RevokedAccessToken.builder()
                .jti("idempotent-jti-003")
                .expiresAt(now.minusHours(1))
                .build();
        revokedTokenRepository.save(expiredJti);

        // Chạy 3 lần liên tiếp
        tokenCleanupScheduler.cleanupExpiredTokens();
        tokenCleanupScheduler.cleanupExpiredTokens();
        tokenCleanupScheduler.cleanupExpiredTokens();

        // Kết quả giống nhau — không duplicate delete, không exception
        assertThat(revokedTokenRepository.count()).isZero();
    }
}

