package com.psms.dto.response;

import com.psms.enums.ApplicationStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AdminApplicationResponse {

    private Long id;
    private String applicationCode;
    private Long citizenId;
    private String citizenFullName;
    private String citizenNationalId;
    private Long serviceTypeId;
    private String serviceTypeName;
    private String departmentName;
    private BigDecimal fee;
    private Long departmentId;
    private Long assignedStaffId;
    private String assignedStaffName;
    private ApplicationStatus status;
    private LocalDateTime submittedAt;
    private LocalDate processingDeadline;
    private LocalDateTime receivedAt;
    private LocalDateTime completedAt;
    private String notes;
    private String rejectionReason;
    private List<ApplicationStatusHistoryResponse> statusHistory;

    /**
     * Được tính và set bởi AdminApplicationService.mapToAdminResponse(),
     * không dùng LocalDate.now() trực tiếp trong DTO để dễ test.
     */
    private boolean overdue;

    public String getStatusBadgeClass() {
        return status == null ? "" : status.getBadgeClass();
    }

    public String getStatusLabel() {
        return status == null ? "" : status.getLabel();
    }
}
