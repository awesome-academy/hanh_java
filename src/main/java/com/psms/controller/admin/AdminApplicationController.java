package com.psms.controller.admin;

import com.psms.dto.request.AssignStaffRequest;
import com.psms.dto.request.UpdateStatusRequest;
import com.psms.dto.response.AdminApplicationResponse;
import com.psms.dto.response.ApiResponse;
import com.psms.entity.User;
import com.psms.enums.ApplicationStatus;
import com.psms.service.AdminApplicationService;
import com.psms.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST API quan ly ho so danh cho admin.
 * - GET  /api/admin/applications       — danh sach filter da chieu
 * - GET  /api/admin/applications/{id}  — chi tiet + history
 * - PUT  /api/admin/applications/{id}/status — cap nhat trang thai (state machine)
 * - PUT  /api/admin/applications/{id}/assign — phan cong can bo
 */
@RestController
@RequestMapping("/api/admin/applications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER','SUPER_ADMIN')")
@Tag(name = "Admin Application", description = "Quan ly ho so (STAFF / MANAGER / SUPER_ADMIN)")
public class AdminApplicationController {

    private final AdminApplicationService adminApplicationService;
    private final DocumentService documentService;

    private static final String DEFAULT_PAGE_SIZE_STR = "20";

    @Operation(
        summary = "Danh sách hồ sơ (Admin)",
        description = """
            Filter và phân trang hồ sơ cho admin theo: status, serviceTypeId, staffId, từ ngày, đến ngày.
            Phân trang 20 hồ sơ/trang, sort submittedAt DESC.
            """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminApplicationResponse>>> listApplications(
            @Parameter(description = "Loc theo trang thai") @RequestParam(required = false) ApplicationStatus status,
            @Parameter(description = "Loc theo ID dich vu") @RequestParam(required = false) Long serviceTypeId,
            @Parameter(description = "Lọc theo Staff.id (bảng staff, KHÔNG phải User.id)") @RequestParam(required = false) Long staffId,
            @Parameter(description = "Tu ngay (ISO datetime)") @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Den ngay (ISO datetime)") @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "Trang (0-based, >= 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "So ban ghi/trang (1–100)") @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size) {

        if (page < 0) {
            throw new IllegalArgumentException("'page' phải >= 0, nhận: " + page);
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("'size' phải trong khoảng 1–100, nhận: " + size);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' (" + from + ") không được sau 'to' (" + to + ")");
        }

        Page<AdminApplicationResponse> result = adminApplicationService
                .findAll(status, serviceTypeId, staffId, from, to, page, size);
        return ResponseEntity.ok(ApiResponse.success("OK", result));
    }

    @Operation(
        summary = "Chi tiet ho so (admin)",
        description = "Lay thong tin day du + timeline trang thai. Admin xem duoc tat ca ho so."
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminApplicationResponse>> getApplicationDetail(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("OK", adminApplicationService.findById(id)));
    }

    @Operation(
        summary = "Cập nhật trạng thái hồ sơ (state machine)",
        description = """
            State machine bắt buộc phải tuân theo:
              SUBMITTED -> RECEIVED
              RECEIVED  -> PROCESSING
              PROCESSING -> APPROVED | REJECTED | ADDITIONAL_REQUIRED
              ADDITIONAL_REQUIRED -> SUBMITTED

            - Transition khong hop le -> 400
            - REJECTED / ADDITIONAL_REQUIRED bat buoc co notes
            """
    )
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminApplicationResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal User actingUser) {

        AdminApplicationResponse response = adminApplicationService.updateStatus(id, request, actingUser);
        return ResponseEntity.ok(ApiResponse.success("Cap nhat trang thai thanh cong", response));
    }

    @Operation(
        summary = "Phân công cán bộ phụ trách hồ sơ",
        description = """
            Gắn assignedStaffId cho hồ sơ.
            Chi can bo co is_available=true moi duoc chon.
            """
    )
    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('MANAGER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminApplicationResponse>> assignStaff(
            @PathVariable Long id,
            @Valid @RequestBody AssignStaffRequest request) {

        AdminApplicationResponse response = adminApplicationService.assignStaff(id, request);
        return ResponseEntity.ok(ApiResponse.success("Phan cong thanh cong", response));
    }

    // ─── POST /api/admin/applications/{id}/documents ──────────────────

    @Operation(
        summary = "Admin upload tài liệu phản hồi",
        description = """
            Cán bộ upload tài liệu phản hồi cho hồ sơ (is_response = true).

            **Business rules:**
            - Không giới hạn trạng thái hồ sơ
            - File: chỉ PDF/JPG/JPEG/PNG/DOCX, tối đa 10 MB/file
            - Tài liệu sẽ hiển thị riêng biệt với tài liệu citizen nộp

            **Output:** 200 OK khi thành công
            """
    )
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> uploadResponseDocuments(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files) {

        documentService.uploadResponseDocuments(id, files, user);
        return ResponseEntity.ok(ApiResponse.success("Upload tài liệu phản hồi thành công", null));
    }
}

