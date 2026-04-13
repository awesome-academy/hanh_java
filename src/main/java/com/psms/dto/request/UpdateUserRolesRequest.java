package com.psms.dto.request;

import com.psms.enums.RoleName;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.Set;

/**
 * Request gán / thu hồi role cho user (SUPER_ADMIN only).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRolesRequest {

    @NotEmpty(message = "{validation.role.required}")
    private Set<RoleName> roles;
}

