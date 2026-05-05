package com.psms.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Binding cho prefix {@code email.*} trong application.yml.
 * Dùng @ConfigurationProperties thay vì @Value rải rác để dễ test và refactor.
 *
 * <p>{@code @Validated} đảm bảo Spring Boot fail-fast khi startup nếu config thiếu/blank —
 * tốt hơn fail silently khi gửi email đầu tiên.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "email")
public class EmailProperties {
    /** From address — ví dụ: noreply@psms.gov.vn */
    @NotBlank
    private String from = "noreply@psms.gov.vn";
    /** Display name của sender — ví dụ: "Cổng DVCQG" */
    @NotBlank
    private String fromName = "Cổng DVCQG";
}
