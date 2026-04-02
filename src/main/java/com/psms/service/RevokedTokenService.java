package com.psms.service;

import com.psms.entity.RevokedAccessToken;
import com.psms.repository.RevokedAccessTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Quản lý blacklist JTI của access token sau khi logout.
 *
 * <p><b>Tại sao cần service này?</b><br>
 * JWT là stateless — server không thể "hủy" một token đang còn hạn.
 * Giải pháp: lưu JTI vào DB. {@code JwtAuthenticationFilter}
 * kiểm tra blacklist trước khi cho request đi qua.
 *
 * <p>Bảng tự dọn dẹp: cleanup scheduler xóa JTI hết hạn mỗi giờ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevokedTokenService {

    private final RevokedAccessTokenRepository revokedTokenRepository;

    /**
     * Thêm JTI vào blacklist.
     *
     * @param jti       JWT ID claim từ access token
     * @param expiresAt thời điểm token hết hạn — dùng cho cleanup scheduler
     */
    @Transactional
    public void revoke(String jti, LocalDateTime expiresAt) {
        RevokedAccessToken revoked = RevokedAccessToken.builder()
                .jti(jti)
                .expiresAt(expiresAt)
                .build();
        revokedTokenRepository.save(revoked);
        log.debug("Revoked access token jti={}", jti);
    }

    /**
     * Kiểm tra JTI có trong blacklist không.
     * Được gọi bởi {@code JwtAuthenticationFilter} trên mỗi request.
     *
     * @param jti JWT ID claim
     * @return true nếu token đã bị revoke
     */
    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        return revokedTokenRepository.existsByJti(jti);
    }
}

