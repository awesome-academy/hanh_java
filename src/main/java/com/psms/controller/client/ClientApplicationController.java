package com.psms.controller.client;

import com.psms.dto.request.SubmitApplicationRequest;
import com.psms.dto.response.ApiResponse;
import com.psms.dto.response.ApplicationDetailResponse;
import com.psms.dto.response.ApplicationResponse;
import com.psms.entity.User;
import com.psms.enums.ApplicationStatus;
import com.psms.service.ApplicationService;
import com.psms.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    private final DocumentService documentService;
    private static final String DEFAULT_PAGE_SIZE_STR = "10";

    // ─── POST /api/client/applications ────────────────────────────────

    @Operation(
        summary = "Nộp hồ sơ mới (multipart/form-data)",
        description = """
            Citizen nộp hồ sơ kèm tài liệu đính kèm.

            **Content-Type:** `multipart/form-data`

            **Business rules:**
            - Dịch vụ phải `is_active = true`
            - File: chỉ PDF/JPG/JPEG/PNG/DOCX, tối đa 10 MB/file
            - Mã HS sinh tự động: `HS-YYYYMMDD-NNNNN`
            - Trạng thái ban đầu: `SUBMITTED`
            - Ghi history: `null → SUBMITTED`

            **Output:** ApplicationResponse với applicationCode đã sinh
            """
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ApplicationResponse>> submit(
            @AuthenticationPrincipal User user,
            @Valid @ModelAttribute SubmitApplicationRequest request,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        ApplicationResponse response = applicationService.submit(user.getId(), request, files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Nộp hồ sơ thành công", response));
    }

    // ─── GET /api/client/applications ─────────────────────────────────

    @Operation(
        summary = "Danh sách hồ sơ của tôi",
        description = """
            Lấy danh sách hồ sơ của citizen đang đăng nhập, phân trang.

            **Business rules:**
            - Chỉ trả hồ sơ của chính citizen đang đăng nhập
            - Sắp xếp: mới nhất trước

            **Output:** Page<ApplicationResponse>
            """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ApplicationResponse>>> listMyApplications(
            @AuthenticationPrincipal User user,
            @Parameter(description = "Lọc theo trạng thái") @RequestParam(required = false) ApplicationStatus status,
            @Parameter(description = "Trang (0-based, >= 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi/trang (1–50)") @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size) {

        if (page < 0) throw new IllegalArgumentException("'page' phải >= 0, nhận: " + page);
        if (size < 1 || size > 50) throw new IllegalArgumentException("'size' phải trong khoảng 1–50, nhận: " + size);

        Page<ApplicationResponse> applications = applicationService.findMyApplications(user.getId(), status, page, size);
        return ResponseEntity.ok(ApiResponse.success("OK", applications));
    }

    // ─── GET /api/client/applications/{id} ────────────────────────────

    @Operation(
        summary = "Chi tiết hồ sơ",
        description = """
            Lấy chi tiết hồ sơ kèm timeline và danh sách tài liệu.

            **Business rules:**
            - Chỉ xem được hồ sơ của chính mình (IDOR protection)
            - `citizenDocuments`: tài liệu citizen nộp
            - `staffDocuments`: tài liệu phản hồi từ cán bộ

            **Output:** ApplicationDetailResponse
            """
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ApplicationDetailResponse>> getMyApplicationDetail(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        ApplicationDetailResponse detail = applicationService.findMyApplicationById(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("OK", detail));
    }

    // ─── POST /api/client/applications/{id}/documents ─────────────────

    @Operation(
        summary = "Upload tài liệu bổ sung",
        description = """
            Citizen upload tài liệu bổ sung theo yêu cầu của cán bộ.

            **Business rules:**
            - Chỉ cho phép khi status = `ADDITIONAL_REQUIRED`
            - Sau khi upload thành công → status tự động chuyển `SUBMITTED`
            - Ghi `ApplicationStatusHistory`: `ADDITIONAL_REQUIRED → SUBMITTED`
            - File: chỉ PDF/JPG/JPEG/PNG/DOCX, tối đa 10 MB/file
            - Ownership: citizen chỉ upload được hồ sơ của chính mình

            **Output:** 200 OK khi thành công
            """
    )
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> uploadSupplementalDocuments(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files) {

        documentService.uploadSupplementalDocuments(id, user.getId(), files);
        return ResponseEntity.ok(ApiResponse.success("Nộp bổ sung tài liệu thành công", null));
    }
}
