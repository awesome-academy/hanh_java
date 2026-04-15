package com.psms.enums;

public enum ImportType {
    CITIZENS("citizens"),
    SERVICES("services"),
    DEPARTMENTS("departments"),
    STAFF("staff");

    private final String code;

    ImportType(String code) { this.code = code; }
    public String getCode() { return code; }

    public static ImportType fromCode(String code) {
        for (ImportType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        throw new IllegalArgumentException("ImportType không hợp lệ: " + code);
    }
}

