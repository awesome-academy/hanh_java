package com.psms.service;

import com.psms.dto.response.AdminDepartmentResponse;
import com.psms.entity.Department;
import com.psms.entity.User;
import com.psms.exception.BusinessException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.repository.DepartmentRepository;
import com.psms.repository.StaffRepository;
import com.psms.repository.UserRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit test cho AdminDepartmentService.
 *
 * <p>Covers:
 * <ul>
 *   <li>findAll(): batch staffCount + serviceCount mapping, empty-page guard, leader mapping</li>
 *   <li>delete(): blocked when staff exist, allowed when dept is empty</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDepartmentService")
class AdminDepartmentServiceTest {

    @Mock DepartmentRepository departmentRepository;
    @Mock StaffRepository      staffRepository;
    @Mock UserRepository       userRepository;

    @InjectMocks AdminDepartmentService service;

    /** Helper: tránh lỗi type-inference với List.of(new Object[]{...}) */
    private static List<Object[]> rows(Object[]... data) {
        List<Object[]> list = new java.util.ArrayList<>();
        for (Object[] row : data) list.add(row);
        return list;
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private Department makeDept(Long id, String code, String name) {
        Department dept = new Department();
        dept.setId(id);
        dept.setCode(code);
        dept.setName(name);
        dept.setActive(true);
        return dept;
    }

    private Department makeDeptWithLeader(Long id, String code, String name) {
        Department dept = makeDept(id, code, name);
        User leader = new User();
        leader.setId(100L);
        leader.setFullName("Nguyễn Văn A");
        leader.setEmail("leader@example.com");
        dept.setLeader(leader);
        return dept;
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
            given(departmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(Page.empty());

            Page<AdminDepartmentResponse> result = service.findAll(null, null, 0, 10);

            assertThat(result.getContent()).isEmpty();
            verify(staffRepository, never()).countByDepartmentIdIn(any());
            verify(departmentRepository, never()).countServicesByDepartmentIdIn(any());
        }

        @Test
        @DisplayName("Có phòng ban → gọi đúng 2 batch queries (staff + service) với đúng IDs")
        void findAll_withDepts_callsBothBatchCountsOnce() {
            Department d1 = makeDept(1L, "PB-001", "Phòng A");
            Department d2 = makeDept(2L, "PB-002", "Phòng B");
            given(departmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(d1, d2)));
            List<Object[]> staffCounts = rows(new Object[]{1L, 5L}, new Object[]{2L, 2L});
            List<Object[]> svcCounts   = rows(new Object[]{1L, 3L});
            given(staffRepository.countByDepartmentIdIn(any())).willReturn(staffCounts);
            given(departmentRepository.countServicesByDepartmentIdIn(any())).willReturn(svcCounts);

            service.findAll(null, null, 0, 10);

            verify(staffRepository, times(1)).countByDepartmentIdIn(eq(List.of(1L, 2L)));
            verify(departmentRepository, times(1)).countServicesByDepartmentIdIn(eq(List.of(1L, 2L)));
        }

        @Test
        @DisplayName("staffCount + serviceCount được map đúng theo departmentId")
        void findAll_countsMapCorrectly() {
            Department d1 = makeDept(1L, "PB-001", "Phòng A");
            Department d2 = makeDept(2L, "PB-002", "Phòng B");
            given(departmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(d1, d2)));
            List<Object[]> staffCounts = rows(new Object[]{1L, 4L}, new Object[]{2L, 1L});
            // d2 vắng trong batch service → default 0
            List<Object[]> svcCounts = rows(new Object[]{1L, 6L});
            given(staffRepository.countByDepartmentIdIn(any())).willReturn(staffCounts);
            given(departmentRepository.countServicesByDepartmentIdIn(any())).willReturn(svcCounts);

            Page<AdminDepartmentResponse> result = service.findAll(null, null, 0, 10);

            AdminDepartmentResponse dto1 = result.getContent().stream()
                    .filter(d -> d.getId().equals(1L)).findFirst().orElseThrow();
            AdminDepartmentResponse dto2 = result.getContent().stream()
                    .filter(d -> d.getId().equals(2L)).findFirst().orElseThrow();

            assertThat(dto1.getStaffCount()).isEqualTo(4L);
            assertThat(dto1.getServiceCount()).isEqualTo(6L);
            assertThat(dto2.getStaffCount()).isEqualTo(1L);
            assertThat(dto2.getServiceCount()).isZero(); // vắng trong batch → default 0
        }

