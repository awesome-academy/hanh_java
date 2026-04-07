package com.psms.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitApplicationRequest {

    @NotNull(message = "{validation.serviceType.required}")
    private Long serviceTypeId;

    private String notes;
}
