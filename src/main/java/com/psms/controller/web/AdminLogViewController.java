package com.psms.controller.web;

import com.psms.dto.response.ActivityLogResponse;
import com.psms.enums.ActionType;
import com.psms.service.ActivityLogService;
import com.psms.util.PaginationInfo;
import com.psms.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import com.psms.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MVC controller render trang nhật ký hoạt động hệ thống.
 * <p>Access: MANAGER + SUPER_ADMIN.
 */
@Controller
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
public class AdminLogViewController {

    private final ActivityLogService activityLogService;
    private static final String DEFAULT_PAGE_SIZE_STR = "20";

    /** Tất cả action types được phép — dùng cho dropdown filter. */
    private static final List<ActionType> ALL_ACTIONS = List.of(ActionType.values());

    /**
     * GET /admin/logs — hiển thị danh sách log với filter.
     */
    @GetMapping
    public String logList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size,
            Model model) {

        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to   = toDate   != null ? toDate.atTime(23, 59, 59) : null;


        int safePage = Math.max(page, 0);
        int safeSize = Math.min(100, Math.max(size, 1));

        Page<ActivityLogResponse> logs = activityLogService.findLogs(keyword, null, action, from, to, safePage, safeSize);
        PaginationInfo pageInfo = PaginationUtils.calculate(logs);

        model.addAttribute("logs", logs);
        model.addAttribute("allActions", ALL_ACTIONS);
        model.addAttribute("keyword", keyword);
        model.addAttribute("action", action);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("pageStart", pageInfo.pageStart());
        model.addAttribute("pageEnd", pageInfo.pageEnd());
        model.addAttribute("displayFrom", pageInfo.displayFrom());
        model.addAttribute("displayTo", pageInfo.displayTo());
        model.addAttribute("activePage", "logs");
        return "admin/log-list";
    }

    /**
     * DELETE /admin/logs/purge — xóa log cũ hơn N ngày (SUPER_ADMIN only).
     *
     * <p>Endpoint này nằm trên MVC chain (session-based, CSRF enabled) thay vì
     * REST API chain, để Thymeleaf page có thể gọi qua fetch() với session auth +
     * CSRF header mà không cần gửi Authorization Bearer token.
     *
     * <p><b>Business rule:</b> Chỉ xóa log cũ hơn {@code days} ngày. Default = 90.
     */
    @DeleteMapping("/purge")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> purge(
            @RequestParam(defaultValue = "90") int days) {
        int deleted = activityLogService.purgeOlderThan(days);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xóa " + deleted + " bản ghi log cũ hơn " + days + " ngày",
                Map.of("deleted", deleted)));
    }
}

