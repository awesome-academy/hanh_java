package com.psms.controller.web;

import com.psms.dto.request.CreateServiceTypeRequest;
import com.psms.dto.request.UpdateServiceTypeRequest;
import com.psms.dto.response.AdminServiceTypeResponse;
import com.psms.dto.response.ApiResponse;
import com.psms.entity.Department;
import com.psms.repository.DepartmentRepository;
import com.psms.repository.ServiceCategoryRepository;
import com.psms.service.AdminServiceTypeService;
import com.psms.util.PaginationInfo;
import com.psms.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MVC controller trang quản lý dịch vụ công (SUPER_ADMIN).
 * <ul>
 *   <li>GET /admin/services — SSR: render danh sách + filter + modal</li>
 *   <li>POST/PUT/DELETE — AJAX JSON: tạo/sửa/xóa/toggle không reload trang</li>
 * </ul>
 */
@Slf4j
@Controller
@RequestMapping("/admin/services")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminServiceViewController {

    private final AdminServiceTypeService adminServiceTypeService;
    private final ServiceCategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;

    private static final String DEFAULT_PAGE_SIZE_STR = "10";

    // ─── GET /admin/services — SSR page ──────────────────────────────────────

    @Operation(summary = "Trang danh sách dịch vụ công (SSR)")
    @GetMapping
    public String serviceList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size,
            Model model) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(100, Math.max(size, 1));

        Page<AdminServiceTypeResponse> services =
                adminServiceTypeService.findAll(keyword, categoryId, isActive, safePage, safeSize);
        PaginationInfo pageInfo = PaginationUtils.calculate(services);
        List<Department> departments = departmentRepository.findAllByOrderByNameAsc();

        model.addAttribute("services", services);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("departments", departments);
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("isActive", isActive);
        model.addAttribute("pageStart", pageInfo.pageStart());
        model.addAttribute("pageEnd", pageInfo.pageEnd());
        model.addAttribute("displayFrom", pageInfo.displayFrom());
        model.addAttribute("displayTo", pageInfo.displayTo());
        model.addAttribute("activePage", "services");
        return "admin/service-list";
    }

    // ─── POST — Create ────────────────────────────────────────────────────────

    @Operation(summary = "Tạo dịch vụ mới (AJAX JSON)")
    @ResponseBody
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AdminServiceTypeResponse>> createService(
            @RequestBody @Valid CreateServiceTypeRequest request) {
        AdminServiceTypeResponse created = adminServiceTypeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo dịch vụ thành công", created));
    }

    // ─── PUT /{id} — Update ───────────────────────────────────────────────────

    @Operation(summary = "Cập nhật dịch vụ (AJAX JSON)")
    @ResponseBody
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AdminServiceTypeResponse>> updateService(
            @PathVariable Long id,
            @RequestBody @Valid UpdateServiceTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thành công",
                adminServiceTypeService.update(id, request)));
    }

    // ─── PUT /{id}/toggle — Toggle active ────────────────────────────────────

    @Operation(summary = "Bật/tắt dịch vụ (AJAX JSON)")
    @ResponseBody
    @PutMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<AdminServiceTypeResponse>> toggleService(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã thay đổi trạng thái",
                adminServiceTypeService.toggleActive(id)));
    }

    // ─── DELETE /{id} — Delete ────────────────────────────────────────────────

    @Operation(summary = "Xóa dịch vụ (AJAX JSON)")
    @ResponseBody
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteService(@PathVariable Long id) {
        adminServiceTypeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa dịch vụ", null));
    }
}

