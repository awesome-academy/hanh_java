package com.psms.dto.response;

import com.psms.enums.NotificationType;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private String title;
    private String content;
    private boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private Long applicationId;

    /** Icon emoji theo type — tính toán từ enum, không persist. */
    public String getIcon() {
        return type != null ? type.getIcon() : "🔔";
    }

    /** Background color cho avatar icon. */
    public String getBgColor() {
        return type != null ? type.getBgColor() : "#F1F5F9";
    }

    public String getTypeLabel() {
        return type != null ? type.getLabel() : "";
    }
}

