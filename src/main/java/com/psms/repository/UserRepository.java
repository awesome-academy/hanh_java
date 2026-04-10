package com.psms.repository;

import com.psms.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesByEmail(String email);

    /**
     * Tìm user kèm roles theo ID — dùng khi cần đọc / cập nhật roles.
     * {@code @EntityGraph} eager-fetch roles trong 1 query, tránh N+1.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    /**
     * Batch-fetch users kèm roles theo danh sách ID.
     * Dùng trong findAll() pagination để tránh N+1 lazy-load roles.
     * 1 query duy nhất thay vì N query.
     */
    @EntityGraph(attributePaths = "roles")
    List<User> findWithRolesByIdIn(Collection<Long> ids);
}

