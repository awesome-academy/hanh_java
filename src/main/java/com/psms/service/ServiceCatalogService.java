package com.psms.service;

import com.psms.dto.response.ServiceCategoryResponse;
import com.psms.dto.response.ServiceTypeDetailResponse;
import com.psms.dto.response.ServiceTypeResponse;
import com.psms.exception.ResourceNotFoundException;
import com.psms.mapper.ServiceTypeMapper;
import com.psms.repository.ServiceCategoryRepository;
import com.psms.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service xử lý logic nghiệp vụ cho Service Catalog (danh mục + dịch vụ công).
 *
 * <p>Read-only service — toàn bộ method dùng @Transactional(readOnly=true)
 * để Hibernate tắt dirty-checking, tăng hiệu năng.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceCatalogService {

    private final ServiceCategoryRepository categoryRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceTypeMapper serviceTypeMapper;

    private static final int POPULAR_SERVICE_LIMIT = 5;

    // ─── Categories ───────────────────────────────────────────────────

    /**
     * Lấy danh sách lĩnh vực active, kèm số lượng dịch vụ trong từng lĩnh vực.
     * Dùng cho: category grid trang chủ + dropdown filter trang danh sách DV.
     */
    public List<ServiceCategoryResponse> findAllActiveCategories() {
        // 1 query duy nhất: JOIN + GROUP BY → tránh N+1 query
        return categoryRepository.findAllActiveWithServiceCount();
    }

    // ─── Service Types ────────────────────────────────────────────────

    /**
     * Tìm kiếm dịch vụ công với filter + phân trang.
     *
     * @param keyword    Tên DV (partial match, case-insensitive) — null = không filter
     * @param categoryId Lĩnh vực — null = tất cả lĩnh vực
     * @param page       Trang hiện tại (0-based)
     * @param size       Số bản ghi mỗi trang
     */
    public Page<ServiceTypeResponse> searchServices(
            String keyword, Integer categoryId, int page, int size) {

        // Chuẩn hoá keyword: empty string → null để query không bị filter sai
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return serviceTypeRepository.searchActive(kw, categoryId, pageable)
                .map(serviceTypeMapper::toResponse);
    }

    /**
     * Lấy chi tiết 1 dịch vụ. Chỉ trả dịch vụ đang active.
     *
     * @throws ResourceNotFoundException không tồn tại hoặc đã ngừng hoạt động
     */
    public ServiceTypeDetailResponse findServiceById(Long id) {
        return serviceTypeRepository.findByIdAndIsActiveTrue(id)
                .map(serviceTypeMapper::toDetailResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dịch vụ không tồn tại hoặc đã ngừng hoạt động"));
    }

    /**
     * Lấy 5 DV mới nhất (placeholder cho "phổ biến") — dùng cho trang chủ.
     * Sẽ được thay bằng logic lấy theo ranking lượt nộp sau này.
     */
    public List<ServiceTypeResponse> findPopularServices() {
        return serviceTypeRepository.findTopActiveServices(PageRequest.of(0, POPULAR_SERVICE_LIMIT))
                .stream()
                .map(serviceTypeMapper::toResponse)
                .toList();
    }

    /**
     * Tổng số dịch vụ active — dùng cho hero stats trang chủ.
     */
    public long countActiveServices() {
        return serviceTypeRepository.countByIsActiveTrue();
    }
}

