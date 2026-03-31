package com.psms.dto.response;

import com.psms.enums.ApplicationStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ApplicationResponse {

    private Long id;
    private String applicationCode;
    private Long citizenId;
    private Long serviceTypeId;
    private String serviceTypeName;
    private LocalDateTime submittedAt;
    private LocalDate processingDeadline;
    private ApplicationStatus status;

    public String getStatusBadgeClass() {
        return status == null ? null : status.getBadgeClass();
    }

    public String getStatusLabel() {
        return status == null ? null : status.getLabel();
    }
}

