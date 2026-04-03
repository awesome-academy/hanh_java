package com.psms.controller.client;

import com.psms.dto.request.LoginRequest;
import com.psms.dto.request.RegisterRequest;
import com.psms.dto.response.ApiResponse;
import com.psms.dto.response.AuthResponse;
import com.psms.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller xử lý auth cho cổng công dân.
 *
 * <p>Các endpoint này là public (không cần JWT) — đã cấu hình trong {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth — Citizen", description = "Đăng ký, đăng nhập, refresh token, đăng xuất cho công dân")
public class AuthController {

    private final AuthService authService;

    // ----------------------------------------------------------------
    // POST /api/auth/register
    // ----------------------------------------------------------------

    @Operation(
        summary = "Đăng ký tài khoản công dân",
        description = """
            Tạo tài khoản User + Citizen, gán role CITIZEN.

            **Business rules:**
            - Email phải unique trong hệ thống
            - Số CCCD/CMND phải unique
            - Mật khẩu tối thiểu 8 ký tự
            - Sau khi đăng ký thành công: chưa đăng nhập, cần gọi /login

            **Input:** RegisterRequest (fullName, email, password, nationalId, ...)
            **Output:** 201 Created — message xác nhận
            """
    )
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký thành công. Vui lòng đăng nhập.", null));
    }

    // ----------------------------------------------------------------
    // POST /api/auth/login
    // ----------------------------------------------------------------

    @Operation(
        summary = "Đăng nhập — cổng công dân",
        description = """
            Xác thực email + password, trả về access token và lưu refresh token.

            **Business rules:**
            - Kiểm tra account tồn tại, password đúng, không bị khóa (isLocked, lockedUntil)
            - Thành công: access token trả trong body, refresh token lưu HttpOnly cookie
            - Thành công: cập nhật last_login_at, reset failed_login_count về 0
            - Thất bại: không tiết lộ field nào sai

            **Input:** LoginRequest (email, password)
            **Output:** AuthResponse (accessToken, expiresIn, email, fullName, roles)
            """
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse httpResponse,
            HttpSession session) {

        AuthResponse auth = authService.login(request, List.of(), httpResponse, session);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", auth));
    }

    // ----------------------------------------------------------------
    // POST /api/auth/refresh-token
    // ----------------------------------------------------------------

    @Operation(
        summary = "Refresh access token (Token Rotation)",
        description = """
            Đọc refresh token từ HttpOnly cookie, xóa token cũ, sinh cặp token mới.

            **Business rules
            - Token Rotation: mỗi lần refresh → xóa token cũ → INSERT token mới
            - Reuse Detection: nếu token cũ đã bị dùng (không còn trong DB)
              → revoke toàn bộ session của user đó → 401, buộc login lại
            - Client nên gọi endpoint này khi nhận 401 từ các API khác

            **Input:** refresh_token cookie (HttpOnly, tự động gửi)
            **Output:** AuthResponse với access token mới; cookie mới được set
            """
    )
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            HttpSession session) {

        AuthResponse auth = authService.refreshToken(httpRequest, httpResponse, session);
        return ResponseEntity.ok(ApiResponse.success("Token đã được làm mới", auth));
    }

    // ----------------------------------------------------------------
    // POST /api/auth/logout
    // ----------------------------------------------------------------

    @Operation(
        summary = "Đăng xuất",
        description = """
            Blacklist access token (jti → revoked_access_tokens), xóa refresh token
            khỏi DB, invalidate HttpSession, xóa HttpOnly cookie.

            **Business rules:**
            - Access token bị blacklist → dùng lại sẽ nhận 401 dù chưa hết hạn
            - Tất cả refresh token của user bị xóa (logout all devices)
            - Không cần body — đọc token từ session + cookie

            **Output:** 200 OK
            """
    )
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        authService.logout(httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }
}

