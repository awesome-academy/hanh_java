package com.psms.repository;

import com.psms.entity.ServiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long>, JpaSpecificationExecutor<ServiceType> {

    Optional<ServiceType> findByCode(String code);

    Optional<ServiceType> findByIdAndIsActiveTrue(Long id);

    boolean existsByCode(String code);

    // Đếm DV active theo từng lĩnh vực — dùng cho category grid (serviceCount)
    long countByCategory_IdAndIsActiveTrue(Integer categoryId);

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

}

