package com.psms.controller.admin;

import com.psms.dto.response.ApiResponse;
import com.psms.dto.response.ImportResult;
import com.psms.enums.ExportType;
import com.psms.enums.ImportType;
import com.psms.service.CsvExportService;
import com.psms.service.CsvImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

/**
 * REST controller cho Import/Export CSV — MANAGER và SUPER_ADMIN.
 *
 * <p>Export: {@code GET /api/admin/export/{type}} — 5 loại: citizens, applications, services, departments, staff
 * <p>Import: {@code POST /api/admin/import/{type}} — 4 loại: citizens, services, departments, staff
 * <p>Template: {@code GET /api/admin/import/templates/{type}} — download CSV mẫu
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
@Tag(name = "Admin CSV", description = "Import/Export dữ liệu CSV")
public class AdminCsvController {

    private final CsvExportService exportService;
    private final CsvImportService importService;

    // ── Export ─────────────────────────────────────────────────────────────────

    @GetMapping("/export/{type}")
    @Operation(
        summary = "Export dữ liệu CSV",
        description = """
            Export dữ liệu ra file CSV với UTF-8 BOM.
            type: citizens | applications | services | departments | staff.
            Yêu cầu role MANAGER hoặc SUPER_ADMIN.
            """
    )
    public ResponseEntity<byte[]> exportCsv(@PathVariable String type) throws IOException {
        ExportType exportType;
        try {
            exportType = ExportType.fromCode(type);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
        byte[] csvData = exportService.exportByType(exportType);
        String filename = type + "_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    @PostMapping(value = "/import/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Import dữ liệu từ CSV",
        description = """
            Import dữ liệu từ file CSV. Không fail toàn bộ nếu 1 row lỗi.
            type: citizens | services | departments | staff.
            Response: { total, success, failed, errors:[{row, field, message}] }.
            Giới hạn tối đa 1000 rows/lần. Yêu cầu role MANAGER hoặc SUPER_ADMIN.
            """
    )
    public ResponseEntity<ApiResponse<ImportResult>> importCsv(
            @PathVariable String type,
            @RequestParam("file") MultipartFile file) throws IOException {

        ImportType importType;
        try {
            importType = ImportType.fromCode(type);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Loại import không hợp lệ: " + type));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Vui lòng chọn file CSV để import"));
        }

        ImportResult result = importService.importByType(importType, file);
        String message = String.format("Import hoàn thành: %d/%d thành công", result.getSuccess(), result.getTotal());
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    // ── Template download ──────────────────────────────────────────────────────

    @GetMapping("/import/templates/{type}")
    @Operation(
        summary = "Download CSV mẫu",
        description = """
            Download file CSV mẫu (chỉ có header row) để điền dữ liệu trước khi import.
            type: citizens | services | departments | staff.
            """
    )
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String type) throws IOException {
        ImportType importType;
        try {
            importType = ImportType.fromCode(type);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }

        byte[] template = importService.getTemplate(importType);
        String filename = "template_" + type + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(template);
    }
}
