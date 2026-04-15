package com.psms.controller.admin;

import com.psms.dto.request.UpdateStaffRequest;
import com.psms.dto.response.AdminStaffResponse;
import com.psms.dto.response.ApiResponse;
import com.psms.service.AdminStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API quản lý cán bộ — MANAGER và SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/staff")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
@Tag(name = "Admin Staff Management", description = "Quản lý cán bộ — MANAGER và SUPER_ADMIN")
public class AdminStaffController {

    private final AdminStaffService adminStaffService;
    private static final String DEFAULT_PAGE_SIZE_STR = "10";


    @Operation(
        summary = "Danh sách cán bộ",
        description = """
            Filter theo phòng ban, trạng thái is_available + phân trang.
            Kèm workload (activeApplicationCount) cho mỗi cán bộ.
            """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminStaffResponse>>> listStaff(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean isAvailable,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size) {

        if (page < 0) throw new IllegalArgumentException("'page' phải >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("'size' phải trong khoảng 1-100");

        return ResponseEntity.ok(ApiResponse.success(
                adminStaffService.findAll(departmentId, isAvailable, page, size)));
    }

    @Operation(summary = "Chi tiết cán bộ", description = "Lấy thông tin chi tiết 1 cán bộ.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminStaffResponse>> getStaff(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminStaffService.findById(id)));
    }

    @Operation(
        summary = "Cập nhật cán bộ",
        description = """
            Cập nhật phòng ban, chức vụ, trạng thái sẵn sàng (is_available) của cán bộ.
            Gán hồ sơ cho cán bộ thực hiện qua PUT /api/admin/applications/{id}/assign.
            """
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminStaffResponse>> updateStaff(
            @PathVariable Long id,
            @RequestBody @Valid UpdateStaffRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thành công",
                adminStaffService.update(id, request)));
    }
}

