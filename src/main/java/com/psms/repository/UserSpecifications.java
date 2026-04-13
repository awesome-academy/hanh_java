package com.psms.repository;

import com.psms.entity.Role;
import com.psms.entity.User;
import com.psms.enums.RoleName;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Specification cho User — dùng để build dynamic query trong AdminUserService.
 *
 * <p>Role filter dùng EXISTS subquery thay vì JOIN trực tiếp,
 * để tránh cartesian product (1 user nhiều roles) làm sai pagination count.
 *
 * <p>Keyword search dùng LIKE (lowercase) — tận dụng schema có FULLTEXT index.
 * Trade-off: LIKE '%keyword%' không dùng FULLTEXT index, nhưng đơn giản hơn native query
 * và đủ nhanh cho admin page (~vài nghìn user). Nếu scale lớn hơn, dùng native MATCH AGAINST.
 */
public final class UserSpecifications {

    private UserSpecifications() {}

    /**
     * Filter user có role cụ thể.
     * Dùng EXISTS subquery để tránh duplicate rows khi paginate với @ManyToMany.
     */
    public static Specification<User> hasRole(RoleName role) {
        return (root, query, cb) -> {
            if (role == null) return null;
            Subquery<Long> sub = query.subquery(Long.class);
            Root<User> subRoot = sub.from(User.class);
            Join<User, Role> rolesJoin = subRoot.join("roles");
            sub.select(subRoot.get("id"))
                    .where(
                            cb.equal(subRoot.get("id"), root.get("id")),
                            cb.equal(rolesJoin.get("name"), role)
                    );
            return cb.exists(sub);
        };
    }

    /** Filter theo trạng thái active. */
    public static Specification<User> isActive(Boolean active) {
        return (root, query, cb) -> active == null
                ? null
                : cb.equal(root.get("isActive"), active);
    }

    /**
     * Filter theo từ khoá (fullName LIKE hoặc email LIKE).
     * Case-insensitive, trim whitespace.
     */
    public static Specification<User> keywordLike(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) return null;
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)
            );
        };
    }
}

