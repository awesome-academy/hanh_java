package com.psms.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request body cho API phân công cán bộ xử lý hồ sơ.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignStaffRequest {

    @NotNull(message = "{validation.staff.required}")
    private Long staffId;
}
