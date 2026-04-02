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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
     * @param tokenString refresh token string từ HttpOnly cookie
     * @return entity {@link RefreshToken}
     * @throws BusinessException nếu token không tồn tại hoặc đã hết hạn
     */
    @Transactional(readOnly = true)
    public RefreshToken validate(String tokenString) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new BusinessException("Refresh token không hợp lệ hoặc đã hết hạn"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            // Token hết hạn nhưng chưa bị cleanup — xóa luôn
            refreshTokenRepository.delete(token);
            throw new BusinessException("Refresh token đã hết hạn, vui lòng đăng nhập lại");
        }

        return token;
    }

    /**
     * Token Rotation: xóa token cũ → sinh cặp token mới.
     *
     * <p>Nếu token cũ không tồn tại trong DB → Reuse Detection:
     * xóa toàn bộ session của user và ném exception.
     *
     * @param oldTokenString refresh token cũ từ client
     * @return cặp token mới {@link RotationResult}
     */
    @Transactional
    public RotationResult rotate(String oldTokenString) {
        RefreshToken oldToken = refreshTokenRepository.findByToken(oldTokenString)
                .orElse(null);

        if (oldToken == null) {
            // Reuse Detection: token đã dùng rồi — có thể bị đánh cắp
            // Tìm user từ JWT claim để revoke toàn bộ session
            try {
                String username = jwtTokenProvider.extractUsername(oldTokenString);
                userRepository.findByEmail(username).ifPresent(user -> {
                    revokeAllByUser(user.getId());
                    log.warn("Refresh token reuse detected for user={} — all sessions revoked", username);
                });
            } catch (Exception e) {
                log.warn("Refresh token reuse detected — cannot extract user, ignoring: {}", e.getMessage());
            }
            throw new BusinessException("Refresh token không hợp lệ. Toàn bộ phiên đăng nhập đã bị thu hồi");
        }

        // Xóa token cũ
        refreshTokenRepository.delete(oldToken);

        // Load user và sinh token mới
        User user = userRepository.findById(oldToken.getUserId())
                .orElseThrow(() -> new BusinessException("User không tồn tại"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        RefreshToken newRefreshToken = create(user);

        return new RotationResult(newAccessToken, newRefreshToken.getToken());
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

    /** Kết quả của Token Rotation — trả về access + refresh token mới */
    public record RotationResult(String accessToken, String refreshToken) {}
}

