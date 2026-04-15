package com.psms.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDepartmentRequest {

    @NotBlank(message = "{validation.department.name.required}")
    @Size(max = 200, message = "{validation.department.name.size}")
    private String name;

    private String address;

    @Size(max = 20, message = "{validation.phone.size}")
    private String phone;

    @Email(message = "{validation.department.email.invalid}")
    @Size(max = 180, message = "{validation.email.size}")
    private String email;

    /** ID của user có role MANAGER làm trưởng phòng — null = xóa trưởng phòng */
    private Long leaderId;
}
