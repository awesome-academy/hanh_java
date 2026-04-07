package com.psms.enums;

public enum ApplicationStatus {
    DRAFT("Nháp",                        "p-gray",  "#F1F5F9"),
    SUBMITTED("Đã nộp",                  "p-amber", "var(--warn)"),
    RECEIVED("Đã tiếp nhận",             "p-amber", "var(--warn)"),
    PROCESSING("Đang xử lý",             "p-blue",  "var(--info)"),
    ADDITIONAL_REQUIRED("Yêu cầu bổ sung", "p-amber", "var(--warn)"),
    APPROVED("Đã duyệt",                 "p-green", "var(--success)"),
    REJECTED("Từ chối",                  "p-red",   "var(--danger)");

    private final String label;
    private final String badgeClass;
    /** CSS variable / giá trị màu dùng cho dot trong timeline. */
    private final String dotColor;

    ApplicationStatus(String label, String badgeClass, String dotColor) {
        this.label = label;
        this.badgeClass = badgeClass;
        this.dotColor = dotColor;
    }

    public String getLabel()      { return label; }
    public String getBadgeClass() { return badgeClass; }
    public String getDotColor()   { return dotColor; }
}
