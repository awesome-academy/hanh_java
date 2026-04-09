package com.psms.enums;

/**
 * Loại thông báo gửi đến công dân.
 * Mỗi loại tương ứng với 1 sự kiện nghiệp vụ trong hệ thống.
 */
public enum NotificationType {

    APPLICATION_RECEIVED("Hồ sơ đã được tiếp nhận"),
    ADDITIONAL_REQUIRED("Yêu cầu bổ sung tài liệu"),
    STATUS_UPDATED("Trạng thái hồ sơ thay đổi"),
    APPROVED("Hồ sơ đã được phê duyệt"),
    REJECTED("Hồ sơ bị từ chối"),
    SYSTEM("Thông báo hệ thống");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Icon emoji và background color cho UI — xem ui-spec.md C-06. */
    public String getIcon() {
        return switch (this) {
            case APPLICATION_RECEIVED -> "📨";
            case ADDITIONAL_REQUIRED  -> "📋";
            case STATUS_UPDATED       -> "⚙️";
            case APPROVED             -> "✅";
            case REJECTED             -> "❌";
            case SYSTEM               -> "🔔";
        };
    }

    public String getBgColor() {
        return switch (this) {
            case APPLICATION_RECEIVED, STATUS_UPDATED -> "#EFF6FF";
            case ADDITIONAL_REQUIRED                  -> "#FEF9C3";
            case APPROVED                             -> "#F0FDF4";
            case REJECTED                             -> "#FEE2E2";
            case SYSTEM                               -> "#F1F5F9";
        };
    }
}

