package com.psms.controller.web;

import com.psms.dto.response.ServiceCategoryResponse;
import com.psms.dto.response.ServiceTypeDetailResponse;
import com.psms.dto.response.ServiceTypeResponse;
import com.psms.exception.ResourceNotFoundException;
import com.psms.service.ServiceCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * MVC controller — render Thymeleaf pages cho cổng công dân.
 *
 * <p>Tất cả handler chỉ chuẩn bị data cho Model rồi trả tên template.
 * Business logic nằm trong {@link ServiceCatalogService}.
 */
@Controller
@RequiredArgsConstructor
public class ClientViewController {

    private final ServiceCatalogService serviceCatalogService;

    // ─── GET / ─────────────────────────────────────────────────

    @Operation(summary = "Trang chủ công dân", description = "Hero section + category grid + 5 DV phổ biến")
    @GetMapping("/")
    public String home(Model model) {
        List<ServiceCategoryResponse> categories = serviceCatalogService.findAllActiveCategories();
        List<ServiceTypeResponse> popularServices = serviceCatalogService.findPopularServices();
        long totalServices = serviceCatalogService.countActiveServices();

        model.addAttribute("categories", categories);
        model.addAttribute("popularServices", popularServices);
        model.addAttribute("totalServices", totalServices);
        model.addAttribute("activeNav", "home");
        return "client/home";
    }

    // ───  GET /services ──────────────────────────────────────────

    @Operation(
        summary = "Danh sách dịch vụ công",
        description = "Filter keyword + categoryId, phân trang, giữ filter params khi chuyển trang"
    )
    @GetMapping("/services")
    public String serviceList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        Page<ServiceTypeResponse> services = serviceCatalogService.searchServices(keyword, categoryId, page, size);
        List<ServiceCategoryResponse> categories = serviceCatalogService.findAllActiveCategories();

        // Tính sẵn các giá trị phân trang — tránh dùng T(Math) trong Thymeleaf
        // vì Spring Boot 3+ chặn truy cập class Java tĩnh từ SpEL trong template
        int pageStart   = Math.max(0, services.getNumber() - 2);
        int pageEnd     = Math.min(services.getTotalPages() - 1, services.getNumber() + 2);
        long displayFrom = (long) services.getNumber() * services.getSize() + 1;
        long displayTo   = Math.min(
                (long) services.getNumber() * services.getSize() + services.getNumberOfElements(),
                services.getTotalElements()
        );

        model.addAttribute("services", services);
        model.addAttribute("categories", categories);
        // Giữ filter params để Thymeleaf xây URL pagination đúng
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        // Biến phân trang
        model.addAttribute("pageStart", pageStart);
        model.addAttribute("pageEnd", pageEnd);
        model.addAttribute("displayFrom", displayFrom);
        model.addAttribute("displayTo", displayTo);
        model.addAttribute("activeNav", "services");
        return "client/service-list";
    }

    // ─── #05-06: GET /services/{id} ─────────────────────────────────────

    @Operation(
        summary = "Chi tiết dịch vụ công",
        description = "Tên, mô tả, yêu cầu hồ sơ, thời hạn, lệ phí, phòng ban. Button 'Nộp hồ sơ ngay'"
    )
    @GetMapping("/services/{id}")
    public String serviceDetail(@PathVariable Long id, Model model) {
        ServiceTypeDetailResponse service = serviceCatalogService.findServiceById(id);

        model.addAttribute("service", service);
        model.addAttribute("activeNav", "services");
        return "client/service-detail";
    }

    // ─── Exception handler (MVC scope) ──────────────────────────────────

    /**
     * Khi dịch vụ không tồn tại (throw từ service layer),
     * redirect về trang danh sách với flash error message thay vì hiện trang trắng.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/services";
    }
}

