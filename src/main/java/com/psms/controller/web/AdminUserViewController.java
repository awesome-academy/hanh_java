package com.psms.controller.web;

import com.psms.dto.request.CreateUserRequest;
import com.psms.dto.request.UpdateUserRequest;
import com.psms.dto.request.UpdateUserRolesRequest;
import com.psms.dto.response.AdminUserResponse;
import com.psms.dto.response.ApiResponse;
import com.psms.entity.Department;
import com.psms.entity.User;
import com.psms.enums.RoleName;
import com.psms.repository.DepartmentRepository;
import com.psms.service.AdminUserService;
import com.psms.util.PaginationInfo;
import com.psms.util.PaginationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MVC controller cho trang quản lý người dùng (SUPER_ADMIN).
 * <ol>
 *   <li>GET /admin/users — render trang list user với filter + pagination (SSR)</li>
 *   <li>POST/PUT/DELETE /admin/users/** — các action tạo/sửa/xóa user, trả về JSON (AJAX)</li>
 * </ol>
 * <p>Tất cả route yêu cầu role SUPER_ADMIN.
 * <p>POST/PUT/DELETE trả JSON để JS xử lý cập nhật UI động mà không cần reload page. GET trả HTML để render server-side.
 * <p>Exception handling được delegate hoàn toàn cho {@link com.psms.exception.GlobalExceptionHandler}
 * — bao gồm BusinessException, ResourceNotFoundException, MethodArgumentNotValidException.
 * <p>Business rules:
 * <ul>
 *   <li>Chỉ SUPER_ADMIN có quyền truy cập</li>
 *   <li>Filter theo role (CITIZEN/STAFF/MANAGER/SUPER_ADMIN), isActive (true/false), keyword (fullName/email LIKE)</li>
 *   <li>Sort: createdAt DESC</li>
 *   <li>Page size mặc định 10</li>
 *   <li>Không cho phép SUPER_ADMIN khóa/xóa chính mình</li>
 * </ul>
 */
@Slf4j
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserViewController {

    private final AdminUserService adminUserService;
    private final DepartmentRepository departmentRepository;

    private static final String DEFAULT_PAGE_SIZE_STR = "10";

    // ─── GET /admin/users — SSR page ───────────────────────────────────────────

    @GetMapping
    public String userList(
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size,
            @AuthenticationPrincipal User currentUser,
            Model model) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(100, Math.max(size, 1));

        Page<AdminUserResponse> users = adminUserService.findAll(role, isActive, keyword, safePage, safeSize);
        PaginationInfo pageInfo = PaginationUtils.calculate(users);
        List<Department> departments = departmentRepository.findAllByIsActiveTrueOrderByNameAsc();

        model.addAttribute("users", users);
        model.addAttribute("allRoles", RoleName.values());
        model.addAttribute("departments", departments);
        model.addAttribute("role", role);
        model.addAttribute("isActive", isActive);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageStart", pageInfo.pageStart());
        model.addAttribute("pageEnd", pageInfo.pageEnd());
        model.addAttribute("displayFrom", pageInfo.displayFrom());
        model.addAttribute("displayTo", pageInfo.displayTo());
        model.addAttribute("activePage", "users");
        // Truyền ID của user đang đăng nhập để UI ẩn các action trên chính mình
        model.addAttribute("currentUserId", currentUser != null ? currentUser.getId() : -1L);
        return "admin/user-list";
    }

    // ─── POST /admin/users — Create ────────────────────────────────

    @ResponseBody
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AdminUserResponse>> createUser(
            @RequestBody @Valid CreateUserRequest request) {
        AdminUserResponse created = adminUserService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo tài khoản thành công", created));
    }

    // ─── PUT /admin/users/{id} — Update ───────────────────────────

    @ResponseBody
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(
            @PathVariable Long id,
            @RequestBody @Valid UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thành công",
                adminUserService.updateUser(id, request)));
    }

    // ─── PUT /admin/users/{id}/lock ─────────────────────────────────────────────

    @ResponseBody
    @PutMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<AdminUserResponse>> lockUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã khóa tài khoản",
                adminUserService.lockUser(id)));
    }

    // ─── PUT /admin/users/{id}/unlock ───────────────────────────────────────────

    @ResponseBody
    @PutMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unlockUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã mở khóa tài khoản",
                adminUserService.unlockUser(id)));
    }

    // ─── DELETE /admin/users/{id} — Soft delete ────────────────────────────────

    @ResponseBody
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        adminUserService.softDeleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa tài khoản", null));
    }

    // ─── PUT /admin/users/{id}/roles — Update roles ─────────────────────────────

    @ResponseBody
    @PutMapping(value = "/{id}/roles", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateRoles(
            @PathVariable Long id,
            @RequestBody @Valid UpdateUserRolesRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật quyền thành công",
                adminUserService.updateRoles(id, request)));
    }
}
