package com.psms.service;

import com.psms.dto.request.CreateDepartmentRequest;
import com.psms.dto.request.UpdateDepartmentRequest;
import com.psms.dto.response.AdminDepartmentResponse;
import com.psms.entity.Department;
import com.psms.entity.User;
import com.psms.enums.ActionType;
import com.psms.exception.BusinessException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.repository.DepartmentRepository;
import com.psms.repository.StaffRepository;
import com.psms.repository.UserRepository;
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
 * Service quản lý phòng ban — CRUD cho admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminDepartmentService {

    private final DepartmentRepository departmentRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;

    // ─── List ──────────────────────────────────────────────────────────────────

    /**
     * Danh sách phòng ban có filter + phân trang.
     * - Chỉ thực hiện 3 query cho mỗi page (1 query lấy phòng ban, 2 query batch đếm staff/service),
     * không phát sinh N+1 query khi map sang DTO.
     * - Nếu không có phòng ban nào, sẽ không thực hiện query đếm (tránh lỗi IN() rỗng).
     */
    public Page<AdminDepartmentResponse> findAll(String keyword, Boolean isActive, int page, int size) {

        // Khởi tạo một Specification<Department> rỗng, sau đó AND thêm các filter nếu có
        Specification<Department> spec = (root, q, cb) -> null;
        if (keyword != null && !keyword.isBlank()) spec = spec.and(keywordLike(keyword));
        if (isActive != null)                      spec = spec.and(hasActive(isActive));

        // 1 query — @EntityGraph eager- lấy danh sách phòng ban theo filter và phân trang, sắp xếp theo tên.
        Page<Department> deptPage = departmentRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")));

        // Lấy danh sách id của các phòng ban trong trang hiện tại.
        List<Long> ids = deptPage.getContent().stream().map(Department::getId).toList();
        // Đếm Đếm số cán bộ theo từng phòng ban trong 1 query duy nhất, trả về Map<departmentId, staffCount>
        Map<Long, Long> staffCountMap = ids.isEmpty() ? Map.of() :
                staffRepository.countByDepartmentIdIn(ids)
                        .stream().collect(Collectors.toMap(
                                r -> (Long) r[0],
                                r -> (Long) r[1]));
        //Đếm số dịch vụ theo từng phòng ban trong 1 query duy nhất, trả về Map<departmentId, serviceCount>
        Map<Long, Long> serviceCountMap = ids.isEmpty() ? Map.of() :
                departmentRepository.countServicesByDepartmentIdIn(ids)
                        .stream().collect(Collectors.toMap(
                                r -> (Long) r[0],
                                r -> (Long) r[1]));

        // Map kết quả sang DTO -> Trả về Page<AdminDepartmentResponse>
        return deptPage.map(dept -> mapToResponse(dept,
                staffCountMap.getOrDefault(dept.getId(), 0L),
                serviceCountMap.getOrDefault(dept.getId(), 0L)));
    }

    // ─── Get by ID ─────────────────────────────────────────────────────────────

    public AdminDepartmentResponse findById(Long id) {
        return departmentRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban", id));
    }

    // ─── Create ────────────────────────────────────────────────────────────────

    /**
     * Tạo phòng ban mới.
     *
     * @throws BusinessException nếu code đã tồn tại
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.CREATE_DEPT,
        entityType = "departments",
        entityIdSpEL = "#result.id",
        description = "'Tạo mới phòng ban ' + #result.code + ' — ' + #result.name")
    @Transactional
    public AdminDepartmentResponse create(CreateDepartmentRequest request) {
        if (departmentRepository.existsByCode(request.getCode())) {
            throw new BusinessException("Mã phòng ban đã tồn tại: " + request.getCode());
        }

        Department dept = new Department();
        dept.setCode(request.getCode().trim().toUpperCase());
        dept.setName(request.getName().trim());
        dept.setAddress(request.getAddress());
        dept.setPhone(request.getPhone());
        dept.setEmail(request.getEmail());
        dept.setActive(true);

        if (request.getLeaderId() != null) {
            validUserManager(dept, request.getLeaderId());
        }

        departmentRepository.save(dept);
        log.info("Admin created department: code={}, name={}", dept.getCode(), dept.getName());
        return mapToResponse(dept);
    }

    private void validUserManager(Department dept, Long leaderId) {
        User leader = userRepository.findById(leaderId)
            .orElseThrow(() -> new ResourceNotFoundException("Trưởng phòng không tồn tại: id=" + leaderId));
        boolean isManager = leader.getRoles().stream()
            .anyMatch(role -> role.getName() == com.psms.enums.RoleName.MANAGER);
        if (!isManager) {
            throw new BusinessException("Chỉ có thể chọn user có vai trò 'MANAGER' làm trưởng phòng.");
        }
        dept.setLeader(leader);
    }


    // ─── Update ────────────────────────────────────────────────────────────────

    /**
     * Cập nhật phòng ban (không được đổi code).
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.UPDATE_DEPT,
        entityType = "departments",
        entityIdSpEL = "#p0",
        description = "'Cập nhật phòng ban ' + #result.code + ' — ' + #result.name")
    @Transactional
    public AdminDepartmentResponse update(Long id, UpdateDepartmentRequest request) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban", id));

        dept.setName(request.getName().trim());
        dept.setAddress(request.getAddress());
        dept.setPhone(request.getPhone());
        dept.setEmail(request.getEmail());

        if (request.getLeaderId() != null) {
            validUserManager(dept, request.getLeaderId());
        } else {
            // leaderId = null → xóa trưởng phòng
            dept.setLeader(null);
        }

        departmentRepository.save(dept);
        log.info("Admin updated department: id={}", id);
        return mapToResponse(dept);
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    /**
     * Xóa phòng ban.
     *
     * <p><strong>Block delete:</strong> không cho xóa nếu còn cán bộ đang thuộc phòng ban
     * để tránh mất thông tin liên kết staff-department.
     *
     * @throws BusinessException nếu còn cán bộ trong phòng ban
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.DELETE_DEPT,
        entityType = "departments",
        entityIdSpEL = "#p0",
        description = "'Xóa phòng ban #' + #p0")
    @Transactional
    public void delete(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban", id));

        long staffCount = staffRepository.countByDepartmentId(id);
        if (staffCount > 0) {
            throw new BusinessException(
                    "Không thể xóa phòng ban \"" + dept.getName() + "\" vì còn "
                    + staffCount + " cán bộ đang thuộc phòng ban này. Hãy chuyển cán bộ trước.");
        }

        long  serviceCount = departmentRepository.countServicesByDepartmentId(id);
        if (serviceCount > 0) {
            throw new BusinessException(
                    "Không thể xóa phòng ban \"" + dept.getName() + "\" vì còn "
                    + serviceCount + " dịch vụ đang thuộc phòng ban này. Hãy chuyển dịch vụ trước.");
        }
        departmentRepository.delete(dept);
        log.info("Admin deleted department: id={}, code={}", id, dept.getCode());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /** Dùng cho findAll() — staffCount và serviceCount đã được batch-load trước, không query thêm */
    private AdminDepartmentResponse mapToResponse(Department dept, long staffCount, long serviceCount) {
        AdminDepartmentResponse dto = AdminDepartmentResponse.builder()
                .id(dept.getId())
                .code(dept.getCode())
                .name(dept.getName())
                .address(dept.getAddress())
                .phone(dept.getPhone())
                .email(dept.getEmail())
                .active(dept.isActive())
                .staffCount(staffCount)
                .serviceCount(serviceCount)
                .build();

        if (dept.getLeader() != null) {
            dto.setLeaderId(dept.getLeader().getId());
            dto.setLeaderName(dept.getLeader().getFullName());
            dto.setLeaderEmail(dept.getLeader().getEmail());
        }
        return dto;
    }

    /** Dùng cho findById(), create(), update() — load counts riêng lẻ */
    private AdminDepartmentResponse mapToResponse(Department dept) {
        return mapToResponse(dept,
                staffRepository.countByDepartmentId(dept.getId()),
                departmentRepository.countServicesByDepartmentId(dept.getId()));
    }

    // ─── Specifications ────────────────────────────────────────────────────────

    private static Specification<Department> keywordLike(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String kw = "%" + keyword.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("name")), kw);
    }

    private static Specification<Department> hasActive(Boolean isActive) {
        if (isActive == null) return null;
        return (root, q, cb) -> cb.equal(root.get("isActive"), isActive);
    }
}

