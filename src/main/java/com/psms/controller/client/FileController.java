package com.psms.controller.client;

import com.psms.service.DocumentService;
import com.psms.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.psms.entity.User;
import org.springframework.web.bind.annotation.*;
import com.psms.entity.ApplicationDocument;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Download tài liệu hồ sơ — có kiểm tra quyền truy cập.
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "File", description = "Download tài liệu hồ sơ (cần đăng nhập)")
public class FileController {

    private final FileStorageService fileStorageService;
    private final DocumentService documentService;

    private static final String PREFIX = "/files/";

    @Operation(
        summary = "Download file tài liệu",
        description = """
            Tải về file tài liệu đính kèm của hồ sơ.

            **Path:** `/files/{applicationId}/{filename}`

            **Business rules:**
            - Cần đăng nhập
            - CITIZEN chỉ download được file thuộc hồ sơ của mình
            - STAFF / MANAGER / SUPER_ADMIN download được tất cả file
            """
    )
    @GetMapping("/**")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {

        // Extract relativePath: loại bỏ prefix "/api/files/" (có contextPath)
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String fullPrefix = contextPath + PREFIX;
        String relativePath = uri.startsWith(fullPrefix) ? uri.substring(fullPrefix.length()) : uri;

        // Kiểm tra quyền và lấy ApplicationDocument để lấy fileName gốc
        ApplicationDocument doc = documentService.authorizeDownload(relativePath, user);

        Resource resource = fileStorageService.load(relativePath);

        // Lấy tên file gốc (fileName) để hiển thị đúng khi tải về
        String originalFileName = doc.getFileName();
        // Encode theo RFC 5987 để hỗ trợ ký tự Unicode / có dấu tiếng Việt
        String encodedFileName = URLEncoder.encode(originalFileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        // Content-Disposition: inline để browser preview PDF/ảnh, attachment cho docx
        String contentType = resolveContentType(relativePath);
        String disposition = relativePath.endsWith(".docx") ? "attachment" : "inline";
        String contentDisposition = String.format(
                "%s; filename=\"%s\"; filename*=UTF-8''%s",
                disposition, originalFileName.replace("\"", ""), encodedFileName
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private String resolveContentType(String path) {
        if (path.endsWith(".pdf"))  return "application/pdf";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }
}
