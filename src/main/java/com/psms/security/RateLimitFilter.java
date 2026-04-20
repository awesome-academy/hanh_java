package com.psms.security;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limit filter cho các endpoint auth — chống brute-force attack.
 *
 * <p><b>Algorithm:</b> Fixed window per IP address.
 * Mỗi IP được phép tối đa {@code MAX_REQUESTS} request trong {@code WINDOW_SECONDS} giây.
 * Nếu vượt quá → 429 Too Many Requests.
 *
 * <p><b>Áp dụng cho:</b>
 * <ul>
 *   <li>{@code POST /api/auth/login}</li>
 *   <li>{@code POST /api/auth/register}</li>
 *   <li>{@code POST /api/admin/auth/login}</li>
 * </ul>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Số request tối đa mỗi IP trong 1 window. */
    private static final int MAX_REQUESTS = 10;

    /** Kích thước window tính bằng giây (1 phút). */
    private static final long WINDOW_SECONDS = 60L;

    /** IP → [request count, window start epoch seconds]. */
    private final ConcurrentHashMap<String, long[]> ipCounters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        // Chỉ rate-limit các POST endpoint auth
        return !("POST".equals(method) && (
                uri.equals("/api/auth/login") ||
                uri.equals("/api/auth/register") ||
                uri.equals("/api/admin/auth/login")
        ));
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain)
            throws ServletException, IOException {

        String ip = extractClientIp(request);
        long nowSeconds = Instant.now().getEpochSecond();

        long[] counter = ipCounters.compute(ip, (key, existing) -> {
            if (existing == null || nowSeconds - existing[1] >= WINDOW_SECONDS) {
                // Window mới hoặc lần đầu: [count=1, windowStart=now]
                return new long[]{1L, nowSeconds};
            }
            // Cùng window: tăng count
            existing[0]++;
            return existing;
        });

        long requestCount = counter[0];
        long windowStart  = counter[1];
        long resetIn      = WINDOW_SECONDS - (nowSeconds - windowStart);

        if (requestCount > MAX_REQUESTS) {
            log.warn("Rate limit exceeded for IP={} uri={} count={}",
                     ip, request.getRequestURI(), requestCount);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(resetIn));
            response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(windowStart + WINDOW_SECONDS));
            response.getWriter().write("""
                    {"success":false,"message":"Quá nhiều yêu cầu. Vui lòng thử lại sau %d giây.","data":null}
                    """.formatted(resetIn));
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(MAX_REQUESTS - requestCount));
        response.setHeader("X-RateLimit-Reset", String.valueOf(windowStart + WINDOW_SECONDS));

        filterChain.doFilter(request, response);
    }

    /**
     * Lấy IP thực của client, xử lý trường hợp đứng sau proxy / load balancer.
     * Ưu tiên: X-Forwarded-For → X-Real-IP → RemoteAddr.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For có thể chứa nhiều IP: "client, proxy1, proxy2"
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
