package com.psms.mapper;

import com.psms.dto.request.RegisterRequest;
import com.psms.dto.response.UserSummaryResponse;
import com.psms.entity.Role;
import com.psms.entity.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(config = MapStructCentralConfig.class)
public interface UserMapper {
    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "locked", ignore = true)
    @Mapping(target = "failedLoginCount", ignore = true)
    @Mapping(target = "lockedUntil", ignore = true)
    @Mapping(target = "emailNotifEnabled", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "authorities", ignore = true)  // UserDetails field, Spring Security tự tính từ roles
    User toEntity(RegisterRequest request);

    @Mapping(target = "roles", expression = "java(mapRoleNames(user.getRoles()))")
    UserSummaryResponse toSummary(User user);

    default Set<String> mapRoleNames(Set<Role> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
                .map(Role::getName)
                .map(Enum::name)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}

