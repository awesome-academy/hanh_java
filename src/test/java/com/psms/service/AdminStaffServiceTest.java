package com.psms.service;

import com.psms.dto.response.AdminStaffResponse;
import com.psms.entity.Department;
import com.psms.entity.Staff;
import com.psms.entity.User;
import com.psms.repository.ApplicationRepository;
import com.psms.repository.DepartmentRepository;
import com.psms.repository.StaffRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit test cho AdminStaffService.
 *
 * <p>Covers:
 * <ul>
 *   <li>findAll(): batch workload mapping theo userId, empty-page guard, workloadClass tiers, DTO fields</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminStaffService")
class AdminStaffServiceTest {

    @Mock StaffRepository      staffRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ApplicationRepository applicationRepository;

    @InjectMocks AdminStaffService service;

    /** Helper: tránh lỗi type-inference với List.of(new Object[]{...}) */
    private static List<Object[]> rows(Object[]... data) {
        List<Object[]> list = new java.util.ArrayList<>();
        for (Object[] row : data) list.add(row);
        return list;
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private Staff makeStaff(Long staffId, Long userId, Long deptId) {
        User user = new User();
        user.setId(userId);
        user.setFullName("Cán bộ " + userId);
        user.setEmail("staff" + userId + "@example.com");
        user.setPhone("09" + userId);

        Department dept = new Department();
        dept.setId(deptId);
        dept.setName("Phòng " + deptId);

        Staff staff = new Staff();
        staff.setId(staffId);
        staff.setStaffCode("CB-00" + staffId);
        staff.setPosition("Chuyên viên");
        staff.setAvailable(true);
        staff.setUser(user);
        staff.setDepartment(dept);
        return staff;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // findAll()
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAll()")
    class FindAllTests {

        @Test
        @DisplayName("Trang rỗng → không gọi batch count query (guard ids.isEmpty)")
        void findAll_emptyPage_noBatchQuery() {
            given(staffRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(Page.empty());

            Page<AdminStaffResponse> result = service.findAll(null, null, 0, 10);

            assertThat(result.getContent()).isEmpty();
            verify(applicationRepository, never())
                    .countActiveByAssignedStaffIdIn(any(), any());
        }

        @Test
        @DisplayName("Có cán bộ → batch count gọi đúng 1 lần với đúng userIds")
        void findAll_withStaff_callsBatchCountOnce() {
            Staff s1 = makeStaff(1L, 10L, 100L);
            Staff s2 = makeStaff(2L, 20L, 100L);
            given(staffRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(s1, s2)));
            given(applicationRepository.countActiveByAssignedStaffIdIn(any(), any()))
                    .willReturn(rows(new Object[]{10L, 3L}, new Object[]{20L, 6L}));

            service.findAll(null, null, 0, 10);

            verify(applicationRepository, times(1))
                    .countActiveByAssignedStaffIdIn(eq(List.of(10L, 20L)), any());
        }

        @Test
        @DisplayName("activeCount được map đúng vào từng DTO theo userId (không phải staffId)")
        void findAll_activeCountMappedCorrectlyByUserId() {
            Staff s1 = makeStaff(1L, 10L, 100L);
            Staff s2 = makeStaff(2L, 20L, 100L);
            given(staffRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(s1, s2)));
            given(applicationRepository.countActiveByAssignedStaffIdIn(any(), any()))
                    .willReturn(rows(new Object[]{10L, 3L}, new Object[]{20L, 8L}));

            Page<AdminStaffResponse> result = service.findAll(null, null, 0, 10);

            AdminStaffResponse dto1 = result.getContent().stream()
                    .filter(d -> d.getUserId().equals(10L)).findFirst().orElseThrow();
            AdminStaffResponse dto2 = result.getContent().stream()
                    .filter(d -> d.getUserId().equals(20L)).findFirst().orElseThrow();

            assertThat(dto1.getActiveApplicationCount()).isEqualTo(3L);
            assertThat(dto2.getActiveApplicationCount()).isEqualTo(8L);
        }

        @Test
        @DisplayName("Cán bộ vắng trong batch result → activeCount mặc định = 0")
        void findAll_staffAbsentFromBatchResult_defaultsToZero() {
            Staff s = makeStaff(1L, 10L, 100L);
            given(staffRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(s)));
            given(applicationRepository.countActiveByAssignedStaffIdIn(any(), any()))
                    .willReturn(List.of()); // rỗng → ID 10L vắng

            Page<AdminStaffResponse> result = service.findAll(null, null, 0, 10);

            assertThat(result.getContent().get(0).getActiveApplicationCount()).isZero();
        }

        @Test
        @DisplayName("workloadClass: 0–4 → p-green, 5–7 → p-amber, ≥8 → p-red")
        void findAll_workloadClassTiersCorrect() {
            Staff s1 = makeStaff(1L, 10L, 100L); // 4 apps → green (giữa 0-4)
            Staff s2 = makeStaff(2L, 20L, 100L); // 5 apps → amber (5-7)
            Staff s3 = makeStaff(3L, 30L, 100L); // 8 apps → red  (≥8)
            given(staffRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(s1, s2, s3)));
            given(applicationRepository.countActiveByAssignedStaffIdIn(any(), any()))
                    .willReturn(rows(
                            new Object[]{10L, 4L},
                            new Object[]{20L, 5L},
                            new Object[]{30L, 8L}
                    ));

            Page<AdminStaffResponse> result = service.findAll(null, null, 0, 10);

            assertThat(result.getContent().stream()
                    .filter(d -> d.getUserId().equals(10L)).findFirst().orElseThrow()
                    .getWorkloadClass()).isEqualTo("p-green");
            assertThat(result.getContent().stream()
                    .filter(d -> d.getUserId().equals(20L)).findFirst().orElseThrow()
                    .getWorkloadClass()).isEqualTo("p-amber");
            assertThat(result.getContent().stream()
                    .filter(d -> d.getUserId().equals(30L)).findFirst().orElseThrow()
                    .getWorkloadClass()).isEqualTo("p-red");
        }

