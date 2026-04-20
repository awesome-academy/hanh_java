package com.psms.repository;

import com.psms.entity.ServiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long>, JpaSpecificationExecutor<ServiceType> {

    /**
     * Override JpaSpecificationExecutor.findAll để eager-fetch category + department trong 1 query.
     * Tránh N+1 lazy load khi pagination trong AdminServiceTypeService.findAll().
     */
    @Override
    @EntityGraph(attributePaths = {"category", "department"})
    Page<ServiceType> findAll(Specification<ServiceType> spec, Pageable pageable);

    Optional<ServiceType> findByCode(String code);

    Optional<ServiceType> findByIdAndIsActiveTrue(Long id);

    boolean existsByCode(String code);

    // Đếm tổng DV active — dùng cho hero stats
    long countByIsActiveTrue();

    // Tìm top N DV active mới tạo nhất — placeholder "phổ biến" cho trang chủ
    // Sẽ được thay bằng logic lấy theo ranking lượt nộp sau này.
    @Query("SELECT s FROM ServiceType s WHERE s.isActive = true ORDER BY s.createdAt DESC")
    List<ServiceType> findTopActiveServices(Pageable pageable);


    // Full-text search: keyword LIKE trên name, filter categoryId tuỳ chọn, chỉ DV active, phân trang
    @Query("""
        SELECT s FROM ServiceType s
        WHERE s.isActive = true
          AND (:categoryId IS NULL OR s.category.id = :categoryId)
          AND (:keyword IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY s.name ASC
        """)
    Page<ServiceType> searchActive(
            @Param("keyword") String keyword,
            @Param("categoryId") Integer categoryId,
            Pageable pageable);

    // Lấy toàn bộ DV active, sắp xếp A-Z — dùng cho dropdown form nộp hồ sơ
    List<ServiceType> findAllByIsActiveTrueOrderByNameAsc();

    /**
     * Fetch toàn bộ service types kèm category + department — dùng cho CSV export.
     */
    @EntityGraph(attributePaths = {"category", "department"})
    @Query("SELECT s FROM ServiceType s ORDER BY s.name")
    List<ServiceType> findAllForExport();

}

