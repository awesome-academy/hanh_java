package com.psms.service;

import com.psms.dto.request.CreateServiceTypeRequest;
import com.psms.dto.request.UpdateServiceTypeRequest;
import com.psms.dto.response.AdminServiceTypeResponse;
import com.psms.entity.Department;
import com.psms.entity.ServiceCategory;
import com.psms.entity.ServiceType;
import com.psms.enums.ApplicationStatus;
import com.psms.exception.BusinessException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.repository.ApplicationRepository;
import com.psms.repository.DepartmentRepository;
import com.psms.repository.ServiceCategoryRepository;
import com.psms.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service quản lý dịch vụ công — CRUD cho admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminServiceTypeService {

    /** Các trạng thái hồ sơ đang xử lý — dùng để đếm workload và chặn xóa dịch vụ */
    static final List<ApplicationStatus> ACTIVE_STATUSES = List.of(
            ApplicationStatus.SUBMITTED,
            ApplicationStatus.RECEIVED,
            ApplicationStatus.PROCESSING,
            ApplicationStatus.ADDITIONAL_REQUIRED
    );

    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationRepository applicationRepository;

    // ─── List ──────────────────────────────────────────────────────────────────

    /**
     * Truy vấn danh sách dịch vụ công (ServiceType), hỗ trợ filter, phân trang
     * - Tối ưu hóa để tránh N+1 queries khi cần hiển thị số lượng hồ sơ đang xử lý của từng dịch vụ.
     */
    public Page<AdminServiceTypeResponse> findAll(String keyword, Integer categoryId,
                                                   Boolean isActive, int page, int size) {
        // Tạo Specification filter động dựa trên các tham số filter (keyword, categoryId, isActive).
        Specification<ServiceType> spec = (root, q, cb) -> null;
        if (keyword != null && !keyword.isBlank()) spec = spec.and(keywordLike(keyword));
        if (categoryId != null)                    spec = spec.and(hasCategoryId(categoryId));
        if (isActive != null)                      spec = spec.and(hasActive(isActive));

        // 1 query — @EntityGraph eager-fetch category + department (tránh lazy N+1)
        Page<ServiceType> svcPage = serviceTypeRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")));

        // 1 batch query COUNT thay cho N queries — guard empty để tránh lỗi IN()
        List<Long> ids = svcPage.getContent().stream().map(ServiceType::getId).toList();
        // Truy vấn batch đếm số hồ sơ đang xử lý theo từng serviceTypeId trong trang hiện tại
        // trả về Map<serviceTypeId, count>
        Map<Long, Long> activeCountMap = ids.isEmpty() ? Map.of() :
                applicationRepository.countActiveByServiceTypeIdIn(ids, ACTIVE_STATUSES)
                        .stream().collect(Collectors.toMap(
                                r -> (Long) r[0],
                                r -> (Long) r[1]));

        // Map kết quả sang DTO -> Trả về Page<AdminServiceTypeResponse>
        return svcPage.map(svc -> mapToResponse(svc, activeCountMap.getOrDefault(svc.getId(), 0L)));
    }

    // ─── Get by ID ─────────────────────────────────────────────────────────────

    /**
     * Chi tiết 1 dịch vụ (bao gồm cả inactive).
     */
    public AdminServiceTypeResponse findById(Long id) {
        return serviceTypeRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Dịch vụ", id));
    }

    // ─── Create ────────────────────────────────────────────────────────────────

    /**
     * Tạo dịch vụ mới.
     *
     * @throws BusinessException nếu code đã tồn tại
     */
    @Transactional
    public AdminServiceTypeResponse create(CreateServiceTypeRequest request) {
        if (serviceTypeRepository.existsByCode(request.getCode())) {
            throw new BusinessException("Mã dịch vụ đã tồn tại: " + request.getCode());
        }
        ServiceCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Lĩnh vực không tồn tại: id=" + request.getCategoryId()));
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại: id=" + request.getDepartmentId()));

        ServiceType svc = buildServiceTypeFromRequest(request, category, department);
        serviceTypeRepository.save(svc);

        log.info("Admin created service type: code={}, name={}", svc.getCode(), svc.getName());
        return mapToResponse(svc);
    }

    private ServiceType buildServiceTypeFromRequest(CreateServiceTypeRequest request, ServiceCategory category, Department department) {
        ServiceType svc = new ServiceType();
        svc.setCode(request.getCode().trim().toUpperCase());
        svc.setName(request.getName().trim());
        svc.setCategory(category);
        svc.setDepartment(department);
        svc.setDescription(request.getDescription());
        svc.setRequirements(request.getRequirements());
        svc.setProcessingTimeDays(request.getProcessingTimeDays());
        svc.setFee(request.getFee() != null ? request.getFee() : java.math.BigDecimal.ZERO);
        svc.setFeeDescription(request.getFeeDescription());
        svc.setActive(true);
        return svc;
    }

    // ─── Update ────────────────────────────────────────────────────────────────

    /**
     * Cập nhật dịch vụ (không được đổi code).
     */
    @Transactional
    public AdminServiceTypeResponse update(Long id, UpdateServiceTypeRequest request) {
        ServiceType svc = serviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dịch vụ", id));
        ServiceCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Lĩnh vực không tồn tại: id=" + request.getCategoryId()));
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại: id=" + request.getDepartmentId()));

        svc.setName(request.getName().trim());
        svc.setCategory(category);
        svc.setDepartment(department);
        svc.setDescription(request.getDescription());
        svc.setRequirements(request.getRequirements());
        svc.setProcessingTimeDays(request.getProcessingTimeDays());
        svc.setFee(request.getFee() != null ? request.getFee() : java.math.BigDecimal.ZERO);
        svc.setFeeDescription(request.getFeeDescription());
        serviceTypeRepository.save(svc);

        log.info("Admin updated service type: id={}", id);
        return mapToResponse(svc);
    }

    // ─── Toggle active ─────────────────────────────────────────────────────────

    /**
     * Bật/tắt trạng thái hoạt động của dịch vụ.
     * Dịch vụ tắt không hiển thị cho công dân trên portal.
     */
    @Transactional
    public AdminServiceTypeResponse toggleActive(Long id) {
        ServiceType svc = serviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dịch vụ", id));
        svc.setActive(!svc.isActive());
        serviceTypeRepository.save(svc);
        log.info("Admin toggled service type: id={}, active={}", id, svc.isActive());
        return mapToResponse(svc);
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    /**
     * Xóa dịch vụ.
     *
     * <p><strong>Block delete:</strong> không cho xóa nếu có hồ sơ đang ở trạng thái chưa
     * hoàn thành để đảm bảo tính toàn vẹn dữ liệu hồ sơ.
     *
     * @throws BusinessException nếu dịch vụ đang có hồ sơ đang xử lý
     */
    @Transactional
    public void delete(Long id) {
        ServiceType svc = serviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dịch vụ", id));

        long activeCount = applicationRepository.countByServiceTypeIdAndStatusIn(id, ACTIVE_STATUSES);
        if (activeCount > 0) {
            throw new BusinessException(
                    "Không thể xóa dịch vụ \"" + svc.getName() + "\" vì đang có "
                    + activeCount + " hồ sơ chưa hoàn thành. Hãy tắt dịch vụ thay vì xóa.");
        }
        serviceTypeRepository.delete(svc);
        log.info("Admin deleted service type: id={}, code={}", id, svc.getCode());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /** Dùng cho findAll() — activeCount đã được batch-load trước, không query thêm */
    private AdminServiceTypeResponse mapToResponse(ServiceType svc, long activeCount) {
        return AdminServiceTypeResponse.builder()
                .id(svc.getId())
                .code(svc.getCode())
                .name(svc.getName())
                .categoryId(svc.getCategory() != null ? svc.getCategory().getId() : null)
                .categoryName(svc.getCategory() != null ? svc.getCategory().getName() : null)
                .departmentId(svc.getDepartment() != null ? svc.getDepartment().getId() : null)
                .departmentName(svc.getDepartment() != null ? svc.getDepartment().getName() : null)
                .description(svc.getDescription())
                .requirements(svc.getRequirements())
                .processingTimeDays(svc.getProcessingTimeDays())
                .fee(svc.getFee())
                .feeDescription(svc.getFeeDescription())
                .active(svc.isActive())
                .activeApplicationCount(activeCount)
                .build();
    }

    /** Dùng cho findById(), create(), update(), toggleActive() — load count riêng lẻ */
    private AdminServiceTypeResponse mapToResponse(ServiceType svc) {
        return mapToResponse(svc,
                applicationRepository.countByServiceTypeIdAndStatusIn(svc.getId(), ACTIVE_STATUSES));
    }

    // ─── Specifications ────────────────────────────────────────────────────────

    private static Specification<ServiceType> keywordLike(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String kw = "%" + keyword.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("name")), kw);
    }

    private static Specification<ServiceType> hasCategoryId(Integer categoryId) {
        if (categoryId == null) return null;
        return (root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    private static Specification<ServiceType> hasActive(Boolean isActive) {
        if (isActive == null) return null;
        return (root, q, cb) -> cb.equal(root.get("isActive"), isActive);
    }
}

