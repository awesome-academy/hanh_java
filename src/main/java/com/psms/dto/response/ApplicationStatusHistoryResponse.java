package com.psms.dto.response;

import com.psms.enums.ApplicationStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ApplicationStatusHistoryResponse {

    private Long id;
    private ApplicationStatus oldStatus;
    private ApplicationStatus newStatus;
    private Long changedById;
    private String changedByName;
    private String notes;
    private LocalDateTime changedAt;

    public String getNewStatusLabel() {
        return newStatus == null ? null : newStatus.getLabel();
    }
}

