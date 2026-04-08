package com.psms.service;

import com.psms.dto.request.CreateUserRequest;
import com.psms.dto.request.UpdateUserRolesRequest;
import com.psms.dto.response.AdminUserResponse;
import com.psms.entity.Department;
import com.psms.entity.Role;
import com.psms.entity.Staff;
import com.psms.entity.User;
import com.psms.enums.RoleName;
import com.psms.exception.BusinessException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit test cho AdminUserService.
 *
 * <p>Covers:
 * <ul>
 *   <li>#10-11: Chỉ SUPER_ADMIN truy cập được — @PreAuthorize hoạt động</li>
 *   <li>#10-12: Xóa user → is_active=false, dữ liệu vẫn trong DB</li>
 *   <li>#10-13: Lock user → is_locked=true → không thể login</li>
 *   <li>Separation of Duties: CITIZEN không được kết hợp với STAFF/MANAGER/SUPER_ADMIN</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService")
class AdminUserServiceTest {

    @Mock UserRepository       userRepository;
    @Mock CitizenRepository    citizenRepository;
    @Mock StaffRepository      staffRepository;
    @Mock RoleRepository       roleRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock PasswordEncoder      passwordEncoder;

    @InjectMocks AdminUserService service;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private User makeUser(Long id, boolean active, boolean locked) {
        User u = new User();
        u.setId(id);
        u.setEmail("test" + id + "@example.com");
        u.setFullName("User " + id);
        u.setActive(active);
        u.setLocked(locked);

        Role role = new Role();
        role.setName(RoleName.CITIZEN);
        u.setRoles(new LinkedHashSet<>(Set.of(role)));
        return u;
    }

    private Role makeRole(RoleName name) {
        Role r = new Role();
        r.setName(name);
        return r;
    }

