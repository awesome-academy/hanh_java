package com.psms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * Response trả về sau login/refresh-token thành công.
 *
 * <p>{@code refreshToken} chỉ xuất hiện ở login response body (trường hợp REST client).
 * Với Thymeleaf SSR: refreshToken được set vào HttpOnly cookie, không trả trong body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    /** TTL tính bằng giây (client dùng để biết khi nào cần refresh) */
    private long expiresIn;

    private String refreshToken;

    private String email;

    private String fullName;

    /** Danh sách role (ví dụ: ["CITIZEN"], ["STAFF", "MANAGER"]) */
    private List<String> roles;
}

