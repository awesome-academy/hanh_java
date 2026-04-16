package com.psms.dto.response;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO cho activity log entry — dùng trong admin log list (Feature #13).
 */
@Data
@Builder
public class ActivityLogResponse {

    private Long id;

    /** ID người thực hiện. Null = system action. */
    private Long userId;

    /** Tên hiển thị của người thực hiện. Null = "System". */
    private String userFullName;

    /** Email người thực hiện. */
    private String userEmail;

    /** Loại hành động: LOGIN, SUBMIT_APP, UPDATE_STATUS, ... */
    private String action;

    /** Loại entity bị tác động: applications | users | service_types | ... */
    private String entityType;

    /** ID entity bị tác động. */
    private String entityId;

    /** Mô tả chi tiết hành động. */
    private String description;

    /** IP address của client. */
    private String ipAddress;

    private LocalDateTime createdAt;

    /**
     * CSS class cho action badge — computed từ action, dùng trực tiếp trong Thymeleaf.
     * Không persist, không expose qua JSON API (đã annotate @JsonIgnore, chỉ dùng ở MVC template).
     */
    @JsonIgnore
    public String getTagClass() {
        if (action == null) return "log-update";
        return switch (action) {
            case "LOGIN"                    -> "log-login";
            case "APPROVE"                  -> "log-approve";
            case "REJECT"                   -> "log-reject";
            case "SUBMIT_APP", "CREATE_APP" -> "log-submit";
            default -> {
                if (action.startsWith("CREATE"))  yield "log-create";
                if (action.startsWith("DELETE") || action.equals("LOCK_USER")) yield "log-delete";
                if (action.startsWith("ASSIGN"))  yield "log-assign";
                yield "log-update"; // UPDATE_*, TOGGLE_*, UPDATE_STAFF, ...
            }
        };
    }
}

