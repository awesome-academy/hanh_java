package com.psms.service;

import com.psms.dto.request.CreateUserRequest;
import com.psms.dto.request.UpdateUserRequest;
import com.psms.dto.request.UpdateUserRolesRequest;
import com.psms.dto.response.AdminUserResponse;
import com.psms.entity.*;
import com.psms.enums.RoleName;
import com.psms.exception.BusinessException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service quản lý người dùng — chỉ SUPER_ADMIN có quyền truy cập.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserService {

    private final UserRepository userRepository;
    private final CitizenRepository citizenRepository;
    private final StaffRepository staffRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    // ─── List ──────────────────────────────────────────────────────────────────

    /**
     * Danh sách user có filter + phân trang.
     *
     * <p>Query strategy: batch-load tất cả profiles trong 4-5 queries cố định,
     * bất kể page size — tránh N+1 (+2N) pattern của .map(mapToResponse):
     * <ol>
     *   <li>findAll(spec, pageable) — 1 query lấy users (+ 1 count query)</li>
     *   <li>findWithRolesByIdIn — 1 query batch roles cho toàn page</li>
     *   <li>findByUserIdIn — 1 query batch citizen profiles</li>
     *   <li>findWithDepartmentByUserIdIn — 1 query batch staff + department</li>
     * </ol>
     */
    public Page<AdminUserResponse> findAll(RoleName role, Boolean isActive, String keyword,
                                           int page, int size) {
        // Khởi tạo một Specification<User> rỗng, sau đó AND thêm các filter nếu có
        Specification<User> spec = (root, q, cb) -> null;
        if (role != null)                          spec = spec.and(UserSpecifications.hasRole(role));
        if (isActive != null)                      spec = spec.and(UserSpecifications.isActive(isActive));
        if (keyword != null && !keyword.isBlank()) spec = spec.and(UserSpecifications.keywordLike(keyword));

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> userPage = userRepository.findAll(spec, pageable);
        if (userPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> userIds = userPage.getContent().stream().map(User::getId).toList();

        // Batch load #1: roles — JOIN FETCH user_roles trong 1 query
        Map<Long, Set<RoleName>> rolesMap = userRepository.findWithRolesByIdIn(userIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> u.getRoles().stream()
                                .map(Role::getName)
                                .collect(Collectors.toCollection(LinkedHashSet::new))
                ));

        // Batch load #2: citizen profiles — WHERE user_id IN (...)
        Map<Long, Citizen> citizenMap = citizenRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(c -> c.getUser().getId(), c -> c));

        // Batch load #3: staff profiles kèm department — JOIN FETCH department trong 1 query
        Map<Long, Staff> staffMap = staffRepository.findWithDepartmentByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        // Map to DTOs từ in-memory maps — zero additional queries
        return userPage.map(user -> mapToResponse(
                user,
                rolesMap.getOrDefault(user.getId(), Set.of()),
                citizenMap.get(user.getId()),
                staffMap.get(user.getId())
        ));
    }

    // ─── Get by ID ─────────────────────────────────────────────────────────────

    /**
     * Chi tiết một user, kèm citizen/staff profile.
     */
    public AdminUserResponse findById(Long id) {
        User user = userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với ID: " + id));
        return mapToResponse(user);
    }

    // ─── Create ────────────────────────────────────────────────────────────────

    /**
     * Tạo tài khoản mới.
     *
     * <p>Flow:
     * <ol>
     *   <li>Validate email unique</li>
     *   <li>Validate role compatibility (CITIZEN không được kết hợp với admin roles)</li>
     *   <li>Tạo User + gán roles</li>
     *   <li>Nếu CITIZEN role → tạo Citizen record (validate CCCD unique)</li>
     *   <li>Nếu STAFF/MANAGER role → tạo Staff record (validate staffCode unique)</li>
     * </ol>
     */
    @Transactional
    public AdminUserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email đã được đăng ký: " + request.getEmail());
        }

        validateRoleCompatibility(request.getRoles());
        Set<Role> roles = resolveRoles(request.getRoles());

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setRoles(roles);
        userRepository.save(user);

        boolean hasCitizenRole = request.getRoles().contains(RoleName.CITIZEN);
        boolean hasStaffRole   = request.getRoles().contains(RoleName.STAFF)
                                  || request.getRoles().contains(RoleName.MANAGER);

        if (hasCitizenRole) {
            createCitizenProfile(user, request);
        }
        if (hasStaffRole) {
            createStaffProfile(user, request);
        }

        log.info("Admin created user: email={}, roles={}", user.getEmail(), request.getRoles());
        return mapToResponse(user);
    }

    // ─── Update ────────────────────────────────────────────────────────────────

    /**
     * Cập nhật thông tin cơ bản (fullName, phone).
     * Không cho phép sửa email, password, roles (endpoint riêng cho roles).
     *
     * <p>Rules:
     * <ul>
     *   <li>CITIZEN và SUPER_ADMIN: chỉ được cập nhật fullName, phone.
     *   <li>STAFF/MANAGER: được cập nhật thêm departmentId, position.</li>
     * </ul>
     */
    @Transactional
    public AdminUserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với ID: " + id));

        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        userRepository.save(user);

        // Validate và cập nhật staff info nếu có cho tài khoản STAFF/MANAGER
        if (request.getDepartmentId() != null || StringUtils.hasText(request.getPosition())) {
            Staff staff = staffRepository.findByUserId(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Không thể cập nhật thông tin phòng ban/chức vụ cho tài khoản này. Chỉ tài khoản STAFF/MANAGER mới có thể cập nhật thông tin này."));
            if (request.getDepartmentId() != null) {
                Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại"));
                staff.setDepartment(dept);
            }
            if (StringUtils.hasText(request.getPosition())) {
                staff.setPosition(request.getPosition());
            }
            staffRepository.save(staff);
        }
        log.info("Admin updated user: id={}", id);
        return mapToResponse(user);
    }

    // ─── Lock / Unlock ─────────────────────────────────────────────────────────

    /**
     * Khóa tài khoản — user không thể đăng nhập (is_locked=true).
     *
     * <p>Guard: SUPER_ADMIN không thể khóa chính mình.
     */
    @Transactional
    public AdminUserResponse lockUser(Long id) {
        User user = userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với ID: " + id));
        assertNotSelf(user, "khóa");
        user.setLocked(true);
        userRepository.save(user);
        log.info("Admin locked user: id={}", id);
        return mapToResponse(user);
    }

    /**
     * Mở khóa tài khoản — reset is_locked, failed_login_count, locked_until.
     */
    @Transactional
    public AdminUserResponse unlockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với ID: " + id));
        user.setLocked(false);
        user.setFailedLoginCount((byte) 0);
        user.setLockedUntil(null);
        userRepository.save(user);
        log.info("Admin unlocked user: id={}", id);
        return mapToResponse(user);
    }

    // ─── Soft Delete ───────────────────────────────────────────────────────────

    /**
     * Xóa mềm tài khoản — is_active=false, dữ liệu hồ sơ vẫn giữ nguyên.
     *
     * <p>Guard: SUPER_ADMIN không thể xóa chính mình.
     */
    @Transactional
    public void softDeleteUser(Long id) {
        User user = userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với ID: " + id));
        assertNotSelf(user, "xóa");
        user.setActive(false);
        userRepository.save(user);
        log.info("Admin soft-deleted user: id={}", id);
    }

    // ─── Update Roles ──────────────────────────────────────────────────────────

    /**
     * Gán / thu hồi role cho user.
     * Thay thế toàn bộ roles hiện tại bằng set mới.
     * Áp dụng validation Separation of Duties: CITIZEN không được kết hợp với admin roles.
     *
     * <p>Guard: SUPER_ADMIN không thể thay đổi roles của chính mình.
     */
    @Transactional
    public AdminUserResponse updateRoles(Long id, UpdateUserRolesRequest request) {
        User user = userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với ID: " + id));
        validateRoleCompatibility(request.getRoles());
        assertNotSelf(user, "thay đổi vai trò của");
        user.setRoles(resolveRoles(request.getRoles()));
        userRepository.save(user);
        log.info("Admin updated roles for user: id={}, newRoles={}", id, request.getRoles());
        return mapToResponse(user);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Guard self-protection: SUPER_ADMIN không thể khóa, xóa hoặc thay đổi
     * roles của chính mình.
     *
     * <p>Lý do: Ngăn vô tình tự khóa/tự xóa bản thân, đảm bảo luôn có
     * ít nhất một SUPER_ADMIN có thể đăng nhập để quản trị hệ thống.
     *
     * @param targetUser user sắp bị tác động
     * @param action     tên hành động (dùng trong thông báo lỗi)
     * @throws BusinessException nếu target là chính người dùng hiện tại
     */
    private void assertNotSelf(User targetUser, String action) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User currentUser
                && currentUser.getId().equals(targetUser.getId())) {
            throw new BusinessException("Không thể " + action + " chính tài khoản của bạn.");
        }
    }

    private void createCitizenProfile(User user, CreateUserRequest request) {
        if (!StringUtils.hasText(request.getNationalId())) {
            throw new BusinessException("Số CCCD không được trống khi tạo tài khoản CITIZEN");
        } else if (citizenRepository.existsByNationalId(request.getNationalId())) {
            throw new BusinessException("Số CCCD đã được sử dụng: " + request.getNationalId());
        }
        Citizen citizen = Citizen.builder()
                .user(user)
                .nationalId(request.getNationalId())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .permanentAddress(request.getPermanentAddress())
                .ward(request.getWard())
                .province(request.getProvince())
                .build();
        citizenRepository.save(citizen);
    }

    private void createStaffProfile(User user, CreateUserRequest request) {
        if (!StringUtils.hasText(request.getStaffCode())) {
            throw new BusinessException("Mã cán bộ (staffCode) không được trống khi tạo tài khoản STAFF/MANAGER");
        }
        if (staffRepository.existsByStaffCode(request.getStaffCode())) {
            throw new BusinessException("Mã cán bộ đã tồn tại: " + request.getStaffCode());
        }

        if (request.getDepartmentId() == null) {
            throw new BusinessException("Phòng ban (department) là bắt buộc khi tạo tài khoản STAFF/MANAGER");
        }
        Department dept = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại: id=" + request.getDepartmentId()));

        Staff staff = new Staff();
        staff.setUser(user);
        staff.setStaffCode(request.getStaffCode());
        staff.setDepartment(dept);
        staff.setPosition(request.getPosition());
        staff.setAvailable(true);
        staffRepository.save(staff);
    }

    /**
     * Resolve Set<RoleName> → Set<Role> entity (từ DB).
     */
    private Set<Role> resolveRoles(Set<RoleName> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new BusinessException("Phải có ít nhất một vai trò");
        }
        return roleNames.stream()
                .map(rn -> roleRepository.findByName(rn)
                        .orElseThrow(() -> new BusinessException("Vai trò không hợp lệ: " + rn)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Separation of Duties: CITIZEN không được kết hợp với STAFF/MANAGER/SUPER_ADMIN.
     *
     * <p>Lý do: Nếu 1 user vừa là CITIZEN vừa là admin role, họ có thể tự nộp hồ sơ
     * rồi tự xử lý/approve — vi phạm nguyên tắc kiểm soát nội bộ cơ bản.
     *
     * @throws BusinessException nếu roles vi phạm quy tắc phân tách
     */
    private void validateRoleCompatibility(Set<RoleName> roles) {
        if (roles == null || roles.isEmpty()) return;
        boolean hasCitizen = roles.contains(RoleName.CITIZEN);
        boolean hasAdminRole = roles.contains(RoleName.STAFF)
                || roles.contains(RoleName.MANAGER)
                || roles.contains(RoleName.SUPER_ADMIN);
        if (hasCitizen && hasAdminRole) {
            throw new BusinessException(
                "Separation of Duties: Vai trò CITIZEN không thể kết hợp với STAFF/MANAGER/SUPER_ADMIN và ngược lại."
            );
        }
    }

    /**
     * Map User entity → AdminUserResponse DTO (single-user path).
     * Dùng cho findById, createUser, updateUser... — queries citizen/staff individually.
     */
    private AdminUserResponse mapToResponse(User user) {
        Set<RoleName> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        AdminUserResponse dto = AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .active(user.isActive())
                .locked(user.isLocked())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .roles(roleNames)
                .citizen(roleNames.contains(RoleName.CITIZEN))
                .build();

        citizenRepository.findByUserId(user.getId()).ifPresent(c ->
                dto.setNationalId(c.getNationalId()));

        staffRepository.findWithDepartmentByUserId(user.getId()).ifPresent(s -> {
            dto.setStaffCode(s.getStaffCode());
            dto.setPosition(s.getPosition());
            if (s.getDepartment() != null) {
                dto.setDepartmentId(s.getDepartment().getId());
                dto.setDepartmentName(s.getDepartment().getName());
            }
        });

        return dto;
    }

    /**
     * Map User entity → AdminUserResponse DTO với pre-loaded profiles (batch-load path).
     * Dùng cho findAll() — nhận citizen/staff đã được batch-load sẵn, không phát sinh thêm query nào.
     *
     * @param user     user entity (roles đã được batch-load vào rolesMap)
     * @param roleNames roles đã resolve từ batch query
     * @param citizen  citizen profile (null nếu không có)
     * @param staff    staff profile kèm department (null nếu không có)
     */
    private AdminUserResponse mapToResponse(User user, Set<RoleName> roleNames,
                                            Citizen citizen, Staff staff) {
        AdminUserResponse dto = AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .active(user.isActive())
                .locked(user.isLocked())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .roles(roleNames)
                .citizen(roleNames.contains(RoleName.CITIZEN))
                .build();

        if (citizen != null) {
            dto.setNationalId(citizen.getNationalId());
        }
        if (staff != null) {
            dto.setStaffCode(staff.getStaffCode());
            dto.setPosition(staff.getPosition());
            if (staff.getDepartment() != null) {
                dto.setDepartmentId(staff.getDepartment().getId());
                dto.setDepartmentName(staff.getDepartment().getName());
            }
        }
        return dto;
    }
}

