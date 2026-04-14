package com.psms.service;

import com.psms.dto.response.AdminServiceTypeResponse;
import com.psms.entity.Department;
import com.psms.entity.ServiceCategory;
import com.psms.entity.ServiceType;
import com.psms.exception.BusinessException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.repository.ApplicationRepository;
import com.psms.repository.DepartmentRepository;
import com.psms.repository.ServiceCategoryRepository;
import com.psms.repository.ServiceTypeRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit test cho AdminServiceTypeService.
 *
 * <p>Covers:
 * <ul>
 *   <li>findAll(): batch workload mapping, empty-page guard, DTO field mapping</li>
 *   <li>delete(): blocked when active applications exist, allowed when none</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminServiceTypeService")
class AdminServiceTypeServiceTest {

    @Mock ServiceTypeRepository   serviceTypeRepository;
    @Mock ServiceCategoryRepository categoryRepository;
    @Mock DepartmentRepository    departmentRepository;
    @Mock ApplicationRepository   applicationRepository;

    @InjectMocks AdminServiceTypeService service;

    /** Helper: tránh lỗi type-inference với List.of(new Object[]{...}) */
    private static List<Object[]> rows(Object[]... data) {
        List<Object[]> list = new java.util.ArrayList<>();
        for (Object[] row : data) list.add(row);
        return list;
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private ServiceType makeServiceType(Long id, String code, String name) {
        ServiceCategory cat = new ServiceCategory();
        cat.setId(1);
        cat.setName("Lĩnh vực A");

        Department dept = new Department();
        dept.setId(10L);
        dept.setName("Phòng ban A");

        ServiceType svc = new ServiceType();
        svc.setId(id);
        svc.setCode(code);
        svc.setName(name);
        svc.setCategory(cat);
        svc.setDepartment(dept);
        svc.setProcessingTimeDays((short) 5);
        svc.setFee(BigDecimal.ZERO);
        svc.setActive(true);
        return svc;
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
            given(serviceTypeRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(Page.empty());

            Page<AdminServiceTypeResponse> result = service.findAll(null, null, null, 0, 10);

            assertThat(result.getContent()).isEmpty();
            verify(applicationRepository, never())
                    .countActiveByServiceTypeIdIn(any(), any());
        }

        @Test
        @DisplayName("Có dịch vụ → batch count gọi đúng 1 lần với đúng IDs")
        void findAll_withServices_callsBatchCountOnce() {
            ServiceType svc1 = makeServiceType(1L, "DV-001", "Dịch vụ A");
            ServiceType svc2 = makeServiceType(2L, "DV-002", "Dịch vụ B");
            given(serviceTypeRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(svc1, svc2)));
            List<Object[]> batchResult = rows(new Object[]{1L, 3L}, new Object[]{2L, 7L});
            given(applicationRepository.countActiveByServiceTypeIdIn(any(), any()))
                    .willReturn(batchResult);

            service.findAll(null, null, null, 0, 10);

            verify(applicationRepository, times(1))
                    .countActiveByServiceTypeIdIn(eq(List.of(1L, 2L)), any());
        }

        @Test
        @DisplayName("activeCount được map đúng vào từng DTO theo serviceTypeId")
        void findAll_activeCountMappedCorrectly() {
            ServiceType svc1 = makeServiceType(1L, "DV-001", "Dịch vụ A");
            ServiceType svc2 = makeServiceType(2L, "DV-002", "Dịch vụ B");
            given(serviceTypeRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(svc1, svc2)));
            List<Object[]> batchResult = rows(new Object[]{1L, 3L}, new Object[]{2L, 8L});
            given(applicationRepository.countActiveByServiceTypeIdIn(any(), any()))
                    .willReturn(batchResult);

            Page<AdminServiceTypeResponse> result = service.findAll(null, null, null, 0, 10);

            AdminServiceTypeResponse dto1 = result.getContent().stream()
                    .filter(d -> d.getId().equals(1L)).findFirst().orElseThrow();
            AdminServiceTypeResponse dto2 = result.getContent().stream()
                    .filter(d -> d.getId().equals(2L)).findFirst().orElseThrow();

            assertThat(dto1.getActiveApplicationCount()).isEqualTo(3L);
            assertThat(dto2.getActiveApplicationCount()).isEqualTo(8L);
        }