        @Test
        @DisplayName("Phòng ban vắng trong cả 2 batch result → staffCount=0, serviceCount=0")
        void findAll_deptAbsentFromBothBatches_defaultsToZero() {
            Department dept = makeDept(99L, "PB-099", "Phòng Mới");
            given(departmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(dept)));
            given(staffRepository.countByDepartmentIdIn(any())).willReturn(List.of());
            given(departmentRepository.countServicesByDepartmentIdIn(any())).willReturn(List.of());

            AdminDepartmentResponse dto = service.findAll(null, null, 0, 10)
                    .getContent().get(0);

            assertThat(dto.getStaffCount()).isZero();
            assertThat(dto.getServiceCount()).isZero();
        }

        @Test
        @DisplayName("Phòng ban có trưởng phòng → leaderId, leaderName, leaderEmail được map đúng")
        void findAll_deptWithLeader_leaderFieldsMapped() {
            Department dept = makeDeptWithLeader(1L, "PB-001", "Phòng A");
            given(departmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(dept)));
            given(staffRepository.countByDepartmentIdIn(any())).willReturn(List.of());
            given(departmentRepository.countServicesByDepartmentIdIn(any())).willReturn(List.of());

            AdminDepartmentResponse dto = service.findAll(null, null, 0, 10)
                    .getContent().get(0);

            assertThat(dto.getLeaderId()).isEqualTo(100L);
            assertThat(dto.getLeaderName()).isEqualTo("Nguyễn Văn A");
            assertThat(dto.getLeaderEmail()).isEqualTo("leader@example.com");
        }

        @Test
        @DisplayName("Phòng ban không có trưởng phòng → leaderId/leaderName/leaderEmail = null")
        void findAll_deptWithoutLeader_leaderFieldsNull() {
            Department dept = makeDept(1L, "PB-001", "Phòng A"); // không có leader
            given(departmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(dept)));
            given(staffRepository.countByDepartmentIdIn(any())).willReturn(List.of());
            given(departmentRepository.countServicesByDepartmentIdIn(any())).willReturn(List.of());

            AdminDepartmentResponse dto = service.findAll(null, null, 0, 10)
                    .getContent().get(0);

            assertThat(dto.getLeaderId()).isNull();
            assertThat(dto.getLeaderName()).isNull();
            assertThat(dto.getLeaderEmail()).isNull();
        }

        @Test
        @DisplayName("DTO chứa đúng metadata: id, code, name, active")
        void findAll_dtoFieldsMappedCorrectly() {
            Department dept = makeDept(1L, "PB-001", "Phòng Hành chính");
            given(departmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(dept)));
            given(staffRepository.countByDepartmentIdIn(any())).willReturn(List.of());
            given(departmentRepository.countServicesByDepartmentIdIn(any())).willReturn(List.of());

            AdminDepartmentResponse dto = service.findAll(null, null, 0, 10)
                    .getContent().get(0);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getCode()).isEqualTo("PB-001");
            assertThat(dto.getName()).isEqualTo("Phòng Hành chính");
            assertThat(dto.isActive()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // delete()
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("Còn cán bộ trong phòng ban → BusinessException, không gọi delete")
        void delete_withStaff_throwsBusinessException() {
            Department dept = makeDept(1L, "PB-001", "Phòng A");
            given(departmentRepository.findById(1L)).willReturn(Optional.of(dept));
            given(staffRepository.countByDepartmentId(1L)).willReturn(3L);

            assertThatThrownBy(() -> service.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("3 cán bộ");

            verify(departmentRepository, never()).delete(any(Department.class));
        }

        @Test
        @DisplayName("Không còn cán bộ → xóa thành công")
        void delete_noStaff_deletesSuccessfully() {
            Department dept = makeDept(1L, "PB-001", "Phòng A");
            given(departmentRepository.findById(1L)).willReturn(Optional.of(dept));
            given(staffRepository.countByDepartmentId(1L)).willReturn(0L);

            assertThatCode(() -> service.delete(1L)).doesNotThrowAnyException();

            verify(departmentRepository).delete((Department) dept);
        }

        @Test
        @DisplayName("Phòng ban không tồn tại → ResourceNotFoundException, không gọi delete")
        void delete_notFound_throwsResourceNotFoundException() {
            given(departmentRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(departmentRepository, never()).delete(any(Department.class));
        }

        @Test
        @DisplayName("Business message lỗi chứa tên phòng ban và số lượng cán bộ")
        void delete_errorMessageContainsDeptNameAndCount() {
            Department dept = makeDept(1L, "PB-001", "Phòng Tài chính");
            given(departmentRepository.findById(1L)).willReturn(Optional.of(dept));
            given(staffRepository.countByDepartmentId(1L)).willReturn(5L);

            assertThatThrownBy(() -> service.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Phòng Tài chính")
                    .hasMessageContaining("5 cán bộ");
        }
    }
}

