package com.psms.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceTypeResponse {

    private Long id;
    private String code;
    private String name;
    private Integer categoryId;
    private String categoryName;
    private Long departmentId;
    private String departmentName;
    private short processingTimeDays;
    private BigDecimal fee;
    private boolean active;
}

