package com.psms.controller;

import com.psms.controller.web.ClientViewController;
import com.psms.dto.response.ServiceCategoryResponse;
import com.psms.dto.response.ServiceTypeDetailResponse;
import com.psms.dto.response.ServiceTypeResponse;
import com.psms.exception.ResourceNotFoundException;
import com.psms.service.ServiceCatalogService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit test cho {@link ClientViewController} — MVC controller render Thymeleaf.
 *
 * <p>Kiểm tra: view name trả về đúng, Model có đủ attribute, redirect & flash message hoạt động.
 * Dùng standaloneSetup — không load Spring context, không cần Thymeleaf engine thực.
 *
 * <p>Covers: #05-04, #05-05, #05-06 (MVC controller layer).
 */
@ExtendWith(MockitoExtension.class)
class ClientViewControllerTest {

    @Mock
    ServiceCatalogService serviceCatalogService;

    @InjectMocks
    ClientViewController clientViewController;

    MockMvc mockMvc;

    // Fixtures tái sử dụng
    private final List<ServiceCategoryResponse> sampleCategories = List.of(
            ServiceCategoryResponse.builder().id(1).code("HC").name("Hành chính").serviceCount(5).build(),
            ServiceCategoryResponse.builder().id(2).code("DT").name("Đầu tư").serviceCount(2).build()
    );

    @BeforeEach
    void setup() {
        // ClientViewController tự handle ResourceNotFoundException (@ExceptionHandler),
        // không cần gắn GlobalExceptionHandler ngoài
        mockMvc = MockMvcBuilders
                .standaloneSetup(clientViewController)
                .build();
    }

    // ─── #05-04: GET / — Trang chủ ────────────────────────────────────

    @Nested
    @DisplayName("GET / — home()")
    class Home {

        @Test
        @DisplayName("Render view 'client/home' với 3 model attributes bắt buộc")
        void rendersHomeViewWithModelAttributes() throws Exception {
            List<ServiceTypeResponse> popular = List.of(
                    ServiceTypeResponse.builder().id(1L).name("Cấp CCCD").build(),
                    ServiceTypeResponse.builder().id(2L).name("Khai sinh").build()
            );
            given(serviceCatalogService.findAllActiveCategories()).willReturn(sampleCategories);
            given(serviceCatalogService.findPopularServices()).willReturn(popular);
            given(serviceCatalogService.countActiveServices()).willReturn(42L);

            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("client/home"))
                    .andExpect(model().attributeExists("categories"))
                    .andExpect(model().attributeExists("popularServices"))
                    .andExpect(model().attributeExists("totalServices"))
                    .andExpect(model().attribute("activeNav", "home"))
                    .andExpect(model().attribute("totalServices", 42L));
        }

