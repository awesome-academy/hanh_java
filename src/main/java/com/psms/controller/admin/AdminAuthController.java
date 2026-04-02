package com.psms.controller.admin;

import com.psms.dto.request.LoginRequest;
import com.psms.dto.response.ApiResponse;
import com.psms.dto.response.AuthResponse;
import com.psms.enums.RoleName;
import com.psms.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller xử lý auth cho cổng quản trị nội bộ.
 *
 * <p>Chỉ tài khoản có role {@code STAFF}, {@code MANAGER}, hoặc {@code SUPER_ADMIN}
 * Citizen cố đăng nhập → 400 "Bạn không có quyền truy cập cổng quản trị".
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Tag(name = "Auth — Admin", description = "Đăng nhập cổng quản trị nội bộ (STAFF+)")
public class AdminAuthController {

    private final AuthService authService;

    // ----------------------------------------------------------------
    //  POST /api/admin/auth/login
    // ----------------------------------------------------------------

    @Operation(
        summary = "Đăng nhập — Admin (STAFF+)",
        description = """
            Tương tự citizen login nhưng **chỉ cho phép** tài khoản có role
            STAFF, MANAGER, hoặc SUPER_ADMIN.

            **Business rules:**
            - Citizen cố đăng nhập → 400 "Bạn không có quyền truy cập cổng quản trị"
            - Tài khoản bị khóa → 401
            - Thành công: access token trong body, refresh token trong HttpOnly cookie

            **Input:** LoginRequest (email, password)
            **Output:** AuthResponse (accessToken, expiresIn, email, fullName, roles)
            """
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> adminLogin(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse httpResponse,
            HttpSession session) {

        List<RoleName> requiredRoles = List.of(
                RoleName.STAFF, RoleName.MANAGER, RoleName.SUPER_ADMIN);

        AuthResponse auth = authService.login(request, requiredRoles, httpResponse, session);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập cổng quản trị thành công", auth));
    }
}

