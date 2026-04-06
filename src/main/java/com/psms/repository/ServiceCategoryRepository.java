package com.psms.repository;

import com.psms.dto.response.ServiceCategoryResponse;
import com.psms.entity.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Integer>, JpaSpecificationExecutor<ServiceCategory> {

    Optional<ServiceCategory> findByCode(String code);

    List<ServiceCategory> findAllByIsActiveTrueOrderBySortOrderAsc();

    /**
     * Lấy danh sách lĩnh vực active + đếm số DV active trong từng lĩnh vực chỉ trong 1 query.
     * Dùng LEFT JOIN để category không có DV nào vẫn xuất hiện với serviceCount = 0.
     */
    @Query("""
        SELECT new com.psms.dto.response.ServiceCategoryResponse(
            c.id, c.code, c.name, c.description, c.icon, c.sortOrder, COUNT(s.id)
        )
        FROM ServiceCategory c
        LEFT JOIN ServiceType s ON s.category.id = c.id AND s.isActive = true
        WHERE c.isActive = true
        GROUP BY c.id, c.code, c.name, c.description, c.icon, c.sortOrder
        ORDER BY c.sortOrder ASC
        """)
    List<ServiceCategoryResponse> findAllActiveWithServiceCount();
}

