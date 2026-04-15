package com.psms.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateStaffRequest {

    @NotNull(message = "{validation.staff.department.required}")
    private Long departmentId;

    @Size(max = 100, message = "{validation.staff.position.size}")
    private String position;

    /** true = đang sẵn sàng nhận hồ sơ; false = đang nghỉ phép / bận */
    private boolean available = true;
}
