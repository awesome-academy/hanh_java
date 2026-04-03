package com.psms.service;

import com.psms.config.JwtProperties;
import com.psms.entity.RefreshToken;
import com.psms.entity.User;
import com.psms.exception.BusinessException;
import com.psms.repository.RefreshTokenRepository;
import com.psms.repository.UserRepository;
import com.psms.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Quản lý refresh token trong DB — hỗ trợ Token Rotation và Reuse Detection.
 *
 * <p><b>Token Rotation flow:</b>
 * <pre>
 *   Client gọi /api/auth/refresh-token với refresh token cũ
 *     → validate(oldToken): kiểm tra DB + chưa hết hạn
 *     → rotate(oldToken): DELETE old → INSERT new access + refresh token
 *     → trả về token mới
 *
 *   Nếu oldToken không tồn tại trong DB (đã dùng rồi = reuse):
 *     → revokeAllByUser(userId): xóa toàn bộ session của user
 *     → trả về 401 — buộc user đăng nhập lại
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    /**
     * Tạo refresh token mới và lưu vào DB sau khi login thành công.
     *
     * @param user user vừa đăng nhập
     * @return entity {@link RefreshToken} đã được persist
     */
    @Transactional
    public RefreshToken create(User user) {
        String tokenString = jwtTokenProvider.generateRefreshToken(user);
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtProperties.getRefreshExpiration() / 1000);

        RefreshToken token = RefreshToken.builder()
                .userId(user.getId())
                .token(tokenString)
                .expiresAt(expiresAt)
                .build();

        return refreshTokenRepository.save(token);
    }

    /**
     * Validate refresh token: tồn tại trong DB và chưa hết hạn.
     *
     * <p><b>noRollbackFor:</b> Khi token hết hạn, {@link #assertNotExpired} DELETE khỏi DB
     * rồi throw. Nếu không có {@code noRollbackFor}, Spring rollback toàn bộ tx →
     * delete bị huỷ → token hết hạn vẫn còn trong DB mãi mãi.
     *
     * @param tokenString refresh token string từ HttpOnly cookie
     * @return entity {@link RefreshToken} đã xác thực còn hiệu lực
     * @throws AuthenticationCredentialsNotFoundException nếu token không tồn tại hoặc đã hết hạn (→ HTTP 401)
     */
    @Transactional(noRollbackFor = AuthenticationCredentialsNotFoundException.class)
    public RefreshToken validate(String tokenString) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Refresh token không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại"));
        // delete nếu expired + throw → commit nhờ noRollbackFor
        assertNotExpired(token);
        return token;
    }

    /**
     * Token Rotation: xóa token cũ → sinh cặp token mới.
     *
     * <p>Phân biệt 3 trường hợp:
     * <ol>
     *   <li>Token không tồn tại trong DB → Reuse Detection: revoke all sessions → 401</li>
     *   <li>Token tồn tại nhưng hết hạn  → eager cleanup → 401</li>
     *   <li>Token hợp lệ                 → DELETE old, INSERT new → trả token mới</li>
     * </ol>
     *
     * <p><b>noRollbackFor — tại sao bắt buộc:</b><br>
     * Cả 2 path lỗi đều cần ghi DB trước khi throw:
     * <ul>
     *   <li>Reuse: {@code revokeAllByUser()} join outer tx → nếu rollback → sessions KHÔNG bị thu hồi
     *       → attacker vẫn dùng được token cũ. Đây là security bug.</li>
     *   <li>Expired: {@code delete(oldToken)} join outer tx → nếu rollback → token hết hạn vẫn còn DB.</li>
     * </ul>
     * {@code noRollbackFor} đảm bảo tx commit ngay cả khi exception được throw.
     * Các exception khác (DB error, NPE...) vẫn rollback bình thường.
     *
     * @param oldTokenString refresh token cũ từ client
     * @return cặp token mới {@link RotationResult}
     */
    @Transactional(noRollbackFor = AuthenticationCredentialsNotFoundException.class)
    public RotationResult rotate(String oldTokenString) {
        RefreshToken oldToken = refreshTokenRepository.findByToken(oldTokenString)
                .orElse(null);

        if (oldToken == null) {
            // Reuse Detection: token đã dùng rồi — có thể bị đánh cắp.
            // revokeAllByUser() join outer tx → nhờ noRollbackFor, commit được đảm bảo.
            try {
                String username = jwtTokenProvider.extractUsername(oldTokenString);
                userRepository.findByEmail(username).ifPresent(user -> {
                    revokeAllByUser(user.getId());
                    log.warn("Refresh token reuse detected for user={} — all sessions revoked", username);
                });
            } catch (Exception e) {
                log.warn("Refresh token reuse detected — cannot extract user, ignoring: {}", e.getMessage());
            }
            throw new BusinessException(
                    "Refresh token đã bị dùng lại (reuse). Toàn bộ phiên đăng nhập đã bị thu hồi");
        }

        // delete nếu expired + throw → commit nhờ noRollbackFor
        assertNotExpired(oldToken);

        // Token hợp lệ — tiến hành rotation
        refreshTokenRepository.delete(oldToken);

        User user = userRepository.findById(oldToken.getUserId())
                .orElseThrow(() -> new BusinessException("User không tồn tại"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        RefreshToken newRefreshToken = create(user);

        return new RotationResult(newAccessToken, newRefreshToken.getToken());
    }

    /**
     * Tìm {@code userId} từ refresh token trong DB — KHÔNG parse JWT claim.
     *
     * <p>Dùng trong logout(): nếu token còn trong DB thì lấy userId từ record
     * và revoke toàn bộ session, bất kể token đã expired hay malformed về mặt JWT.
     * Nếu token không tồn tại trong DB (đã bị revoke trước đó) → trả về empty, bỏ qua.
     *
     * @param tokenString refresh token string từ HttpOnly cookie
     * @return {@link java.util.Optional} chứa userId nếu tìm thấy trong DB
     */
    @Transactional(readOnly = true)
    public Optional<Long> findUserIdByToken(String tokenString) {
        return refreshTokenRepository.findByToken(tokenString)
                .map(RefreshToken::getUserId);
    }

    /**
     * Xóa toàn bộ refresh token của 1 user (logout hoặc reuse detection).
     *
     * @param userId user ID
     */
    @Transactional
    public void revokeAllByUser(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
        log.debug("All refresh tokens revoked for userId={}", userId);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /**
     * Kiểm tra token chưa hết hạn. Nếu hết hạn: DELETE khỏi DB (eager cleanup) và throw 401.
     *
     * <p>Dùng chung bởi {@link #validate} và {@link #rotate} để tránh duplicate logic.
     * Chạy trong transaction của caller — caller phải có {@code noRollbackFor} để
     * đảm bảo delete được commit khi exception được throw.
     */
    private void assertNotExpired(RefreshToken token) {
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            log.debug("Expired refresh token cleaned up for userId={}", token.getUserId());
            throw new AuthenticationCredentialsNotFoundException(
                    "Refresh token đã hết hạn. Vui lòng đăng nhập lại");
        }
    }

    // ----------------------------------------------------------------

    /** Kết quả của Token Rotation — trả về access + refresh token mới */
    public record RotationResult(String accessToken, String refreshToken) {}
}

