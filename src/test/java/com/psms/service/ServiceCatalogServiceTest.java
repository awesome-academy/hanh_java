package com.psms.service;

import com.psms.dto.response.ServiceCategoryResponse;
import com.psms.dto.response.ServiceTypeDetailResponse;
import com.psms.dto.response.ServiceTypeResponse;
import com.psms.entity.ServiceCategory;
import com.psms.entity.ServiceType;
import com.psms.exception.ResourceNotFoundException;
import com.psms.mapper.ServiceTypeMapper;
import com.psms.repository.ServiceCategoryRepository;
import com.psms.repository.ServiceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit test cho {@link ServiceCatalogService}.
 *
 * <p>Dùng Mockito thuần — không load Spring context, không cần DB.
 * Covers tasks: #05-01, #05-02, #05-03 (service layer).
 */
@ExtendWith(MockitoExtension.class)
class ServiceCatalogServiceTest {

    @Mock
    ServiceCategoryRepository categoryRepository;

    @Mock
    ServiceTypeRepository serviceTypeRepository;

    @Mock
    ServiceTypeMapper serviceTypeMapper;

    @InjectMocks
    ServiceCatalogService serviceCatalogService;

    // ─── Fixtures ──────────────────────────────────────────────────────

    private ServiceCategory buildCategory(Integer id, String code, String name, short sortOrder) {
        ServiceCategory cat = new ServiceCategory();
        cat.setId(id);
        cat.setCode(code);
        cat.setName(name);
        cat.setSortOrder(sortOrder);
        cat.setActive(true);
        return cat;
    }

    private ServiceType buildServiceType(Long id, String name) {
        ServiceType st = new ServiceType();
        // Dùng reflection-free approach: set qua setter
        // ServiceType extend AuditableLongEntity nên dùng getId() từ parent
        return st;
    }

    // ─── #05-01: findAllActiveCategories ──────────────────────────────

    @Nested
    @DisplayName("findAllActiveCategories()")
    class FindAllActiveCategories {

        @Test
        @DisplayName("Trả list category đúng thứ tự sort_order, kèm serviceCount")
        void returnsActiveCategoriesWithServiceCount() {
            // Given
            ServiceCategory cat1 = buildCategory(1, "HC", "Hành chính", (short) 1);
            ServiceCategory cat2 = buildCategory(2, "DT", "Đầu tư", (short) 2);

            given(categoryRepository.findAllByIsActiveTrueOrderBySortOrderAsc())
                    .willReturn(List.of(cat1, cat2));

            // countByCategory_IdAndIsActiveTrue trả 3 cho cat1, 0 cho cat2
            given(serviceTypeRepository.countByCategory_IdAndIsActiveTrue(1)).willReturn(3L);
            given(serviceTypeRepository.countByCategory_IdAndIsActiveTrue(2)).willReturn(0L);

            // When
            List<ServiceCategoryResponse> result = serviceCatalogService.findAllActiveCategories();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getCode()).isEqualTo("HC");
            assertThat(result.get(0).getServiceCount()).isEqualTo(3L);
            assertThat(result.get(1).getCode()).isEqualTo("DT");
            assertThat(result.get(1).getServiceCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Trả list rỗng khi không có category active nào")
        void returnsEmptyListWhenNoCategoriesActive() {
            given(categoryRepository.findAllByIsActiveTrueOrderBySortOrderAsc())
                    .willReturn(List.of());

            List<ServiceCategoryResponse> result = serviceCatalogService.findAllActiveCategories();

            assertThat(result).isEmpty();
        }
    }

    // ─── #05-02: searchServices ────────────────────────────────────────

    @Nested
    @DisplayName("searchServices()")
    class SearchServices {

        @Test
        @DisplayName("Keyword hợp lệ → truyền nguyên xuống repository")
        void validKeywordPassedToRepository() {
            Page<ServiceType> repoPage = new PageImpl<>(List.of());
            given(serviceTypeRepository.searchActive(eq("cấp phép"), eq(1), any(Pageable.class)))
                    .willReturn(repoPage);

            Page<ServiceTypeResponse> result = serviceCatalogService.searchServices("cấp phép", 1, 0, 10);

            assertThat(result).isNotNull();
            verify(serviceTypeRepository).searchActive(eq("cấp phép"), eq(1), any(Pageable.class));
        }

