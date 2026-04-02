package com.psms.enums;

public enum RoleName {
    CITIZEN,
    STAFF,
    MANAGER,
    SUPER_ADMIN;

    /**
     * Trả về authority string theo chuẩn Spring Security (prefix "ROLE_").
     * Dùng thay cho magic string "ROLE_STAFF", "ROLE_MANAGER"... ở mọi nơi.
     *
     * <p>Ví dụ: {@code RoleName.STAFF.toAuthority()} → {@code "ROLE_STAFF"}
     */
    public String toAuthority() {
        return "ROLE_" + this.name();
    }
}

