package com.psms.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitApplicationRequest {

    @NotNull(message = "Vui lòng chọn dịch vụ cần nộp hồ sơ")
    private Long serviceTypeId;

    private String notes;
}

