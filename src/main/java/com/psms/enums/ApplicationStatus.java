package com.psms.enums;

public enum ApplicationStatus {
    DRAFT("Nháp", "p-gray"),
    SUBMITTED("Đã nộp", "p-amber"),
    RECEIVED("Đã tiếp nhận", "p-amber"),
    PROCESSING("Đang xử lý", "p-blue"),
    ADDITIONAL_REQUIRED("Yêu cầu bổ sung", "p-amber"),
    APPROVED("Đã duyệt", "p-green"),
    REJECTED("Từ chối", "p-red");

    private final String label;
    private final String badgeClass;

    ApplicationStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}

