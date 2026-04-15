package com.psms.service;

import com.psms.dto.request.UpdateStaffRequest;
import com.psms.dto.response.AdminStaffResponse;
import com.psms.entity.Department;
import com.psms.entity.Staff;
import com.psms.enums.ApplicationStatus;
import com.psms.exception.ResourceNotFoundException;
import com.psms.repository.ApplicationRepository;
import com.psms.repository.DepartmentRepository;
import com.psms.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service quản lý cán bộ — MANAGER và SUPER_ADMIN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
public class AdminStaffService {

    /** Các trạng thái hồ sơ đang xử lý — dùng để tính workload của cán bộ */
    static final List<ApplicationStatus> ACTIVE_STATUSES = List.of(
            ApplicationStatus.SUBMITTED,
            ApplicationStatus.RECEIVED,
            ApplicationStatus.PROCESSING,
            ApplicationStatus.ADDITIONAL_REQUIRED
    );

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationRepository applicationRepository;

    // ─── List ──────────────────────────────────────────────────────────────────

    /**
     * Danh sách cán bộ có filter + phân trang.
     *
     * @param departmentId filter theo phòng ban (null = tất cả)
     * @param isAvailable  filter theo trạng thái sẵn sàng (null = tất cả)
     * @param page         trang (0-based)
     * @param size         kích thước trang
     */
    public Page<AdminStaffResponse> findAll(Long departmentId, Boolean isAvailable, int page, int size) {
        // Khởi tạo một Specification rỗng, sau đó AND thêm các filter nếu có
        Specification<Staff> spec = (root, q, cb) -> null;
        if (departmentId != null) spec = spec.and(hasDepartment(departmentId));
        if (isAvailable != null)  spec = spec.and(hasAvailable(isAvailable));

        // 1 query — @EntityGraph eager-fetch user + department (tránh lazy N+1)
        Page<Staff> staffPage = staffRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));

        // 1 batch COUNT query thay cho N queries — guard empty để tránh lỗi IN()
        List<Long> userIds = staffPage.getContent().stream()
                .map(s -> s.getUser().getId()).toList();
        Map<Long, Long> activeCountMap = userIds.isEmpty() ? Map.of() :
                applicationRepository.countActiveByAssignedStaffIdIn(userIds, ACTIVE_STATUSES)
                        .stream().collect(Collectors.toMap(
                                r -> (Long) r[0],
                                r -> (Long) r[1]));

        // Map in-memory — 0 extra queries
        return staffPage.map(staff ->
                mapToResponse(staff, activeCountMap.getOrDefault(staff.getUser().getId(), 0L)));
    }

    // ─── Get by ID ─────────────────────────────────────────────────────────────

    public AdminStaffResponse findById(Long id) {
        return staffRepository.findWithDetailsById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cán bộ", id));
    }

    // ─── Update ────────────────────────────────────────────────────────────────

    /**
     * Cập nhật thông tin cán bộ: chuyển phòng ban, đổi chức vụ, thay đổi trạng thái.
     */
    @Transactional
    public AdminStaffResponse update(Long id, UpdateStaffRequest request) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cán bộ", id));
        Department dept = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban", request.getDepartmentId()));

        staff.setDepartment(dept);
        staff.setPosition(request.getPosition());
        staff.setAvailable(request.isAvailable());
        staffRepository.save(staff);

        log.info("Admin updated staff: id={}, dept={}, available={}", id, dept.getName(), request.isAvailable());
        return mapToResponse(staff);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /** Dùng cho findAll() — activeCount đã được batch-load trước, không query thêm */
    private AdminStaffResponse mapToResponse(Staff staff, long activeCount) {
        return AdminStaffResponse.builder()
                .staffId(staff.getId())
                .userId(staff.getUser().getId())
                .staffCode(staff.getStaffCode())
                .fullName(staff.getUser().getFullName())
                .email(staff.getUser().getEmail())
                .phone(staff.getUser().getPhone())
                .departmentId(staff.getDepartment() != null ? staff.getDepartment().getId() : null)
                .departmentName(staff.getDepartment() != null ? staff.getDepartment().getName() : null)
                .position(staff.getPosition())
                .available(staff.isAvailable())
                .activeApplicationCount(activeCount)
                .build();
    }

    /** Dùng cho findById(), update() — load count riêng lẻ */
    private AdminStaffResponse mapToResponse(Staff staff) {
        return mapToResponse(staff,
                applicationRepository.countByAssignedStaffIdAndStatusIn(
                        staff.getUser().getId(), ACTIVE_STATUSES));
    }

    // ─── Specifications ────────────────────────────────────────────────────────

    private static Specification<Staff> hasDepartment(Long departmentId) {
        if (departmentId == null) return null;
        return (root, q, cb) -> cb.equal(root.get("department").get("id"), departmentId);
    }

    private static Specification<Staff> hasAvailable(Boolean isAvailable) {
        if (isAvailable == null) return null;
        return (root, q, cb) -> cb.equal(root.get("isAvailable"), isAvailable);
    }
}

