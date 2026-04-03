package com.psms.controller;

import com.psms.controller.client.ServiceCatalogController;
import com.psms.dto.response.ServiceCategoryResponse;
import com.psms.dto.response.ServiceTypeDetailResponse;
import com.psms.dto.response.ServiceTypeResponse;
import com.psms.exception.GlobalExceptionHandler;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit test cho {@link ServiceCatalogController} — REST endpoints cổng công dân.
 *
 * <p>Dùng MockMvc standaloneSetup: không load Spring context, không cần DB, không cần security.
 * Service được mock bằng Mockito. GlobalExceptionHandler được gắn để test 404 response.
 *
 * <p>Covers: #05-01, #05-02, #05-03 (controller layer).
 */
@ExtendWith(MockitoExtension.class)
class ServiceCatalogControllerTest {

    @Mock
    ServiceCatalogService serviceCatalogService;

    @InjectMocks
    ServiceCatalogController serviceCatalogController;

    MockMvc mockMvc;

    private static final int TEST_PAGE_SIZE = 10;

    @BeforeEach
    void setup() {
        // standaloneSetup: test controller thuần — không có security filter
        // GlobalExceptionHandler gắn để xử lý ResourceNotFoundException → 404
        mockMvc = MockMvcBuilders
                .standaloneSetup(serviceCatalogController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── #05-01: GET /api/client/service-categories ───────────────────

    @Nested
    @DisplayName("GET /api/client/service-categories")
    class ListCategories {

        @Test
        @DisplayName("200 OK — trả JSON list có 2 category với serviceCount đúng")
        void returns200WithCategoryList() throws Exception {
            List<ServiceCategoryResponse> categories = List.of(
                    ServiceCategoryResponse.builder().id(1).code("HC").name("Hành chính").serviceCount(3).build(),
                    ServiceCategoryResponse.builder().id(2).code("DT").name("Đầu tư").serviceCount(0).build()
            );
            given(serviceCatalogService.findAllActiveCategories()).willReturn(categories);

            mockMvc.perform(get("/api/client/service-categories")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].code").value("HC"))
                    .andExpect(jsonPath("$.data[0].serviceCount").value(3))
                    .andExpect(jsonPath("$.data[1].code").value("DT"))
                    .andExpect(jsonPath("$.data[1].serviceCount").value(0));
        }

        @Test
        @DisplayName("200 OK — list rỗng khi không có category active")
        void returns200WithEmptyList() throws Exception {
            given(serviceCatalogService.findAllActiveCategories()).willReturn(List.of());

            mockMvc.perform(get("/api/client/service-categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    // ─── #05-02: GET /api/client/services ─────────────────────────────

    @Nested
    @DisplayName("GET /api/client/services")
    class ListServices {

        @Test
        @DisplayName("200 OK — không filter → trả page với tất cả DV active")
        void returns200WithPageNoFilter() throws Exception {
            List<ServiceTypeResponse> items = List.of(
                    ServiceTypeResponse.builder().id(1L).name("Cấp CCCD").active(true).build(),
                    ServiceTypeResponse.builder().id(2L).name("Đăng ký khai sinh").active(true).build()
            );
            Page<ServiceTypeResponse> page = new PageImpl<>(items, PageRequest.of(0, TEST_PAGE_SIZE), 2);
            given(serviceCatalogService.searchServices(isNull(), isNull(), eq(0), eq(TEST_PAGE_SIZE))).willReturn(page);

            mockMvc.perform(get("/api/client/services")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @DisplayName("200 OK — filter keyword + categoryId → truyền đúng params xuống service")
        void filtersArePassedToService() throws Exception {
            Page<ServiceTypeResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            given(serviceCatalogService.searchServices(eq("khai sinh"), eq(3), eq(0), eq(10)))
                    .willReturn(emptyPage);

            mockMvc.perform(get("/api/client/services")
                            .param("keyword", "khai sinh")
                            .param("categoryId", "3")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        @DisplayName("size > 50 → bị cap xuống 50 (không query quá lớn)")
        void sizeIsCappedAt50() throws Exception {
            Page<ServiceTypeResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
            // Controller cap size=100 → 50 trước khi gọi service
            given(serviceCatalogService.searchServices(isNull(), isNull(), eq(0), eq(50)))
                    .willReturn(page);

            mockMvc.perform(get("/api/client/services")
                            .param("size", "100")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Đảm bảo controller KHÔNG forward size=100 xuống service mà phải cap = 50
            verify(serviceCatalogService).searchServices(isNull(), isNull(), eq(0), eq(50));
        }

        @Test
        @DisplayName("size = 50 → không bị cap (đúng giới hạn)")
        void sizeAt50NotCapped() throws Exception {
            Page<ServiceTypeResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
            given(serviceCatalogService.searchServices(isNull(), isNull(), eq(0), eq(50)))
                    .willReturn(page);

            mockMvc.perform(get("/api/client/services")
                            .param("size", "50")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Đảm bảo size=50 được giữ nguyên, không bị thay đổi thành giá trị khác
            verify(serviceCatalogService).searchServices(isNull(), isNull(), eq(0), eq(50));
        }
    }

    // ─── #05-03: GET /api/client/services/{id} ────────────────────────

    @Nested
    @DisplayName("GET /api/client/services/{id}")
    class GetServiceDetail {

        @Test
        @DisplayName("200 OK — DV tồn tại và active → trả detail đầy đủ")
        void returns200WithDetail() throws Exception {
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

            mockMvc.perform(get("/api/client/services/42")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(42))
                    .andExpect(jsonPath("$.data.code").value("HC-001"))
                    .andExpect(jsonPath("$.data.name").value("Cấp phép xây dựng"))
                    .andExpect(jsonPath("$.data.processingTimeDays").value(15));
        }

        @Test
        @DisplayName("404 — DV không tồn tại → GlobalExceptionHandler trả 404 + message")
        void returns404WhenServiceNotFound() throws Exception {
            given(serviceCatalogService.findServiceById(999L))
                    .willThrow(new ResourceNotFoundException("Dịch vụ không tồn tại hoặc đã ngừng hoạt động"));

            mockMvc.perform(get("/api/client/services/999")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Dịch vụ không tồn tại hoặc đã ngừng hoạt động"));
        }

        @Test
        @DisplayName("404 — DV đã bị tắt (is_active=false) → cùng 404 như không tồn tại")
        void returns404WhenServiceInactive() throws Exception {
            given(serviceCatalogService.findServiceById(10L))
                    .willThrow(new ResourceNotFoundException("Dịch vụ không tồn tại hoặc đã ngừng hoạt động"));

            mockMvc.perform(get("/api/client/services/10")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}