        @Test
        @DisplayName("Model 'categories' chứa đúng list từ service")
        void modelCategoriesMatchServiceResult() throws Exception {
            given(serviceCatalogService.findAllActiveCategories()).willReturn(sampleCategories);
            given(serviceCatalogService.findPopularServices()).willReturn(List.of());
            given(serviceCatalogService.countActiveServices()).willReturn(0L);

            mockMvc.perform(get("/"))
                    .andExpect(model().attribute("categories", sampleCategories));
        }
    }

    // ─── #05-05: GET /services — Danh sách dịch vụ ────────────────────

    @Nested
    @DisplayName("GET /services — serviceList()")
    class ServiceList {

        @Test
        @DisplayName("Render view 'client/service-list' với đầy đủ model attributes")
        void rendersServiceListView() throws Exception {
            Page<ServiceTypeResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            given(serviceCatalogService.searchServices(isNull(), isNull(), eq(0), eq(10))).willReturn(page);
            given(serviceCatalogService.findAllActiveCategories()).willReturn(sampleCategories);

            mockMvc.perform(get("/services"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("client/service-list"))
                    .andExpect(model().attributeExists("services"))
                    .andExpect(model().attributeExists("categories"))
                    .andExpect(model().attribute("activeNav", "services"));
        }

        @Test
        @DisplayName("Filter params keyword + categoryId được giữ lại trong model để Thymeleaf xây URL pagination")
        void filterParamsKeptInModel() throws Exception {
            Page<ServiceTypeResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            given(serviceCatalogService.searchServices(eq("khai sinh"), eq(2), eq(0), eq(10))).willReturn(page);
            given(serviceCatalogService.findAllActiveCategories()).willReturn(sampleCategories);

            mockMvc.perform(get("/services")
                            .param("keyword", "khai sinh")
                            .param("categoryId", "2"))
                    .andExpect(status().isOk())
                    // keyword và categoryId phải được đưa vào model để Thymeleaf xây pagination URL đúng
                    .andExpect(model().attribute("keyword", "khai sinh"))
                    .andExpect(model().attribute("categoryId", 2));
        }

        @Test
        @DisplayName("Không có filter → keyword và categoryId là null trong model (không vỡ template)")
        void noFilterParamsAreNullInModel() throws Exception {
            Page<ServiceTypeResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            given(serviceCatalogService.searchServices(isNull(), isNull(), eq(0), eq(10))).willReturn(page);
            given(serviceCatalogService.findAllActiveCategories()).willReturn(sampleCategories);

            mockMvc.perform(get("/services"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("keyword", nullValue()))
                    .andExpect(model().attribute("categoryId", nullValue()));
        }

        @Test
        @DisplayName("page=2 size=5 → truyền đúng xuống service")
        void paginationParamsPassedCorrectly() throws Exception {
            Page<ServiceTypeResponse> page = new PageImpl<>(List.of(), PageRequest.of(2, 5), 0);
            given(serviceCatalogService.searchServices(isNull(), isNull(), eq(2), eq(5))).willReturn(page);
            given(serviceCatalogService.findAllActiveCategories()).willReturn(List.of());

            mockMvc.perform(get("/services")
                            .param("page", "2")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("client/service-list"));
        }
    }

    // ─── #05-06: GET /services/{id} — Chi tiết dịch vụ ───────────────

    @Nested
    @DisplayName("GET /services/{id} — serviceDetail()")
    class ServiceDetail {

        @Test
        @DisplayName("DV tồn tại → render view 'client/service-detail' với model 'service'")
        void rendersDetailViewWithService() throws Exception {
            ServiceTypeDetailResponse detail = ServiceTypeDetailResponse.builder()
                    .id(42L)
                    .code("HC-001")
                    .name("Cấp phép xây dựng")
                    .categoryName("Xây dựng")
                    .processingTimeDays((short) 15)
                    .fee(new BigDecimal("500000"))
                    .active(true)
                    .build();

            given(serviceCatalogService.findServiceById(42L)).willReturn(detail);

            mockMvc.perform(get("/services/42"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("client/service-detail"))
                    .andExpect(model().attribute("service", detail))
                    .andExpect(model().attribute("activeNav", "services"));
        }

        @Test
        @DisplayName("DV không tồn tại → redirect về /services với flash error")
        void notFoundServiceRedirectsToListWithFlashError() throws Exception {
            given(serviceCatalogService.findServiceById(999L))
                    .willThrow(new ResourceNotFoundException("Dịch vụ không tồn tại hoặc đã ngừng hoạt động"));

            mockMvc.perform(get("/services/999"))
                    // PRG pattern: redirect về trang list
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/services"))
                    .andExpect(flash().attributeExists("error"));
        }

        @Test
        @DisplayName("DV đã tắt → cùng redirect về /services (không hiện trang trắng)")
        void inactiveServiceAlsoRedirects() throws Exception {
            given(serviceCatalogService.findServiceById(10L))
                    .willThrow(new ResourceNotFoundException("Dịch vụ không tồn tại hoặc đã ngừng hoạt động"));

            mockMvc.perform(get("/services/10"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/services"));
        }
    }
}

