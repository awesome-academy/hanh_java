package com.psms.controller.client;

import com.psms.dto.request.SubmitApplicationRequest;
import com.psms.dto.response.ApiResponse;
import com.psms.dto.response.ApplicationDetailResponse;
import com.psms.dto.response.ApplicationResponse;
import com.psms.entity.User;
import com.psms.enums.ApplicationStatus;
import com.psms.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller — Hồ sơ của citizen (cần đăng nhập, role CITIZEN).
 *
 * <p>Ownership được đảm bảo ở service layer: mọi query đều lọc theo citizenId
 * lấy từ userId của principal → tránh IDOR (Insecure Direct Object Reference).
 */
@RestController
@RequestMapping("/api/client/applications")
@RequiredArgsConstructor
@Tag(name = "Client Application", description = "Nộp hồ sơ và tra cứu trạng thái (cần đăng nhập — CITIZEN)")
public class ClientApplicationController {

    private final ApplicationService applicationService;
    private static final String DEFAULT_PAGE_SIZE_STR = "10";

    // ─── POST /api/client/applications ────────────────────────────────

    @Operation(
        summary = "Nộp hồ sơ mới",
        description = """
            Citizen nộp hồ sơ cho một dịch vụ công.

            **Business rules:**
            - Citizen phải có profile (bảng citizens) mới nộp được
            - Dịch vụ phải đang active (`is_active = true`)
            - Mã HS được sinh tự động: `HS-YYYYMMDD-NNNNN` (thread-safe)
            - Trạng thái ban đầu: `SUBMITTED`
            - Hạn xử lý = ngày nộp + `processing_time_days` của dịch vụ
            - Ghi history: `null → SUBMITTED`

            **Output:** ApplicationResponse với applicationCode đã sinh
            """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ApplicationResponse>> submit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitApplicationRequest request) {

        User user = (User) userDetails;
        ApplicationResponse response = applicationService.submit(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Nộp hồ sơ thành công", response));
    }

    // ─── GET /api/client/applications ─────────────────────────────────

    @Operation(
        summary = "Danh sách hồ sơ của tôi",
        description = """
            Lấy danh sách hồ sơ của citizen đang đăng nhập, phân trang.

            **Query params:**
            - `status`  — lọc theo trạng thái (tuỳ chọn)
            - `page`    — trang hiện tại (0-based, mặc định 0)
            - `size`    — số bản ghi mỗi trang (mặc định 10)

            **Business rules:**
            - Chỉ trả hồ sơ của chính citizen đang đăng nhập
            - Sắp xếp: mới nhất trước

            **Output:** Page<ApplicationResponse>
            """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ApplicationResponse>>> listMyApplications(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Lọc theo trạng thái") @RequestParam(required = false) ApplicationStatus status,
            @Parameter(description = "Trang (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi/trang") @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size) {

        User user = (User) userDetails;
        // Giới hạn page không âm và size trong khoảng 1..50 để tránh PageRequest.of(...) ném lỗi
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 50);
        Page<ApplicationResponse> applications = applicationService.findMyApplications(user.getId(), status, safePage, safeSize);
        return ResponseEntity.ok(ApiResponse.success("OK", applications));
    }

    // ─── GET /api/client/applications/{id} ────────────────────────────

    @Operation(
        summary = "Chi tiết hồ sơ",
        description = """
            Lấy chi tiết hồ sơ kèm timeline trạng thái.

            **Business rules:**
            - Chỉ xem được hồ sơ của chính mình
            - Hồ sơ của người khác → 404 (tránh IDOR)

            **Output:** ApplicationDetailResponse kèm statusHistory[]
            """
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ApplicationDetailResponse>> getMyApplicationDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {

        User user = (User) userDetails;
        ApplicationDetailResponse detail = applicationService.findMyApplicationById(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("OK", detail));
    }
}

