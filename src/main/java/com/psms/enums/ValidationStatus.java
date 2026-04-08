package com.psms.enums;

public enum ValidationStatus {
    PENDING("Đang kiểm tra", "p-amber"),
    VALID("Hợp lệ",          "p-green"),
    INVALID("Không hợp lệ",  "p-red");

    private final String label;
    private final String badgeClass;

    ValidationStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel()      { return label; }
    public String getBadgeClass() { return badgeClass; }
}

