package com.psms.repository;

import com.psms.entity.Application;
import com.psms.enums.ApplicationStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * Các Specification này được dùng để xây dựng dynamic query cho ApplicationRepository,
 * cho phép filter theo nhiều tiêu chí khác nhau mà không cần viết nhiều method query riêng lẻ.
 * Ví dụ: filter theo status, citizenId, serviceTypeId, assignedStaffId, khoảng thời gian nộp hồ sơ, v.v.
 * Các method Specification trả về null nếu filter value là null, để dễ dàng kết hợp với Specification.allOf() mà không cần kiểm tra null ở caller.
 **/
public final class ApplicationSpecifications {

    private ApplicationSpecifications() {}

    public static Specification<Application> hasStatus(ApplicationStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Application> hasCitizenId(Long citizenId) {
        return (root, query, cb) -> citizenId == null ? null : cb.equal(root.get("citizen").get("id"), citizenId);
    }

    public static Specification<Application> hasServiceTypeId(Long serviceTypeId) {
        return (root, query, cb) -> serviceTypeId == null ? null : cb.equal(root.get("serviceType").get("id"), serviceTypeId);
    }

    public static Specification<Application> hasAssignedStaffId(Long staffId) {
        return (root, query, cb) -> staffId == null ? null : cb.equal(root.get("assignedStaff").get("id"), staffId);
    }

    public static Specification<Application> submittedFrom(LocalDateTime from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("submittedAt"), from);
    }

    public static Specification<Application> submittedTo(LocalDateTime to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("submittedAt"), to);
    }

    public static Specification<Application> withFilters(ApplicationStatus status,
                                                         Long citizenId,
                                                         LocalDateTime from,
                                                         LocalDateTime to) {
        return Specification.allOf(hasStatus(status), hasCitizenId(citizenId), submittedFrom(from), submittedTo(to));
    }

    // Filter da chieu danh cho admin
    public static Specification<Application> withAdminFilters(ApplicationStatus status,
                                                               Long serviceTypeId,
                                                               Long staffId,
                                                               LocalDateTime from,
                                                               LocalDateTime to) {
        return Specification.allOf(
                hasStatus(status),
                hasServiceTypeId(serviceTypeId),
                hasAssignedStaffId(staffId),
                submittedFrom(from),
                submittedTo(to)
        );
    }
}