        @Test
        @DisplayName("Dịch vụ không có trong batch result → activeCount mặc định = 0")
        void findAll_serviceAbsentFromBatchResult_defaultsToZero() {
            ServiceType svc = makeServiceType(5L, "DV-005", "Dịch vụ C");
            given(serviceTypeRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(svc)));
            // batch trả về rỗng → ID 5L không có trong kết quả
            given(applicationRepository.countActiveByServiceTypeIdIn(any(), any()))
                    .willReturn(List.of());

            Page<AdminServiceTypeResponse> result = service.findAll(null, null, null, 0, 10);

            assertThat(result.getContent().get(0).getActiveApplicationCount()).isZero();
        }

        @Test
        @DisplayName("DTO chứa đúng các field: name, categoryName, departmentName, active")
        void findAll_dtoFieldsMappedCorrectly() {
            ServiceType svc = makeServiceType(1L, "DV-001", "Dịch vụ A");
            given(serviceTypeRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(svc)));
            given(applicationRepository.countActiveByServiceTypeIdIn(any(), any()))
                    .willReturn(List.of());

            AdminServiceTypeResponse dto = service.findAll(null, null, null, 0, 10)
                    .getContent().get(0);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getCode()).isEqualTo("DV-001");
            assertThat(dto.getName()).isEqualTo("Dịch vụ A");
            assertThat(dto.getCategoryName()).isEqualTo("Lĩnh vực A");
            assertThat(dto.getDepartmentName()).isEqualTo("Phòng ban A");
            assertThat(dto.isActive()).isTrue();
        }

        @Test
        @DisplayName("Nhiều dịch vụ, một phần có active apps → trả về Page đúng kích thước")
        void findAll_returnedPageHasCorrectSize() {
            List<ServiceType> svcs = List.of(
                    makeServiceType(1L, "DV-001", "A"),
                    makeServiceType(2L, "DV-002", "B"),
                    makeServiceType(3L, "DV-003", "C")
            );
            given(serviceTypeRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(svcs));
            List<Object[]> batchResult = rows(new Object[]{1L, 1L});
            given(applicationRepository.countActiveByServiceTypeIdIn(any(), any()))
                    .willReturn(batchResult);

            Page<AdminServiceTypeResponse> result = service.findAll(null, null, null, 0, 10);

            assertThat(result.getContent()).hasSize(3);
            // Chỉ ID=1 có activeCount=1, còn lại = 0
            assertThat(result.getContent().stream()
                    .filter(d -> d.getId().equals(2L)).findFirst().orElseThrow()
                    .getActiveApplicationCount()).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // delete()
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("Dịch vụ đang có hồ sơ active → BusinessException, không gọi delete")
        void delete_withActiveApplications_throwsBusinessException() {
            ServiceType svc = makeServiceType(1L, "DV-001", "Dịch vụ A");
            given(serviceTypeRepository.findById(1L)).willReturn(Optional.of(svc));
            given(applicationRepository.countByServiceTypeIdAndStatusIn(eq(1L), any()))
                    .willReturn(3L);

            assertThatThrownBy(() -> service.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("3 hồ sơ chưa hoàn thành");

            verify(serviceTypeRepository, never()).delete(any(ServiceType.class));
        }

        @Test
        @DisplayName("Dịch vụ không có hồ sơ active → xóa thành công")
        void delete_noActiveApplications_deletesSuccessfully() {
            ServiceType svc = makeServiceType(1L, "DV-001", "Dịch vụ A");
            given(serviceTypeRepository.findById(1L)).willReturn(Optional.of(svc));
            given(applicationRepository.countByServiceTypeIdAndStatusIn(eq(1L), any()))
                    .willReturn(0L);

            assertThatCode(() -> service.delete(1L)).doesNotThrowAnyException();

            verify(serviceTypeRepository).delete((ServiceType) svc);
        }

        @Test
        @DisplayName("Dịch vụ không tồn tại → ResourceNotFoundException, không gọi delete")
        void delete_notFound_throwsResourceNotFoundException() {
            given(serviceTypeRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(serviceTypeRepository, never()).delete(any(ServiceType.class));
        }

        @Test
        @DisplayName("Business message lỗi chứa tên dịch vụ và số lượng hồ sơ")
        void delete_errorMessageContainsServiceNameAndCount() {
            ServiceType svc = makeServiceType(1L, "DV-001", "Cấp phép xây dựng");
            given(serviceTypeRepository.findById(1L)).willReturn(Optional.of(svc));
            given(applicationRepository.countByServiceTypeIdAndStatusIn(eq(1L), any()))
                    .willReturn(5L);

            assertThatThrownBy(() -> service.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cấp phép xây dựng")
                    .hasMessageContaining("5 hồ sơ chưa hoàn thành");
        }
    }
}

