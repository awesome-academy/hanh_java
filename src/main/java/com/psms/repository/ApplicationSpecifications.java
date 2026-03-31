package com.psms.repository;

import com.psms.entity.Application;
import com.psms.enums.ApplicationStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class ApplicationSpecifications {

    private ApplicationSpecifications() {
    }

    public static Specification<Application> hasStatus(ApplicationStatus status) {
        return (root, query, criteriaBuilder) -> status == null
                ? null
                : criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Application> hasCitizenId(Long citizenId) {
        return (root, query, criteriaBuilder) -> citizenId == null
                ? null
                : criteriaBuilder.equal(root.get("citizen").get("id"), citizenId);
    }

    public static Specification<Application> submittedFrom(LocalDateTime from) {
        return (root, query, criteriaBuilder) -> from == null
                ? null
                : criteriaBuilder.greaterThanOrEqualTo(root.get("submittedAt"), from);
    }

    public static Specification<Application> submittedTo(LocalDateTime to) {
        return (root, query, criteriaBuilder) -> to == null
                ? null
                : criteriaBuilder.lessThanOrEqualTo(root.get("submittedAt"), to);
    }

    public static Specification<Application> withFilters(ApplicationStatus status,
                                                         Long citizenId,
                                                         LocalDateTime from,
                                                         LocalDateTime to) {
        return Specification.allOf(
                hasStatus(status),
                hasCitizenId(citizenId),
                submittedFrom(from),
                submittedTo(to)
        );
    }
}