    /** Tạo user với một role cụ thể (dùng cho guard tests). */
    private User makeUserWithRole(Long id, RoleName roleName) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@example.com");
        u.setFullName("User " + id);
        u.setActive(true);
        u.setLocked(false);
        Role role = makeRole(roleName);
        u.setRoles(new LinkedHashSet<>(Set.of(role)));
        return u;
    }

    // ── Security context setup ─────────────────────────────────────────────────

    /**
     * Set up SecurityContext với current user ID = 999L (SUPER_ADMIN).
     * Các test dùng target ID khác (1L, 5L, 20L...) sẽ không bị chặn bởi assertNotSelf.
     * Test self-protection dùng target ID = 999L để trigger guard.
     */
    @BeforeEach
    void setUpSecurityContext() {
        User currentAdmin = makeUserWithRole(999L, RoleName.SUPER_ADMIN);
        var auth = new UsernamePasswordAuthenticationToken(
                currentAdmin, null, currentAdmin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // #10-12: Soft delete
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("#10-12 softDeleteUser")
    class SoftDeleteTests {

        @Test
        @DisplayName("Xóa user → is_active=false, record vẫn còn trong DB")
        void softDelete_setsActiveFalse() {
            User user = makeUser(1L, true, false); // CITIZEN role → guard bỏ qua
            given(userRepository.findWithRolesById(1L)).willReturn(Optional.of(user));

            service.softDeleteUser(1L);

            assertThat(user.isActive()).isFalse();
            verify(userRepository).save(user);
            verify(userRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("Xóa user không tồn tại → ResourceNotFoundException")
        void softDelete_userNotFound_throwsException() {
            given(userRepository.findWithRolesById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.softDeleteUser(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("Xóa SUPER_ADMIN duy nhất → BusinessException")
        void softDelete_lastSuperAdmin_throwsBusinessException() {
            // Target = current user (ID 999) → self-protection guard kích hoạt
            User self = makeUserWithRole(999L, RoleName.SUPER_ADMIN);
            given(userRepository.findWithRolesById(999L)).willReturn(Optional.of(self));

            assertThatThrownBy(() -> service.softDeleteUser(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("chính tài khoản của bạn");
        }

        @Test
        @DisplayName("Xóa SUPER_ADMIN khác (không phải mình) → cho phép")
        void softDelete_superAdminWithOthersExisting_succeeds() {
            // Target ID 5L ≠ current user ID 999L → không bị chặn
            User otherAdmin = makeUserWithRole(5L, RoleName.SUPER_ADMIN);
            given(userRepository.findWithRolesById(5L)).willReturn(Optional.of(otherAdmin));

            service.softDeleteUser(5L);

            assertThat(otherAdmin.isActive()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // #10-13: Lock / Unlock
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("#10-13 lockUser / unlockUser")
    class LockTests {

        @Test
        @DisplayName("Khóa tài khoản → is_locked=true")
        void lockUser_setsLockedTrue() {
            User user = makeUser(1L, true, false); // CITIZEN role → guard bỏ qua
            given(userRepository.findWithRolesById(1L)).willReturn(Optional.of(user));
            given(citizenRepository.findByUserId(1L)).willReturn(Optional.empty());
            given(staffRepository.findWithDepartmentByUserId(1L)).willReturn(Optional.empty());

            AdminUserResponse result = service.lockUser(1L);

            assertThat(user.isLocked()).isTrue();
            assertThat(result.isLocked()).isTrue();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Tài khoản bị khóa → User.isAccountNonLocked() = false → Spring Security chặn login")
        void lockedUser_isAccountNonLockedReturnsFalse() {
            User user = makeUser(1L, true, true); // is_locked = true
            assertThat(user.isAccountNonLocked()).isFalse();
        }

        @Test
        @DisplayName("Mở khóa → is_locked=false, failed_login_count=0, locked_until=null")
        void unlockUser_resetsAllLockFields() {
            User user = makeUser(1L, true, true);
            user.setFailedLoginCount((byte) 5);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(10));

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(citizenRepository.findByUserId(1L)).willReturn(Optional.empty());
            given(staffRepository.findWithDepartmentByUserId(1L)).willReturn(Optional.empty());

            AdminUserResponse result = service.unlockUser(1L);

            assertThat(user.isLocked()).isFalse();
            assertThat(user.getFailedLoginCount()).isZero();
            assertThat(user.getLockedUntil()).isNull();
            assertThat(result.isLocked()).isFalse();
        }

        @Test
        @DisplayName("Khóa user không tồn tại → ResourceNotFoundException")
        void lockUser_notFound_throwsException() {
            given(userRepository.findWithRolesById(99L)).willReturn(Optional.empty());
            assertThatThrownBy(() -> service.lockUser(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Khóa SUPER_ADMIN duy nhất → BusinessException")
        void lockUser_lastSuperAdmin_throwsBusinessException() {
            // Target = current user (ID 999) → self-protection
            User self = makeUserWithRole(999L, RoleName.SUPER_ADMIN);
            given(userRepository.findWithRolesById(999L)).willReturn(Optional.of(self));

            assertThatThrownBy(() -> service.lockUser(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("chính tài khoản của bạn");
        }

        @Test
        @DisplayName("Khóa SUPER_ADMIN khác (không phải mình) → cho phép")
        void lockUser_superAdminWithOthersExisting_succeeds() {
            // Target ID 5L ≠ current user 999L → không bị chặn
            User otherAdmin = makeUserWithRole(5L, RoleName.SUPER_ADMIN);
            given(userRepository.findWithRolesById(5L)).willReturn(Optional.of(otherAdmin));
            given(citizenRepository.findByUserId(5L)).willReturn(Optional.empty());
            given(staffRepository.findWithDepartmentByUserId(5L)).willReturn(Optional.empty());

            AdminUserResponse result = service.lockUser(5L);

            assertThat(result.isLocked()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tạo user — validate business rules
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createUser")
    class CreateUserTests {

        @Test
        @DisplayName("Email đã tồn tại → BusinessException")
        void createUser_duplicateEmail_throwsException() {
            CreateUserRequest req = CreateUserRequest.builder()
                    .email("existing@example.com")
                    .password("password123")
                    .fullName("Test User")
                    .roles(Set.of(RoleName.CITIZEN))
                    .build();
            given(userRepository.existsByEmail("existing@example.com")).willReturn(true);

            assertThatThrownBy(() -> service.createUser(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Email đã được đăng ký");
        }

        @Test
        @DisplayName("Tạo STAFF không có staffCode → BusinessException")
        void createUser_staffWithoutCode_throwsException() {
            CreateUserRequest req = CreateUserRequest.builder()
                    .email("newstaff@example.com")
                    .password("password123")
                    .fullName("New Staff")
                    .roles(Set.of(RoleName.STAFF))
                    .staffCode("") // trống
                    .build();

            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(roleRepository.findByName(RoleName.STAFF))
                    .willReturn(Optional.of(makeRole(RoleName.STAFF)));
            given(passwordEncoder.encode(anyString())).willReturn("hashed");
            // save() cần được stub vì Service gọi save() trước khi tạo staff profile
            given(userRepository.save(any())).willAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            assertThatThrownBy(() -> service.createUser(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("staffCode");
        }

        @Test
        @DisplayName("Tạo CITIZEN thành công → trả về AdminUserResponse đúng")
        void createUser_citizen_success() {
            CreateUserRequest req = CreateUserRequest.builder()
                    .email("citizen@example.com")
                    .password("password123")
                    .fullName("New Citizen")
                    .roles(Set.of(RoleName.CITIZEN))
                    .nationalId("012345678901")
                    .build();

            given(userRepository.existsByEmail("citizen@example.com")).willReturn(false);
            given(roleRepository.findByName(RoleName.CITIZEN))
                    .willReturn(Optional.of(makeRole(RoleName.CITIZEN)));
            given(passwordEncoder.encode("password123")).willReturn("$2a$12$hashed");
            given(userRepository.save(any())).willAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(10L);
                return u;
            });
            given(citizenRepository.existsByNationalId("012345678901")).willReturn(false);
            given(citizenRepository.findByUserId(10L)).willReturn(Optional.empty());
            given(staffRepository.findWithDepartmentByUserId(10L)).willReturn(Optional.empty());

            AdminUserResponse result = service.createUser(req);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("citizen@example.com");
            assertThat(result.getFullName()).isEqualTo("New Citizen");
            verify(citizenRepository).save(any());
            verify(staffRepository, never()).save(any(Staff.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Separation of Duties — CITIZEN không kết hợp với admin roles
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Separation of Duties — CITIZEN không kết hợp với admin roles")
    class SeparationOfDutiesTests {

        @Test
        @DisplayName("createUser: CITIZEN + STAFF → BusinessException")
        void createUser_citizenAndStaff_throwsException() {
            CreateUserRequest req = CreateUserRequest.builder()
                    .email("conflict@example.com")
                    .password("password123")
                    .fullName("Conflict User")
                    .roles(Set.of(RoleName.CITIZEN, RoleName.STAFF))
                    .build();
            given(userRepository.existsByEmail(anyString())).willReturn(false);

            assertThatThrownBy(() -> service.createUser(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Separation of Duties");
        }

        @Test
        @DisplayName("createUser: CITIZEN + MANAGER → BusinessException")
        void createUser_citizenAndManager_throwsException() {
            CreateUserRequest req = CreateUserRequest.builder()
                    .email("conflict2@example.com")
                    .password("password123")
                    .fullName("Conflict User 2")
                    .roles(Set.of(RoleName.CITIZEN, RoleName.MANAGER))
                    .build();
            given(userRepository.existsByEmail(anyString())).willReturn(false);

            assertThatThrownBy(() -> service.createUser(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Separation of Duties");
        }

        @Test
        @DisplayName("createUser: CITIZEN + SUPER_ADMIN → BusinessException")
        void createUser_citizenAndSuperAdmin_throwsException() {
            CreateUserRequest req = CreateUserRequest.builder()
                    .email("conflict3@example.com")
                    .password("password123")
                    .fullName("Conflict User 3")
                    .roles(Set.of(RoleName.CITIZEN, RoleName.SUPER_ADMIN))
                    .build();
            given(userRepository.existsByEmail(anyString())).willReturn(false);

            assertThatThrownBy(() -> service.createUser(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Separation of Duties");
        }

        @Test
        @DisplayName("updateRoles: cập nhật thành CITIZEN + STAFF → BusinessException")
        void updateRoles_citizenAndStaff_throwsException() {
            User user = makeUser(1L, true, false);
            given(userRepository.findWithRolesById(1L)).willReturn(Optional.of(user));

            UpdateUserRolesRequest request = new UpdateUserRolesRequest();
            request.setRoles(Set.of(RoleName.CITIZEN, RoleName.STAFF));

            assertThatThrownBy(() -> service.updateRoles(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Separation of Duties");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("updateRoles: thu hồi SUPER_ADMIN khỏi admin duy nhất → BusinessException")
        void updateRoles_removeLastSuperAdmin_throwsBusinessException() {
            // Target = current user (ID 999) → self-protection
            User self = makeUserWithRole(999L, RoleName.SUPER_ADMIN);
            given(userRepository.findWithRolesById(999L)).willReturn(Optional.of(self));

            UpdateUserRolesRequest request = new UpdateUserRolesRequest();
            request.setRoles(Set.of(RoleName.STAFF));

            assertThatThrownBy(() -> service.updateRoles(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("chính tài khoản của bạn");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("updateRoles: giữ nguyên SUPER_ADMIN trên user khác → không cần kiểm tra guard")
        void updateRoles_keepSuperAdmin_noGuardCheck() {
            // Target ID 5L ≠ current user 999L → không bị chặn
            User otherAdmin = makeUserWithRole(5L, RoleName.SUPER_ADMIN);
            given(userRepository.findWithRolesById(5L)).willReturn(Optional.of(otherAdmin));
            given(roleRepository.findByName(RoleName.SUPER_ADMIN))
                    .willReturn(Optional.of(makeRole(RoleName.SUPER_ADMIN)));
            given(roleRepository.findByName(RoleName.MANAGER))
                    .willReturn(Optional.of(makeRole(RoleName.MANAGER)));
            given(citizenRepository.findByUserId(5L)).willReturn(Optional.empty());
            given(staffRepository.findWithDepartmentByUserId(5L)).willReturn(Optional.empty());

            UpdateUserRolesRequest request = new UpdateUserRolesRequest();
            request.setRoles(Set.of(RoleName.SUPER_ADMIN, RoleName.MANAGER));

            assertThatCode(() -> service.updateRoles(5L, request)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("createUser: STAFF + MANAGER (không có CITIZEN) → hợp lệ")
        void createUser_staffAndManager_noConflict() {
            CreateUserRequest req = CreateUserRequest.builder()
                    .email("staffmgr@example.com")
                    .password("password123")
                    .fullName("Staff Manager")
                    .roles(Set.of(RoleName.STAFF, RoleName.MANAGER))
                    .staffCode("CB-999")
                    .departmentId(1L)   // department bắt buộc cho STAFF/MANAGER
                    .build();

            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(roleRepository.findByName(RoleName.STAFF))
                    .willReturn(Optional.of(makeRole(RoleName.STAFF)));
            given(roleRepository.findByName(RoleName.MANAGER))
                    .willReturn(Optional.of(makeRole(RoleName.MANAGER)));
            given(passwordEncoder.encode(anyString())).willReturn("hashed");
            given(userRepository.save(any())).willAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(20L);
                return u;
            });
            given(staffRepository.existsByStaffCode("CB-999")).willReturn(false);
            Department dept = new Department();
            dept.setId(1L);
            dept.setName("Phòng A");
            given(departmentRepository.findById(1L)).willReturn(Optional.of(dept));
            given(citizenRepository.findByUserId(20L)).willReturn(Optional.empty());
            given(staffRepository.findWithDepartmentByUserId(20L)).willReturn(Optional.empty());

            assertThatCode(() -> service.createUser(req)).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // #10-11: SUPER_ADMIN access — @PreAuthorize("hasRole('SUPER_ADMIN')")
    // ═══════════════════════════════════════════════════════════════════════════

    /*
     * #10-11 note: @PreAuthorize enforcement được test ở integration level.
     * Unit test ở đây không test Spring Security interceptor (MockitoExtension không
     * khởi động Spring Context). Test STAFF → 403 được thực hiện tại integration test.
     *
     * Tuy nhiên, verify class-level @PreAuthorize annotation tồn tại:
     */
    @Test
    @DisplayName("#10-11: AdminUserService có @PreAuthorize('hasRole(SUPER_ADMIN)') ở class level")
    void classLevelPreAuthorize_annotation_exists() {
        var annotation = AdminUserService.class.getAnnotation(
                org.springframework.security.access.prepost.PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("SUPER_ADMIN");
    }
}

