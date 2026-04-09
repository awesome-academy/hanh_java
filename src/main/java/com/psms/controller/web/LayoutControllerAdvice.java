package com.psms.controller.web;

import com.psms.entity.User;
import com.psms.service.AdminApplicationService;
import com.psms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Inject các biến layout vao Model cho moi request trong controller.web package:
 * <ul>
 *   <li>{@code pendingCount} — số hồ sơ chờ xử lý, dùng cho sidebar badge admin</li>
 *   <li>{@code unreadCount} — số thông báo chưa đọc, dùng cho badge 🔔 topbar citizen</li>
 * </ul>
 *
 * <p>basePackageClasses giới hạn scope chỉ trong controller web. Không ảnh hưởng REST controllers (/api/**).
 */
@ControllerAdvice(basePackageClasses = LayoutControllerAdvice.class)
@RequiredArgsConstructor
public class LayoutControllerAdvice {

    private final AdminApplicationService adminApplicationService;
    private final NotificationService notificationService;

    /**
     * Them pendingCount (SUBMITTED + RECEIVED) vao model.
     * Chi chay khi request path bat dau /admin/ de tranh query khong can thiet.
     */
    @ModelAttribute("pendingCount")
    public long pendingCount(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/admin/")) {
            return adminApplicationService.countPending();
        }
        return 0L;
    }

    /**
     * Số thông báo chưa đọc — badge 🔔 trên topbar client.
     *
     * <p>Chỉ query khi:
     * <ul>
     *   <li>User đã đăng nhập (principal != null)</li>
     *   <li>User có role CITIZEN</li>
     *   <li>Path không phải /admin/** (admin không dùng badge này)</li>
     * </ul>
     *
     * <p>Kết quả được dùng để render badge trên mọi trang client.
     * JS polling 30s sẽ cập nhật badge mà không cần reload trang.
     */
    @ModelAttribute("unreadCount")
    public long unreadCount(HttpServletRequest request,
                            @AuthenticationPrincipal User user) {
        if (user == null) return 0L;
        if (request.getRequestURI().startsWith("/admin/")) return 0L;

        boolean isCitizen = user.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CITIZEN".equals(a.getAuthority()));
        if (!isCitizen) return 0L;

        return notificationService.countUnread(user.getId());
    }
}
