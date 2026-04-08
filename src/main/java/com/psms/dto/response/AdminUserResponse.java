package com.psms.dto.response;

import com.psms.enums.RoleName;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DTO phẳng cho trang quản lý người dùng (SUPER_ADMIN).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserResponse {

    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private boolean active;
    private boolean locked;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    /** Các role của user — RoleName enum */
    private Set<RoleName> roles;

    /**
     * True nếu user có role CITIZEN, false nếu chỉ có STAFF.
     */
    private boolean citizen;
    private String nationalId;
    private String staffCode;
    private Long departmentId;
    private String departmentName;
    private String position;

    /**
     * Trả về danh sách tên role dạng chuỗi phân cách bằng dấu phẩy.
     * Dùng cho HTML data-attribute để JS đọc được current roles của user.
     *
     * <p>Ví dụ: "CITIZEN,STAFF" — không phải presentation logic, chỉ để JS dễ xử lý.
     */
    public String getRolesJoined() {
        if (roles == null || roles.isEmpty()) return "";
        return roles.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}


