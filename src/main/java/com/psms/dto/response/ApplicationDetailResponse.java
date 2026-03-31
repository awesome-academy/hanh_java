package com.psms.dto.response;

import com.psms.enums.ApplicationStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ApplicationDetailResponse {

    private Long id;
    private String applicationCode;
    private Long citizenId;
    private String citizenFullName;
    private Long serviceTypeId;
    private String serviceTypeName;
    private String departmentName;
    private BigDecimal fee;
    private ApplicationStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime completedAt;
    private LocalDate processingDeadline;
    private Long assignedStaffId;
    private String assignedStaffName;
    private String notes;
    private String rejectionReason;
    private List<ApplicationStatusHistoryResponse> statusHistory;

    public String getStatusBadgeClass() {
        return status == null ? null : status.getBadgeClass();
    }

    public String getStatusLabel() {
        return status == null ? null : status.getLabel();
    }
}

