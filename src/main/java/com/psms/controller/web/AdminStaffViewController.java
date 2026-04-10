package com.psms.controller.web;

import com.psms.dto.request.UpdateStaffRequest;
import com.psms.dto.response.AdminStaffResponse;
import com.psms.dto.response.ApiResponse;
import com.psms.entity.Department;
import com.psms.repository.DepartmentRepository;
import com.psms.service.AdminStaffService;
import com.psms.util.PaginationInfo;
import com.psms.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MVC controller trang quản lý cán bộ (MANAGER, SUPER_ADMIN).
 * <ul>
 *   <li>GET /admin/staff — SSR: render danh sách + filter + workload badge</li>
 *   <li>PUT — AJAX JSON: cập nhật thông tin cán bộ không reload trang</li>
 * </ul>
 * <p>Gán hồ sơ cho cán bộ thực hiện qua endpoint hiện có:
 * {@code PUT /admin/applications/{id}/assign}
 */
@Slf4j
@Controller
@RequestMapping("/admin/staff")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
public class AdminStaffViewController {

    private final AdminStaffService adminStaffService;
    private final DepartmentRepository departmentRepository;

    private static final String DEFAULT_PAGE_SIZE_STR = "10";

    // ─── GET — SSR page ───────────────────────────────────────────────────────

    @Operation(summary = "Trang danh sách cán bộ (SSR)")
    @GetMapping
    public String staffList(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean isAvailable,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size,
            Model model) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 100);

        Page<AdminStaffResponse> staffPage =
                adminStaffService.findAll(departmentId, isAvailable, safePage, safeSize);
        PaginationInfo pageInfo = PaginationUtils.calculate(staffPage);
        List<Department> departments = departmentRepository.findAllByOrderByNameAsc();

        model.addAttribute("staffPage", staffPage);
        model.addAttribute("departments", departments);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("isAvailable", isAvailable);
        model.addAttribute("pageStart", pageInfo.pageStart());
        model.addAttribute("pageEnd", pageInfo.pageEnd());
        model.addAttribute("displayFrom", pageInfo.displayFrom());
        model.addAttribute("displayTo", pageInfo.displayTo());
        model.addAttribute("activePage", "staff");
        return "admin/staff-list";
    }

    // ─── PUT /{id} — Update staff ─────────────────────────────────────────────

    @Operation(summary = "Cập nhật cán bộ (AJAX JSON)")
    @ResponseBody
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AdminStaffResponse>> updateStaff(
            @PathVariable Long id,
            @RequestBody @Valid UpdateStaffRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thành công",
                adminStaffService.update(id, request)));
    }

}

