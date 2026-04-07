package com.psms.controller.web;

import com.psms.dto.request.AssignStaffRequest;
import com.psms.dto.request.UpdateStatusRequest;
import com.psms.dto.response.AdminApplicationResponse;
import com.psms.dto.response.DashboardStatsResponse;
import com.psms.dto.response.ServiceTypeResponse;
import com.psms.dto.response.StaffSummaryResponse;
import com.psms.entity.User;
import com.psms.enums.ApplicationStatus;
import com.psms.exception.BusinessException;
import com.psms.exception.InvalidStatusTransitionException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.service.AdminApplicationService;
import com.psms.service.DocumentService;
import com.psms.service.ServiceCatalogService;
import com.psms.util.ApplicationStateMachine;
import com.psms.util.PaginationInfo;
import com.psms.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * MVC controller render Thymeleaf pages cho cổng quản trị admin.
 *
 * <p>Pattern: mỗi handler chỉ chuẩn bị data cho Model, business logic ở service.
 * PRG pattern: POST → redirect GET để tránh double-submit.
 * Controller chỉ phụ thuộc vào Service, không inject Repository trực tiếp.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER','SUPER_ADMIN')")
public class AdminViewController {

    private final AdminApplicationService adminApplicationService;
    private final ServiceCatalogService serviceCatalogService;
    private final DocumentService documentService;

    private static final String DEFAULT_PAGE_SIZE_STR = "20";

    // ─── GET /admin/dashboard ────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        DashboardStatsResponse stats = adminApplicationService.getDashboardStats();
        List<AdminApplicationResponse> recentApps = adminApplicationService.getRecentPendingApplications();

        model.addAttribute("stats", stats);
        model.addAttribute("recentApps", recentApps);
        model.addAttribute("activePage", "dashboard");
        return "admin/dashboard";
    }

    // ─── GET /admin/applications ─────────────────────────────────────────────

    @GetMapping("/applications")
    public String applicationList(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) Long serviceTypeId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size,
            Model model) {

        // Giới hạn page không âm và size trong khoảng 1..100 để tránh PageRequest.of(...) ném lỗi
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 100);

        Page<AdminApplicationResponse> applications = adminApplicationService
                .findAll(status, serviceTypeId, staffId, from, to, safePage, safeSize);

        PaginationInfo pageInfo = PaginationUtils.calculate(applications);

        model.addAttribute("applications", applications);
        model.addAttribute("allStatuses", ApplicationStatus.values());

        List<ServiceTypeResponse> serviceTypes = serviceCatalogService.findAllServiceTypesForFilter();
        model.addAttribute("serviceTypes", serviceTypes);
        // Filter params để giữ lại khi chuyển trang
        model.addAttribute("status", status);
        model.addAttribute("serviceTypeId", serviceTypeId);
        model.addAttribute("staffId", staffId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        // Pagination vars
        model.addAttribute("pageStart", pageInfo.pageStart());
        model.addAttribute("pageEnd", pageInfo.pageEnd());
        model.addAttribute("displayFrom", pageInfo.displayFrom());
        model.addAttribute("displayTo", pageInfo.displayTo());
        model.addAttribute("activePage", "applications");
        return "admin/application-list";
    }

    // ─── GET /admin/applications/{id} ────────────────────────────────────────

    @GetMapping("/applications/{id}")
    public String applicationDetail(@PathVariable Long id, Model model) {
        AdminApplicationResponse app = adminApplicationService.findById(id);

        Set<ApplicationStatus> allowedTransitions =
                ApplicationStateMachine.getAllowedTransitions(app.getStatus());

        List<StaffSummaryResponse> availableStaff =
                adminApplicationService.findAvailableStaffByDepartment(app.getDepartmentId());

        model.addAttribute("app", app);
        model.addAttribute("allowedTransitions", allowedTransitions);
        model.addAttribute("availableStaff", availableStaff);
        model.addAttribute("activePage", "applications");
        return "admin/application-detail";
    }

    // ─── POST /admin/applications/{id}/status ────────────────────────────────

    /**
     * Cập nhật trạng thái — PRG pattern.
     * State machine check ở service layer, MVC chỉ xử lý HTTP + flash.
     */
    @PostMapping("/applications/{id}/status")
    public String updateStatus(
            @PathVariable Long id,
            @RequestParam ApplicationStatus newStatus,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal User actingUser,
            RedirectAttributes ra) {

        try {
            UpdateStatusRequest request = UpdateStatusRequest.builder()
                    .newStatus(newStatus)
                    .notes(notes)
                    .build();
            adminApplicationService.updateStatus(id, request, actingUser);
            ra.addFlashAttribute("success", "Cập nhật trạng thái thành công: " + newStatus.getLabel());
        } catch (InvalidStatusTransitionException | BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (ResourceNotFoundException ex) {
            ra.addFlashAttribute("error", "Hồ sơ không tồn tại");
            return "redirect:/admin/applications";
        }
        return "redirect:/admin/applications/" + id;
    }

    // ─── POST /admin/applications/{id}/assign ────────────────────────────────

    /**
     * Phân công cán bộ — chỉ MANAGER / SUPER_ADMIN.
     */
    @PostMapping("/applications/{id}/assign")
    @PreAuthorize("hasAnyRole('MANAGER','SUPER_ADMIN')")
    public String assignStaff(
            @PathVariable Long id,
            @RequestParam Long staffId,
            RedirectAttributes ra) {

        try {
            AssignStaffRequest request = AssignStaffRequest.builder().staffId(staffId).build();
            adminApplicationService.assignStaff(id, request);
            ra.addFlashAttribute("success", "Phân công cán bộ thành công");
        } catch (BusinessException | ResourceNotFoundException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/applications/" + id;
    }

    // ─── POST /admin/applications/{id}/documents ──────────────────────────────

    /**
     * Admin upload tài liệu phản hồi cho hồ sơ.
     *
     * <p>Dùng /admin/** (MVC chain, session-based) thay vì /api/admin/** (REST chain, JWT-only)
     * để browser có thể submit multipart form trực tiếp.
     */
    @PostMapping(value = "/applications/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadResponseDocuments(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files,
            RedirectAttributes ra) {

        try {
            documentService.uploadResponseDocuments(id, files, user);
            ra.addFlashAttribute("success", "Upload tài liệu phản hồi thành công");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/applications/" + id;
    }

    // ─── POST /admin/applications/{id}/documents/{docId}/delete ──────────────

    /**
     * Admin xóa tài liệu (soft delete).
     *
     * <p>Business rules:
     * <ul>
     *   <li>STAFF — chỉ xóa tài liệu phản hồi (is_response=true)</li>
     *   <li>MANAGER / SUPER_ADMIN — xóa bất kỳ tài liệu nào</li>
     *   <li>Xóa mềm: is_deleted=true, file vật lý vẫn giữ</li>
     * </ul>
     */
    @PostMapping("/applications/{id}/documents/{docId}/delete")
    public String deleteDocument(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long docId,
            RedirectAttributes ra) {

        try {
            documentService.deleteDocument(id, docId, user);
            ra.addFlashAttribute("success", "Đã xóa tài liệu thành công.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/applications/" + id;
    }

    // ─── Exception handler ────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/admin/applications";
    }
}

