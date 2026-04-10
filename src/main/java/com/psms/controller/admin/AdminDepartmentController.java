package com.psms.controller.admin;

import com.psms.dto.request.CreateDepartmentRequest;
import com.psms.dto.request.UpdateDepartmentRequest;
import com.psms.dto.response.AdminDepartmentResponse;
import com.psms.dto.response.ApiResponse;
import com.psms.service.AdminDepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API quản lý phòng ban — chỉ SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/departments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Admin Department Management", description = "CRUD phòng ban — chỉ SUPER_ADMIN")
public class AdminDepartmentController {

    private final AdminDepartmentService adminDepartmentService;
    private static final String DEFAULT_PAGE_SIZE_STR = "10";

    @Operation(
        summary = "Danh sách phòng ban",
        description = "Filter keyword, isActive + phân trang. Chỉ SUPER_ADMIN."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminDepartmentResponse>>> listDepartments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size) {

        if (page < 0) throw new IllegalArgumentException("'page' phải >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("'size' phải trong khoảng 1-100");

        return ResponseEntity.ok(ApiResponse.success(
                adminDepartmentService.findAll(keyword, isActive, page, size)));
    }

    @Operation(summary = "Chi tiết phòng ban", description = "Lấy chi tiết 1 phòng ban theo ID.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminDepartmentResponse>> getDepartment(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminDepartmentService.findById(id)));
    }

    @Operation(
        summary = "Tạo phòng ban mới",
        description = "Tạo phòng ban mới. Code phải unique. Chỉ SUPER_ADMIN."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<AdminDepartmentResponse>> createDepartment(
            @RequestBody @Valid CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo phòng ban thành công",
                        adminDepartmentService.create(request)));
    }

    @Operation(
        summary = "Cập nhật phòng ban",
        description = "Cập nhật tên, địa chỉ, SĐT, email, trưởng phòng (không được đổi code)."
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminDepartmentResponse>> updateDepartment(
            @PathVariable Long id,
            @RequestBody @Valid UpdateDepartmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thành công",
                adminDepartmentService.update(id, request)));
    }

    @Operation(
        summary = "Xóa phòng ban",
        description = """
            Xóa phòng ban. Bị chặn nếu còn cán bộ đang thuộc phòng ban.
            Hãy chuyển tất cả cán bộ sang phòng ban khác trước khi xóa.
            """
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable Long id) {
        adminDepartmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa phòng ban", null));
    }
}

