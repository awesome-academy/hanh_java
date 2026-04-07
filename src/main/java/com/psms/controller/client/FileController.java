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

            **Path:** `/api/files/{applicationId}/{filename}`

            **Business rules:**
            - Cần đăng nhập
            - CITIZEN chỉ download được file thuộc hồ sơ của mình
            - STAFF / MANAGER / SUPER_ADMIN download được tất cả
            - File không tồn tại → 404
            - Không có quyền → 400
            """
    )
    @GetMapping("/**")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {

        // Extract relativePath: loại bỏ prefix "/api/files/"
        String uri = request.getRequestURI();
        String relativePath = uri.startsWith(PREFIX) ? uri.substring(PREFIX.length()) : uri;

        // Kiểm tra quyền (throws BusinessException nếu không có quyền)
        documentService.authorizeDownload(relativePath, user);

        Resource resource = fileStorageService.load(relativePath);

        // Content-Disposition: inline để browser preview PDF/ảnh, attachment cho docx
        String contentType = resolveContentType(relativePath);
        String disposition = relativePath.endsWith(".docx") ? "attachment" : "inline";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + resource.getFilename() + "\"")
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
