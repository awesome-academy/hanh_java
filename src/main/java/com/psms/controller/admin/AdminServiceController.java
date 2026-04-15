package com.psms.controller.admin;

import com.psms.dto.request.CreateServiceTypeRequest;
import com.psms.dto.request.UpdateServiceTypeRequest;
import com.psms.dto.response.AdminServiceTypeResponse;
import com.psms.dto.response.ApiResponse;
import com.psms.service.AdminServiceTypeService;
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
 * REST API quản lý dịch vụ công — chỉ SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/services")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Admin Service Management", description = "CRUD dịch vụ công — chỉ SUPER_ADMIN")
public class AdminServiceController {

    private final AdminServiceTypeService adminServiceTypeService;
    private static final String DEFAULT_PAGE_SIZE_STR = "10";

    @Operation(
        summary = "Danh sách dịch vụ",
        description = "Filter keyword, categoryId, isActive + phân trang. Chỉ SUPER_ADMIN."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminServiceTypeResponse>>> listServices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size) {

        if (page < 0) throw new IllegalArgumentException("'page' phải >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("'size' phải trong khoảng 1-100");

        return ResponseEntity.ok(ApiResponse.success(
                adminServiceTypeService.findAll(keyword, categoryId, isActive, page, size)));
    }

    @Operation(summary = "Chi tiết dịch vụ", description = "Lấy chi tiết 1 dịch vụ theo ID.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminServiceTypeResponse>> getService(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminServiceTypeService.findById(id)));
    }

    @Operation(
        summary = "Tạo dịch vụ mới",
        description = "Tạo dịch vụ công mới. Code phải unique. Chỉ SUPER_ADMIN."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<AdminServiceTypeResponse>> createService(
            @RequestBody @Valid CreateServiceTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo dịch vụ thành công",
                        adminServiceTypeService.create(request)));
    }

    @Operation(
        summary = "Cập nhật dịch vụ",
        description = "Cập nhật thông tin dịch vụ (không được đổi code)."
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminServiceTypeResponse>> updateService(
            @PathVariable Long id,
            @RequestBody @Valid UpdateServiceTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thành công",
                adminServiceTypeService.update(id, request)));
    }

    @Operation(
        summary = "Bật/tắt dịch vụ",
        description = "Toggle is_active. Dịch vụ tắt không hiển thị cho công dân."
    )
    @PutMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<AdminServiceTypeResponse>> toggleService(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã thay đổi trạng thái",
                adminServiceTypeService.toggleActive(id)));
    }

    @Operation(
        summary = "Xóa dịch vụ",
        description = """
            Xóa dịch vụ. Bị chặn nếu có hồ sơ đang ở trạng thái chưa hoàn thành
            (SUBMITTED/RECEIVED/PROCESSING/ADDITIONAL_REQUIRED).
            """
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteService(@PathVariable Long id) {
        adminServiceTypeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa dịch vụ", null));
    }
}

