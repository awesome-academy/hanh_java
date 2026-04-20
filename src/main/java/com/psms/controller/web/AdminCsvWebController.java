package com.psms.controller.web;

import com.psms.dto.response.ApiResponse;
import com.psms.dto.response.ImportResult;
import com.psms.service.CsvExportService;
import com.psms.service.CsvImportService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import com.psms.enums.ExportType;
import com.psms.enums.ImportType;

/**
 * MVC controller cho export CSV — dùng session auth (không cần Bearer token).
 */
@Hidden
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
public class AdminCsvWebController {

    private final CsvExportService exportService;
    private final CsvImportService importService;

    /**
     * Download CSV export qua session — dùng cho browser anchor link trong UI admin.
     */
    @GetMapping("/export/{type}")
    public ResponseEntity<byte[]> exportCsv(@PathVariable String type) throws IOException {
        ExportType exportType;
        try {
            exportType = ExportType.fromCode(type);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
        byte[] csv = exportService.exportByType(exportType);
        String filename = exportType.getCode() + "_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    /**
     * Import CSV qua session — dùng cho fetch() trong UI admin.
     */
    @PostMapping(value = "/import/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
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
        String msg = String.format("Import hoàn thành: %d/%d thành công", result.getSuccess(), result.getTotal());
        return ResponseEntity.ok(ApiResponse.success(msg, result));
    }

    /**
     * Download template CSV qua session — dùng cho link "Tải file mẫu" trong modal.
     */
    @GetMapping("/import/templates/{type}")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String type) throws IOException {
        ImportType importType;
        try {
            importType = ImportType.fromCode(type);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
        byte[] template = importService.getTemplate(importType);
        String filename = "template_" + importType.getCode() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(template);
    }
}
