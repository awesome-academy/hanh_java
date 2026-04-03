package com.psms.controller.web;

import com.psms.dto.request.SubmitApplicationRequest;
import com.psms.dto.response.ApplicationDetailResponse;
import com.psms.dto.response.ApplicationResponse;
import com.psms.dto.response.ServiceTypeResponse;
import com.psms.entity.User;
import com.psms.enums.ApplicationStatus;
import com.psms.exception.ResourceNotFoundException;
import com.psms.service.ApplicationService;
import com.psms.service.ServiceCatalogService;
import com.psms.util.PaginationInfo;
import com.psms.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * MVC controller — render Thymeleaf pages quản lý hồ sơ cho citizen.
 *
 * <p>Tất cả route yêu cầu role CITIZEN (enforce bằng @PreAuthorize).
 * Ownership check được đảm bảo bởi ApplicationService — citizen chỉ thấy HS của mình.
 */
@Controller
@RequestMapping("/applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CITIZEN')")
public class ApplicationViewController {

    private final ApplicationService applicationService;
    private final ServiceCatalogService serviceCatalogService;

    private static final String DEFAULT_PAGE_SIZE_STR = "10";

    // ─── GET /applications/submit ──────────────────────────────────────

    /**
     * Form nộp hồ sơ.
     * Dropdown dịch vụ, pre-select nếu có serviceId param
     */
    @GetMapping("/submit")
    public String showSubmitForm(
            @RequestParam(required = false) Long serviceId,
            Model model) {

        List<ServiceTypeResponse> services = serviceCatalogService.findAllActiveServices();

        model.addAttribute("services", services);
        model.addAttribute("preSelectedServiceId", serviceId);
        model.addAttribute("activeNav", "applications");
        return "client/application-submit";
    }

    // ─── POST /applications/submit ─────────────────────────────────────

    /**
     * Nộp hồ sơ (PRG).
     * PRG pattern: submit → redirect /applications + flash
     */
    @PostMapping("/submit")
    public String submitApplication(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long serviceTypeId,
            @RequestParam(required = false) String notes,
            RedirectAttributes ra) {

        User user = (User) userDetails;
        SubmitApplicationRequest request = SubmitApplicationRequest.builder()
                .serviceTypeId(serviceTypeId)
                .notes(notes)
                .build();

        ApplicationResponse response = applicationService.submit(user.getId(), request);
        ra.addFlashAttribute("success",
                "Nộp hồ sơ thành công! Mã hồ sơ: " + response.getApplicationCode());
        return "redirect:/applications";
    }

    // ─── GET /applications ─────────────────────────────────────────────

    /**
     * Danh sách hồ sơ của tôi.
     * Filter status + phân trang
     */
    @GetMapping
    public String listMyApplications(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size,
            Model model) {

        // Giới hạn page không âm và size trong khoảng 1..50 để tránh PageRequest.of(...) ném lỗi
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 50);

        User user = (User) userDetails;
        Page<ApplicationResponse> applications =
            applicationService.findMyApplications(user.getId(), status, safePage, safeSize);

        // Tính sẵn phạm vi trang — tránh dùng T(Math) trong Thymeleaf
        PaginationInfo pageInfo = PaginationUtils.calculate(applications);

        model.addAttribute("applications", applications);
        model.addAttribute("status", status);
        model.addAttribute("allStatuses", ApplicationStatus.values());

        // Biến phân trang
        model.addAttribute("pageStart", pageInfo.pageStart());
        model.addAttribute("pageEnd", pageInfo.pageEnd());
        model.addAttribute("displayFrom", pageInfo.displayFrom());
        model.addAttribute("displayTo", pageInfo.displayTo());

        model.addAttribute("activeNav", "applications");
        return "client/application-list";
    }

    // ─── GET /applications/{id} ────────────────────────────────────────

    @GetMapping("/{id}")
    public String showApplicationDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            Model model) {

        User user = (User) userDetails;
        ApplicationDetailResponse detail = applicationService.findMyApplicationById(user.getId(), id);

        model.addAttribute("appDetail", detail);
        model.addAttribute("activeNav", "applications");
        return "client/application-detail";
    }

    // ─── Exception handler (MVC scope) ────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/applications";
    }
}
