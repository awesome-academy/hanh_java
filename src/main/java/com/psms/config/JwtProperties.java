package com.psms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bind các property {@code jwt.*} từ application.yml vào bean.
 *
 * <pre>
 * jwt:
 *   secret: ...          # đọc từ env var JWT_SECRET trên prod
 *   expiration: 3600000  # 1h ms
 *   refresh-expiration: 604800000  # 7d ms
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /** JWT signing secret — đọc từ env var, không hardcode trên prod */
    private String secret;

    /** Access token TTL, milliseconds (default 1h) */
    private long expiration = 3_600_000L;

    /** Refresh token TTL, milliseconds (default 7d) */
    private long refreshExpiration = 604_800_000L;
}

