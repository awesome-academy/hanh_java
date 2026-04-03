package com.psms.controller.client;

import com.psms.dto.response.ApiResponse;
import com.psms.dto.response.ServiceCategoryResponse;
import com.psms.dto.response.ServiceTypeDetailResponse;
import com.psms.dto.response.ServiceTypeResponse;
import com.psms.service.ServiceCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller cổng công dân — Danh mục dịch vụ công.
 *
 * <p>Tất cả endpoint đều PUBLIC (không cần đăng nhập).
 * Đã cấu hình permitAll trong {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
@Tag(name = "Service Catalog — Public", description = "Xem danh mục lĩnh vực và dịch vụ công (không cần đăng nhập)")
public class ServiceCatalogController {

    private final ServiceCatalogService serviceCatalogService;

    private static final String DEFAULT_PAGE_SIZE_STR = "10";

    // ─── GET /api/client/service-categories ───────────────────

    @Operation(
        summary = "Danh sách lĩnh vực dịch vụ công",
        description = """
            Trả về tất cả lĩnh vực (ServiceCategory) đang active,
            sắp xếp theo sort_order tăng dần.

            **Dùng cho:** Dropdown filter trang /services + Category grid trang chủ.

            **Business rules:**
            - Chỉ trả category có `is_active = true`
            - Mỗi category kèm `serviceCount` — số DV đang active trong lĩnh vực đó
            - Không cần đăng nhập

            **Output:** List<ServiceCategoryResponse>
            """
    )
    @GetMapping("/service-categories")
    public ResponseEntity<ApiResponse<List<ServiceCategoryResponse>>> listCategories() {
        List<ServiceCategoryResponse> categories = serviceCatalogService.findAllActiveCategories();
        return ResponseEntity.ok(ApiResponse.success("OK", categories));
    }

    // ─── GET /api/client/services ─────────────────────────────

    @Operation(
        summary = "Danh sách dịch vụ công (phân trang + filter)",
        description = """
            Tìm kiếm dịch vụ công với filter keyword và lĩnh vực, kết quả phân trang.

            **Query params:**
            - `keyword`    — tên DV chứa chuỗi này (case-insensitive, partial match)
            - `categoryId` — lọc theo lĩnh vực; bỏ trống = tất cả
            - `page`       — trang hiện tại (0-based, mặc định 0)
            - `size`       — số bản ghi mỗi trang (mặc định 10, max 50)

            **Business rules:**
            - Chỉ trả DV có `is_active = true`
            - Kết quả sắp xếp theo tên A-Z
            - Pagination metadata trả trong Page wrapper

            **Output:** Page<ServiceTypeResponse>
            """
    )
    @GetMapping("/services")
    public ResponseEntity<ApiResponse<Page<ServiceTypeResponse>>> listServices(
        @Parameter(description = "Tìm theo tên dịch vụ") @RequestParam(required = false) String keyword,
        @Parameter(description = "Lọc theo ID lĩnh vực") @RequestParam(required = false) Integer categoryId,
        @Parameter(description = "Trang (0-based)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Số bản ghi/trang (max 50)")  @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size ) {

        // Giới hạn page không âm và size trong khoảng 1..50 để tránh PageRequest.of(...) ném lỗi
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 50);
        Page<ServiceTypeResponse> result = serviceCatalogService.searchServices(keyword, categoryId, safePage, safeSize);
        return ResponseEntity.ok(ApiResponse.success("OK", result));
    }

    // ─── GET /api/client/services/{id} ────────────────────────

    @Operation(
        summary = "Chi tiết một dịch vụ công",
        description = """
            Lấy thông tin đầy đủ của một dịch vụ: tên, mô tả, yêu cầu hồ sơ,
            thời hạn xử lý, lệ phí, phòng ban phụ trách.

            **Business rules:**
            - Chỉ trả DV có `is_active = true`
            - DV không tồn tại hoặc đã tắt → 404

            **Output:** ServiceTypeDetailResponse
            """
    )
    @GetMapping("/services/{id}")
    public ResponseEntity<ApiResponse<ServiceTypeDetailResponse>> getServiceDetail(
            @PathVariable Long id) {
        ServiceTypeDetailResponse detail = serviceCatalogService.findServiceById(id);
        return ResponseEntity.ok(ApiResponse.success("OK", detail));
    }
}

