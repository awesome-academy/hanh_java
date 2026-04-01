package com.psms.repository;

import com.psms.entity.Role;
import com.psms.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Byte>, JpaSpecificationExecutor<Role> {

    Optional<Role> findByName(RoleName name);
}

