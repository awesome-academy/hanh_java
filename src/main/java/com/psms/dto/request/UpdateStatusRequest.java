package com.psms.dto.request;

import com.psms.enums.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request body cho API cập nhật trạng thái hồ sơ.
 * notes bắt buộc khi newStatus là REJECTED hoặc ADDITIONAL_REQUIRED.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateStatusRequest {

    @NotNull(message = "{validation.status.required}")
    private ApplicationStatus newStatus;

    // Bat buoc khi REJECTED hoac ADDITIONAL_REQUIRED
    private String notes;
}
