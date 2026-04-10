package com.psms.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateServiceTypeRequest {

    @NotBlank(message = "{validation.serviceType.code.required}")
    @Size(max = 30, message = "{validation.serviceType.code.size}")
    private String code;

    @NotBlank(message = "{validation.serviceType.name.required}")
    @Size(max = 200, message = "{validation.serviceType.name.size}")
    private String name;

    @NotNull(message = "{validation.serviceType.category.required}")
    private Integer categoryId;

    @NotNull(message = "{validation.serviceType.department.required}")
    private Long departmentId;

    private String description;
    private String requirements;

    @Min(value = 1, message = "{validation.serviceType.processingTimeDays.min}")
    @Max(value = 365, message = "{validation.serviceType.processingTimeDays.max}")
    private short processingTimeDays = 5;

    @DecimalMin(value = "0", message = "{validation.serviceType.fee.min}")
    private BigDecimal fee = BigDecimal.ZERO;

    @Size(max = 255, message = "{validation.serviceType.feeDescription.size}")
    private String feeDescription;
}
