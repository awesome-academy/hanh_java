package com.psms.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ServiceTypeDetailResponse {

    private Long id;
    private String code;
    private String name;
    private Integer categoryId;
    private String categoryName;
    private Long departmentId;
    private String departmentName;
    private String description;
    private String requirements;
    private short processingTimeDays;
    private BigDecimal fee;
    private String feeDescription;
    private boolean active;
}

