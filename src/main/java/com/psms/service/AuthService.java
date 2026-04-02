package com.psms.service;

import com.psms.config.JwtProperties;
import com.psms.dto.request.LoginRequest;
import com.psms.dto.request.RegisterRequest;
import com.psms.dto.response.AuthResponse;
import com.psms.entity.Citizen;
import com.psms.entity.RefreshToken;
import com.psms.entity.Role;
import com.psms.entity.User;
import com.psms.enums.RoleName;
import com.psms.exception.BusinessException;
import com.psms.repository.CitizenRepository;
import com.psms.repository.RoleRepository;
import com.psms.repository.UserRepository;
import com.psms.util.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Business logic cho toàn bộ auth flow:
 * register → login → refresh-token → logout.
 *
 * <p><b>Token storage strategy (Thymeleaf SSR):</b>
 * <ul>
 *   <li>Access token lưu trong {@code HttpSession} → Thymeleaf đọc để gọi API nội bộ</li>
 *   <li>Refresh token lưu trong {@code HttpOnly; Secure; SameSite=Strict} cookie → chống XSS</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String SESSION_ACCESS_TOKEN_KEY = "ACCESS_TOKEN";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    private final UserRepository userRepository;
    private final CitizenRepository citizenRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final RevokedTokenService revokedTokenService;
    private final AuthenticationManager authenticationManager;

    // ----------------------------------------------------------------
    // Register
    // ----------------------------------------------------------------

    /**
     * Đăng ký tài khoản citizen mới.
     *
     * <p>Tạo {@link User} + {@link Citizen}, gán role {@code CITIZEN}.
     * Validation: email unique, CCCD unique.
     *
     * @param request dữ liệu đăng ký từ client
     * @throws BusinessException nếu email hoặc CCCD đã tồn tại
     */
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email đã được đăng ký: " + request.getEmail());
        }
        if (citizenRepository.existsByNationalId(request.getNationalId())) {
            throw new BusinessException("Số CCCD/CMND đã được sử dụng");
        }

        Role citizenRole = roleRepository.findByName(RoleName.CITIZEN)
                .orElseThrow(() -> new BusinessException("Không tìm thấy role CITIZEN trong hệ thống"));

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setRoles(Set.of(citizenRole));
        userRepository.save(user);

        Citizen citizen = Citizen.builder()
                .user(user)
                .nationalId(request.getNationalId())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .permanentAddress(request.getPermanentAddress())
                .ward(request.getWard())
                .province(request.getProvince())
                .build();
        citizenRepository.save(citizen);

        log.info("New citizen registered: email={}", request.getEmail());
    }

    // ----------------------------------------------------------------
    // Login
    // ----------------------------------------------------------------

    /**
     * Đăng nhập — dùng chung cho citizen và admin.
     * Caller truyền vào {@code requiredRoles} để phân biệt 2 cổng:
     * - Citizen portal: không giới hạn role
     * - Admin portal: bắt buộc có STAFF, MANAGER, hoặc SUPER_ADMIN
     *
     * <p>Flow:
     * <ol>
     *   <li>Authenticate qua Spring Security (kiểm tra password + account status)</li>
     *   <li>Kiểm tra brute-force (failed_login_count, locked_until)</li>
     *   <li>Kiểm tra role nếu requiredRoles không rỗng</li>
     *   <li>Sinh access + refresh token</li>
     *   <li>Lưu access token vào HttpSession, refresh token vào HttpOnly cookie</li>
     *   <li>Cập nhật last_login_at, reset failed_login_count</li>
     * </ol>
     *
     * @param request       login request
     * @param requiredRoles danh sách role bắt buộc (rỗng = không giới hạn)
     * @param httpResponse  để set HttpOnly cookie
     * @param session       để lưu access token
     * @return auth response (access token + thông tin user)
     */
    @Transactional
    public AuthResponse login(LoginRequest request,
                              List<RoleName> requiredRoles,
                              HttpServletResponse httpResponse,
                              HttpSession session) {
        // Authenticate — ném BadCredentialsException nếu sai (GlobalExceptionHandler xử lý)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findWithRolesByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("Tài khoản không tồn tại"));

        // Kiểm tra role cho admin portal
        if (!requiredRoles.isEmpty()) {
            boolean hasRequiredRole = user.getRoles().stream()
                    .anyMatch(r -> requiredRoles.contains(r.getName()));
            if (!hasRequiredRole) {
                throw new BusinessException("Bạn không có quyền truy cập cổng quản trị");
            }
        }

        // Sinh token
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.create(user);

        // Lưu access token vào session (Thymeleaf SSR)
        session.setAttribute(SESSION_ACCESS_TOKEN_KEY, accessToken);

        // Refresh token → HttpOnly cookie
        setRefreshTokenCookie(httpResponse, refreshToken.getToken(),
                (int) (jwtProperties.getRefreshExpiration() / 1000));

        // Cập nhật last_login_at, reset brute-force counter
        user.setLastLoginAt(LocalDateTime.now());
        user.setFailedLoginCount((byte) 0);
        user.setLockedUntil(null);
        userRepository.save(user);

        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .toList();

        log.info("User logged in: email={}, roles={}", user.getEmail(), roles);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .expiresIn(jwtProperties.getExpiration() / 1000)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .build();
    }

    // ----------------------------------------------------------------
    // Refresh Token
    // ----------------------------------------------------------------

    /**
     * Token Rotation: xóa refresh token cũ, sinh cặp token mới.
     *
     * <p>Refresh token được đọc từ HttpOnly cookie.
     * Nếu cookie không có → 401 (client cần login lại).
     * Nếu token đã bị dùng (reuse) → revoke toàn bộ session.
     *
     * @param httpRequest  để đọc cookie
     * @param httpResponse để set cookie mới
     * @param session      để cập nhật access token trong session
     * @return auth response với access + refresh token mới
     */
    @Transactional
    public AuthResponse refreshToken(HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse,
                                     HttpSession session) {
        String oldRefreshToken = extractRefreshTokenFromCookie(httpRequest);
        if (oldRefreshToken == null) {
            throw new BusinessException("Refresh token không tìm thấy. Vui lòng đăng nhập lại");
        }

        // Token Rotation (bao gồm Reuse Detection bên trong)
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(oldRefreshToken);

        // Cập nhật session + cookie
        session.setAttribute(SESSION_ACCESS_TOKEN_KEY, result.accessToken());
        setRefreshTokenCookie(httpResponse, result.refreshToken(),
                (int) (jwtProperties.getRefreshExpiration() / 1000));

        return AuthResponse.builder()
                .accessToken(result.accessToken())
                .expiresIn(jwtProperties.getExpiration() / 1000)
                .build();
    }

    // ----------------------------------------------------------------
    // Logout
    // ----------------------------------------------------------------

    /**
     * Logout: blacklist access token (jti), xóa refresh token khỏi DB,
     * invalidate session, xóa cookie.
     *
     * @param httpRequest  để đọc cookie + session
     * @param httpResponse để xóa cookie
     */
    @Transactional
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // Ưu tiên: đọc từ session (Thymeleaf SSR) → fallback: Authorization header (REST client)
        String accessToken = null;
        HttpSession session = httpRequest.getSession(false);

        if (session != null) {
            accessToken = (String) session.getAttribute(SESSION_ACCESS_TOKEN_KEY);
            session.invalidate();
        }

        if (accessToken == null) {
            String header = httpRequest.getHeader("Authorization");
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                accessToken = header.substring(7);
            }
        }

        // Blacklist access token nếu còn hợp lệ
        if (accessToken != null && jwtTokenProvider.isTokenValid(accessToken)) {
            String jti = jwtTokenProvider.extractJti(accessToken);
            LocalDateTime expiresAt = jwtTokenProvider.extractExpiration(accessToken);
            revokedTokenService.revoke(jti, expiresAt);
        }


        // Xóa refresh token khỏi DB
        String refreshTokenStr = extractRefreshTokenFromCookie(httpRequest);
        if (refreshTokenStr != null) {
            try {
                String username = jwtTokenProvider.extractUsername(refreshTokenStr);
                userRepository.findByEmail(username).ifPresent(user ->
                        refreshTokenService.revokeAllByUser(user.getId()));
            } catch (Exception e) {
                log.debug("Could not extract user from refresh token during logout: {}", e.getMessage());
            }
        }

        // Xóa cookie (maxAge = 0)
        setRefreshTokenCookie(httpResponse, "", 0);

        log.info("User logged out");
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private void setRefreshTokenCookie(HttpServletResponse response, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);         // HTTPS only — Spring Boot tự bỏ qua trên dev HTTP
        cookie.setPath("/api/auth");    // chỉ gửi khi gọi /api/auth (không leak sang các route khác)
        cookie.setMaxAge(maxAgeSeconds);
        // SameSite=Strict — không thể set qua javax.servlet.http.Cookie API trực tiếp,
        // cần dùng response header
        response.addCookie(cookie);
        // Ghi đè Set-Cookie header để thêm SameSite=Strict
        response.setHeader("Set-Cookie",
                String.format("%s=%s; Path=/api/auth; Max-Age=%d; HttpOnly; Secure; SameSite=Strict",
                        REFRESH_TOKEN_COOKIE_NAME, value, maxAgeSeconds));
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_TOKEN_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}

