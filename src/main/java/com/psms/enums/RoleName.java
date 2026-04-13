package com.psms.enums;

public enum RoleName {
    CITIZEN,
    STAFF,
    MANAGER,
    SUPER_ADMIN;

    /**
     * Trả về authority string theo chuẩn Spring Security (prefix "ROLE_").
     * Dùng thay cho magic string "ROLE_STAFF", "ROLE_MANAGER"... ở mọi nơi.
     */
    public String toAuthority() {
        return "ROLE_" + this.name();
    }

    /**
     * CSS pill class tương ứng với role — single source of truth cho màu badge.
     * Template dùng: {@code ${role.pillClass}} thay vì ternary lồng nhau (Rule 1).
     */
    public String getPillClass() {
        return switch (this) {
            case SUPER_ADMIN -> "p-red";
            case MANAGER     -> "p-purple";
            case STAFF       -> "p-blue";
            case CITIZEN     -> "p-gray";
        };
    }

    /** Nhãn tiếng Việt để hiển thị trên UI. */
    public String getLabel() {
        return switch (this) {
            case CITIZEN     -> "Công dân";
            case STAFF       -> "Cán bộ";
            case MANAGER     -> "Quản lý";
            case SUPER_ADMIN -> "Super Admin";
        };
    }
}

