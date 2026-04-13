package com.psms.dto.request;

import com.psms.enums.Gender;
import com.psms.enums.RoleName;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Set;

/**
 * Request tạo tài khoản mới (dùng cho SUPER_ADMIN).
 *
 * <p>Nếu roles chứa CITIZEN → cần nationalId.
 * Nếu roles chứa STAFF/MANAGER → cần staffCode + departmentId.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 180, message = "{validation.email.size}")
    private String email;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 8, max = 255, message = "{validation.password.size}")
    private String password;

    @NotBlank(message = "{validation.fullName.required}")
    @Size(max = 100, message = "{validation.fullName.size}")
    private String fullName;

    @Pattern(regexp = "^(\\+84|0)\\d{9}$", message = "{validation.phone.pattern}")
    @Size(max = 20, message = "{validation.phone.size}")
    private String phone;

    @NotEmpty(message = "{validation.role.required}")
    private Set<RoleName> roles;

    // ── Citizen-specific (bắt buộc nếu roles chứa CITIZEN) ───────────────────
    private String nationalId;
    // ── Citizen-specific (optional nếu roles chứa CITIZEN) ───────────────────
    private LocalDate dateOfBirth;
    private Gender gender;
    private String permanentAddress;
    private String ward;
    private String province;

    // ── Staff-specific (bắt buộc nếu roles chứa STAFF hoặc MANAGER) ──────────
    private String staffCode;
    private Long departmentId;
    // ── Staff-specific (optional nếu roles chứa STAFF hoặc MANAGER) ──────────
    private String position;
}

