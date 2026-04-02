package com.psms.util;

import com.psms.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * Sinh và xác thực JWT access token.
 *
 * <p><b>Access token structure:</b>
 * <pre>
 *   Header: { alg: HS256 }
 *   Payload:
 *     sub   → email (username)
 *     jti   → UUID — dùng để revoke khi logout (blacklist)
 *     iat   → issued at
 *     exp   → expiration (now + jwt.expiration ms)
 * </pre>
 *
 * <p><b>Refresh token:</b> JWT đơn giản, không chứa roles, không có jti.
 * Token rotation được xử lý ở {@link com.psms.service.RefreshTokenService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    // ----------------------------------------------------------------
    // Signing key — lazy-init, thread-safe với volatile + double-check
    // ----------------------------------------------------------------

    private volatile SecretKey signingKey;

    /**
     * Lấy signing key từ {@code jwt.secret}.
     * Dùng HMAC-SHA256 — key phải ≥ 256 bits (32 bytes).
     */
    private SecretKey getSigningKey() {
        if (signingKey == null) {
            synchronized (this) {
                if (signingKey == null) {
                    byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
                    signingKey = Keys.hmacShaKeyFor(keyBytes);
                }
            }
        }
        return signingKey;
    }

    // ----------------------------------------------------------------
    // Generate
    // ----------------------------------------------------------------

    /**
     * Sinh access token JWT với {@code jti} claim để hỗ trợ revoke khi logout.
     *
     * @param userDetails user đã xác thực
     * @return signed JWT string
     */
    public String generateAccessToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())   // jti — dùng để blacklist khi logout
                .subject(userDetails.getUsername()) // sub = email
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Sinh refresh token JWT — có {@code jti} UUID để đảm bảo unique ngay cả khi gọi
     * trong cùng millisecond. Token string được lưu vào DB ({@code refresh_tokens}).
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())   // đảm bảo unique — quan trọng cho token rotation
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    // ----------------------------------------------------------------
    // Extract
    // ----------------------------------------------------------------

    /** Lấy email (subject) từ token — dùng để load UserDetails */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** Lấy jti claim — dùng để insert vào revoked_access_tokens khi logout */
    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    /** Lấy expiration dưới dạng {@link LocalDateTime} — dùng khi insert vào blacklist */
    public LocalDateTime extractExpiration(String token) {
        Date expiry = parseClaims(token).getExpiration();
        return expiry.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    // ----------------------------------------------------------------
    // Validate
    // ----------------------------------------------------------------

    /**
     * Kiểm tra token hợp lệ về mặt signature và chưa hết hạn.
     * Không kiểm tra blacklist — việc đó thuộc trách nhiệm của
     * {@link com.psms.service.RevokedTokenService}.
     *
     * @return true nếu hợp lệ
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token); // throws nếu expired hoặc invalid signature
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ----------------------------------------------------------------
    // Internal
    // ----------------------------------------------------------------

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

