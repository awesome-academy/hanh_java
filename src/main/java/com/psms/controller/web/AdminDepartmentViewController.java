package com.psms.controller.web;

import com.psms.dto.request.CreateDepartmentRequest;
import com.psms.dto.request.UpdateDepartmentRequest;
import com.psms.dto.response.AdminDepartmentResponse;
import com.psms.dto.response.ApiResponse;
import com.psms.entity.User;
import com.psms.enums.RoleName;
import com.psms.repository.UserRepository;
import com.psms.service.AdminDepartmentService;
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
 * MVC controller trang quản lý phòng ban (SUPER_ADMIN).
 * <ul>
 *   <li>GET /admin/departments — SSR: render danh sách + filter + modal</li>
 *   <li>POST/PUT/DELETE — AJAX JSON: tạo/sửa/xóa không reload trang</li>
 * </ul>
 */
@Slf4j
@Controller
@RequestMapping("/admin/departments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminDepartmentViewController {

    private final AdminDepartmentService adminDepartmentService;
    private final UserRepository userRepository;

    private static final String DEFAULT_PAGE_SIZE_STR = "10";

    // ─── GET — SSR page ───────────────────────────────────────────────────────

    @Operation(summary = "Trang danh sách phòng ban (SSR)")
    @GetMapping
    public String departmentList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_STR) int size,
            Model model) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 100);

        Page<AdminDepartmentResponse> departments =
                adminDepartmentService.findAll(keyword, isActive, safePage, safeSize);
        PaginationInfo pageInfo = PaginationUtils.calculate(departments);

        // Danh sách MANAGER để chọn trưởng phòng trong modal
        List<User> managers = userRepository.findAllByRoleName(RoleName.MANAGER);

        model.addAttribute("departments", departments);
        model.addAttribute("managers", managers);
        model.addAttribute("keyword", keyword);
        model.addAttribute("isActive", isActive);
        model.addAttribute("pageStart", pageInfo.pageStart());
        model.addAttribute("pageEnd", pageInfo.pageEnd());
        model.addAttribute("displayFrom", pageInfo.displayFrom());
        model.addAttribute("displayTo", pageInfo.displayTo());
        model.addAttribute("activePage", "departments");
        return "admin/department-list";
    }

    // ─── POST — Create ────────────────────────────────────────────────────────

    @Operation(summary = "Tạo phòng ban mới (AJAX JSON)")
    @ResponseBody
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AdminDepartmentResponse>> createDepartment(
            @RequestBody @Valid CreateDepartmentRequest request) {
        AdminDepartmentResponse created = adminDepartmentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo phòng ban thành công", created));
    }

    // ─── PUT /{id} — Update ───────────────────────────────────────────────────

    @Operation(summary = "Cập nhật phòng ban (AJAX JSON)")
    @ResponseBody
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AdminDepartmentResponse>> updateDepartment(
            @PathVariable Long id,
            @RequestBody @Valid UpdateDepartmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thành công",
                adminDepartmentService.update(id, request)));
    }

    // ─── DELETE /{id} — Delete ────────────────────────────────────────────────

    @Operation(summary = "Xóa phòng ban (AJAX JSON)")
    @ResponseBody
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable Long id) {
        adminDepartmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa phòng ban", null));
    }
}

