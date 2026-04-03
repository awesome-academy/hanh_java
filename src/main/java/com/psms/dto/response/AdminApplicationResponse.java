package com.psms.dto.response;

import com.psms.enums.ApplicationStatus;
import lombok.*;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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

    /**
     * ThreadLocal để tái sử dụng NumberFormat mà không tạo object mới mỗi lần gọi.
     * ThreadLocal đảm bảo thread-safe vì NumberFormat không thread-safe.
     * Locale "vi_VN" đảm bảo kết quả nhất quán trên mọi server.
     */
    private static final ThreadLocal<NumberFormat> VN_NUMBER_FORMAT =
            ThreadLocal.withInitial(() -> {
                NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
                nf.setGroupingUsed(true);
                nf.setMaximumFractionDigits(0);
                return nf;
            });

    /**
     * Format lệ phí cho Thymeleaf — tránh complex SpEL expression trong template.
     */
    public String getFormattedFee() {
        if (fee == null) return "—";
        if (fee.compareTo(BigDecimal.ZERO) == 0) return "Miễn phí";
        return VN_NUMBER_FORMAT.get().format(fee) + " VNĐ";
    }
}
