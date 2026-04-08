package com.psms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request cập nhật thông tin cơ bản của user.
 * Không cho phép sửa: email, password, roles (endpoint riêng).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    @NotBlank(message = "{validation.fullName.required}")
    @Size(max = 100, message = "{validation.fullName.size}")
    private String fullName;

    @Pattern(regexp = "^(\\+84|0)\\d{9}$", message = "{validation.phone.pattern}")
    @Size(max = 20, message = "{validation.phone.size}")
    private String phone;

    // ── Staff-specific (optional, chỉ áp dụng nếu user là staff) ────────────
    private Long departmentId;
    private String position;
}

