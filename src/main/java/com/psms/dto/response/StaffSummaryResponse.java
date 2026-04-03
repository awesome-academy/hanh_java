package com.psms.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffSummaryResponse {

    private Long staffId;
    private Long userId;
    private String staffCode;
    private String fullName;
    private String email;
    private String departmentName;
    private String position;
    private boolean available;
    private long activeApplicationCount;
}