        @Test
        @DisplayName("Keyword blank (spaces only) → normalize thành null trước khi query")
        void blankKeywordNormalizedToNull() {
            Page<ServiceType> repoPage = new PageImpl<>(List.of());
            // keyword bị trim → null, nên repo phải nhận null
            given(serviceTypeRepository.searchActive(isNull(), isNull(), any(Pageable.class)))
                    .willReturn(repoPage);

            Page<ServiceTypeResponse> result = serviceCatalogService.searchServices("   ", null, 0, 10);

            assertThat(result).isNotNull();
            verify(serviceTypeRepository).searchActive(isNull(), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("Keyword null → cũng truyền null xuống repository (không filter)")
        void nullKeywordPassedAsNull() {
            Page<ServiceType> repoPage = new PageImpl<>(List.of());
            given(serviceTypeRepository.searchActive(isNull(), isNull(), any(Pageable.class)))
                    .willReturn(repoPage);

            serviceCatalogService.searchServices(null, null, 0, 10);

            verify(serviceTypeRepository).searchActive(isNull(), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("Keyword có trailing spaces → được trim trước khi gửi")
        void keywordWithTrailingSpacesIsTrimmed() {
            Page<ServiceType> repoPage = new PageImpl<>(List.of());
            given(serviceTypeRepository.searchActive(eq("khai sinh"), isNull(), any(Pageable.class)))
                    .willReturn(repoPage);

            serviceCatalogService.searchServices("  khai sinh  ", null, 0, 10);

            verify(serviceTypeRepository).searchActive(eq("khai sinh"), isNull(), any(Pageable.class));
        }
    }

    // ─── #05-03: findServiceById ───────────────────────────────────────

    @Nested
    @DisplayName("findServiceById()")
    class FindServiceById {

        @Test
        @DisplayName("ID tồn tại và active → trả ServiceTypeDetailResponse")
        void existingActiveServiceReturnsDetail() {
            ServiceType st = new ServiceType();
            ServiceTypeDetailResponse expected = ServiceTypeDetailResponse.builder()
                    .id(42L)
                    .code("HC-001")
                    .name("Cấp phép xây dựng")
                    .fee(BigDecimal.ZERO)
                    .active(true)
                    .build();

            given(serviceTypeRepository.findByIdAndIsActiveTrue(42L)).willReturn(Optional.of(st));
            given(serviceTypeMapper.toDetailResponse(st)).willReturn(expected);

            ServiceTypeDetailResponse result = serviceCatalogService.findServiceById(42L);

            assertThat(result.getId()).isEqualTo(42L);
            assertThat(result.getCode()).isEqualTo("HC-001");
        }

        @Test
        @DisplayName("ID không tồn tại → throw ResourceNotFoundException")
        void nonExistentIdThrowsResourceNotFoundException() {
            given(serviceTypeRepository.findByIdAndIsActiveTrue(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> serviceCatalogService.findServiceById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("không tồn tại");
        }

        @Test
        @DisplayName("DV đã bị tắt (is_active=false) → repository trả empty → throw ResourceNotFoundException")
        void inactiveServiceThrowsResourceNotFoundException() {
            // Repository chỉ trả DV active — đã filter bằng findByIdAndIsActiveTrue
            given(serviceTypeRepository.findByIdAndIsActiveTrue(10L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> serviceCatalogService.findServiceById(10L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── findPopularServices ───────────────────────────────────────────

    @Nested
    @DisplayName("findPopularServices()")
    class FindPopularServices {

        @Test
        @DisplayName("Trả list DV (có thể ít hơn 5 nếu DB ít dữ liệu)")
        void returnsUpToFiveServices() {
            ServiceType st1 = new ServiceType();
            ServiceType st2 = new ServiceType();
            ServiceTypeResponse r1 = ServiceTypeResponse.builder().id(1L).name("DV A").build();
            ServiceTypeResponse r2 = ServiceTypeResponse.builder().id(2L).name("DV B").build();

            given(serviceTypeRepository.findTopActiveServices(PageRequest.of(0, 5)))
                    .willReturn(List.of(st1, st2));
            given(serviceTypeMapper.toResponse(st1)).willReturn(r1);
            given(serviceTypeMapper.toResponse(st2)).willReturn(r2);

            List<ServiceTypeResponse> result = serviceCatalogService.findPopularServices();

            assertThat(result).hasSize(2)
                    .extracting(ServiceTypeResponse::getName)
                    .containsExactly("DV A", "DV B");
        }
    }

    // ─── countActiveServices ───────────────────────────────────────────

    @Nested
    @DisplayName("countActiveServices()")
    class CountActiveServices {

        @Test
        @DisplayName("Trả đúng số lượng DV active từ repository")
        void delegatesToRepository() {
            given(serviceTypeRepository.countByIsActiveTrue()).willReturn(42L);

            long count = serviceCatalogService.countActiveServices();

            assertThat(count).isEqualTo(42L);
        }
    }
}

