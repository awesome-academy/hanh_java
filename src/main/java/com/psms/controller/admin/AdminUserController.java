package com.psms.controller.admin;

import com.psms.dto.request.CreateUserRequest;
import com.psms.dto.request.UpdateUserRequest;
import com.psms.dto.request.UpdateUserRolesRequest;
import com.psms.dto.response.AdminUserResponse;
import com.psms.dto.response.ApiResponse;
import com.psms.enums.RoleName;
import com.psms.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API quản lý người dùng — chỉ dành cho SUPER_ADMIN.
 *
 * <p>Tất cả endpoint yêu cầu JWT Bearer token với role SUPER_ADMIN.
 * STAFF/MANAGER gọi vào đây → 403 Forbidden.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Admin User Management", description = "CRUD người dùng — chỉ SUPER_ADMIN")
public class AdminUserController {

    private final AdminUserService adminUserService;

    private static final String DEFAULT_SIZE = "10";

    // ─── GET list ──────────────────────────────────────────────────────────────

    @Operation(
        summary = "Danh sách user",
        description = """
            Filter + phân trang danh sách người dùng.
            Business rules:
            - Chỉ SUPER_ADMIN có quyền truy cập
            - Filter theo role (CITIZEN/STAFF/MANAGER/SUPER_ADMIN), isActive (true/false), keyword (fullName/email LIKE)
            - Sort: createdAt DESC
            - Page size mặc định 10
            """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> listUsers(
            @Parameter(description = "Filter theo role") @RequestParam(required = false) RoleName role,
            @Parameter(description = "Filter theo trạng thái active") @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "Từ khoá tìm kiếm (fullName, email)") @RequestParam(required = false) String keyword,
            @Parameter(description = "Trang (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = DEFAULT_SIZE) int size) {

        if (page < 0) throw new IllegalArgumentException("'page' phải >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("'size' phải trong khoảng 1-100");

        return ResponseEntity.ok(ApiResponse.success(
                adminUserService.findAll(role, isActive, keyword, page, size)));
    }

    // ─── GET by ID ─────────────────────────────────────────────────────────────

    @Operation(
        summary = "Chi tiết user",
        description = "Lấy đầy đủ thông tin user kèm citizen/staff profile. 404 nếu không tồn tại."
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.findById(id)));
    }

    // ─── POST create ───────────────────────────────────────────────────────────

    @Operation(
        summary = "Tạo tài khoản",
        description = """
            Tạo tài khoản mới cho staff hoặc citizen.
            Business rules:
            - email phải unique
            - Nếu roles chứa CITIZEN: cần nationalId (CCCD phải unique)
            - Nếu roles chứa STAFF/MANAGER: cần staffCode (phải unique) + departmentId
            - Nếu roles chỉ có SUPER_ADMIN: không cần nationalId/staffCode/departmentId
            - Password tối thiểu 8 ký tự, được encode BCrypt
            """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<AdminUserResponse>> createUser(
            @RequestBody @Valid CreateUserRequest request) {
        AdminUserResponse created = adminUserService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo tài khoản thành công", created));
    }

    // ─── PUT update ────────────────────────────────────────────────────────────

    @Operation(
        summary = "Cập nhật thông tin user",
        description = """
            Cập nhật fullName, phone, và staff-specific fields (departmentId, position).
            Không cho phép sửa: email, password, roles (endpoint riêng /roles).
            """
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(
            @PathVariable Long id,
            @RequestBody @Valid UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thành công",
                adminUserService.updateUser(id, request)));
    }

    // ─── PUT lock / unlock ─────────────────────────────────────────────────────

    @Operation(
        summary = "Khóa tài khoản",
        description = """
            Set is_locked=true. Tài khoản bị khóa không thể đăng nhập.
            User.isAccountNonLocked() trả về false → Spring Security chặn login.
            """
    )
    @PutMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<AdminUserResponse>> lockUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã khóa tài khoản",
                adminUserService.lockUser(id)));
    }

    @Operation(
        summary = "Mở khóa tài khoản",
        description = "Set is_locked=false."
    )
    @PutMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unlockUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã mở khóa tài khoản",
                adminUserService.unlockUser(id)));
    }

    // ─── DELETE soft delete ────────────────────────────────────────────────────

    @Operation(
        summary = "Xóa tài khoản (soft delete)",
        description = """
            Set is_active=false. Dữ liệu hồ sơ vẫn giữ nguyên trong DB (audit trail).
            Không xóa cứng để giữ tính toàn vẹn lịch sử hồ sơ.
            """
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        adminUserService.softDeleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa tài khoản", null));
    }

    // ─── PUT update roles ──────────────────────────────────────────────────────

    @Operation(
        summary = "Cập nhật roles",
        description = """
            Gán / thu hồi role cho user. Thay thế toàn bộ roles hiện tại bằng set mới.
            Phải có ít nhất 1 role.
            Citizen không thể có role STAFF/MANAGER/SUPER_ADMIN, ngược lại cũng vậy.
            """
    )
    @PutMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateRoles(
            @PathVariable Long id,
            @RequestBody @Valid UpdateUserRolesRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật quyền thành công",
                adminUserService.updateRoles(id, request)));
    }
}

