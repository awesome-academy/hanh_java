package com.psms.enums;

public enum ExportType {
    CITIZENS("citizens"),
    APPLICATIONS("applications"),
    SERVICES("services"),
    DEPARTMENTS("departments"),
    STAFF("staff");

    private final String code;

    ExportType(String code) { this.code = code; }
    public String getCode() { return code; }

    public static ExportType fromCode(String code) {
        for (ExportType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        throw new IllegalArgumentException("ExportType không hợp lệ: " + code);
    }
}