        @Test
        @DisplayName("workloadClass boundary: 7 apps → p-amber, 8 apps → p-red")
        void findAll_workloadClassBoundaryValues() {
            Staff s1 = makeStaff(1L, 10L, 100L); // 7 apps → amber (max của tier)
            Staff s2 = makeStaff(2L, 20L, 100L); // 8 apps → red   (min của tier)
            given(staffRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(s1, s2)));
            given(applicationRepository.countActiveByAssignedStaffIdIn(any(), any()))
                    .willReturn(rows(
                            new Object[]{10L, 7L},
                            new Object[]{20L, 8L}
                    ));

            Page<AdminStaffResponse> result = service.findAll(null, null, 0, 10);

            assertThat(result.getContent().stream()
                    .filter(d -> d.getUserId().equals(10L)).findFirst().orElseThrow()
                    .getWorkloadClass()).isEqualTo("p-amber");
            assertThat(result.getContent().stream()
                    .filter(d -> d.getUserId().equals(20L)).findFirst().orElseThrow()
                    .getWorkloadClass()).isEqualTo("p-red");
        }

        @Test
        @DisplayName("DTO chứa đúng metadata: staffCode, fullName, email, departmentName, available, staffId, userId")
        void findAll_dtoFieldsMappedCorrectly() {
            Staff staff = makeStaff(1L, 10L, 100L);
            given(staffRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(staff)));
            given(applicationRepository.countActiveByAssignedStaffIdIn(any(), any()))
                    .willReturn(List.of());

            AdminStaffResponse dto = service.findAll(null, null, 0, 10)
                    .getContent().get(0);

            assertThat(dto.getStaffId()).isEqualTo(1L);
            assertThat(dto.getUserId()).isEqualTo(10L);
            assertThat(dto.getStaffCode()).isEqualTo("CB-001");
            assertThat(dto.getFullName()).isEqualTo("Cán bộ 10");
            assertThat(dto.getEmail()).isEqualTo("staff10@example.com");
            assertThat(dto.getDepartmentId()).isEqualTo(100L);
            assertThat(dto.getDepartmentName()).isEqualTo("Phòng 100");
            assertThat(dto.getPosition()).isEqualTo("Chuyên viên");
            assertThat(dto.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Nhiều cán bộ → Page trả về đúng kích thước")
        void findAll_returnedPageHasCorrectSize() {
            List<Staff> staffList = List.of(
                    makeStaff(1L, 10L, 100L),
                    makeStaff(2L, 20L, 100L),
                    makeStaff(3L, 30L, 200L)
            );
            given(staffRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(staffList));
            given(applicationRepository.countActiveByAssignedStaffIdIn(any(), any()))
                    .willReturn(List.of());

            Page<AdminStaffResponse> result = service.findAll(null, null, 0, 10);

            assertThat(result.getContent()).hasSize(3);
        }
    }
}

