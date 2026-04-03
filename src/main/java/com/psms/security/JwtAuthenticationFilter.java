package com.psms.security;

import com.psms.service.CustomUserDetailsService;
import com.psms.service.RevokedTokenService;
import com.psms.util.JwtTokenProvider;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter xác thực JWT — chạy 1 lần mỗi request ({@link OncePerRequestFilter}).
 *
 * <p><b>Flow:</b>
 * <pre>
 *   1. Extract Bearer token từ Authorization header
 *   2. Validate signature + expiry (JwtTokenProvider)
 *   3. Kiểm tra jti có trong blacklist không (RevokedTokenService)
 *      → nếu bị revoke: bỏ qua, Security sẽ trả 401
 *   4. Load UserDetails từ DB (email từ subject claim)
 *   5. Set Authentication vào SecurityContextHolder
 * </pre>
 *
 * <p><b>Lưu ý CSRF:</b> REST API dùng JWT (stateless) nên CSRF được disable cho
 * các route /api/**. Thymeleaf form vẫn dùng CSRF token bình thường.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final RevokedTokenService revokedTokenService;

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractBearerToken(request);

        // Không có token → tiếp tục filter chain (Spring Security sẽ xử lý 401)
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate signature + expiry
        if (!jwtTokenProvider.isTokenValid(token)) {
            log.debug("Invalid JWT token on {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // Kiểm tra blacklist (đã logout chưa)
        String jti = jwtTokenProvider.extractJti(token);
        if (revokedTokenService.isRevoked(jti)) {
            log.debug("Revoked JWT jti={} on {}", jti, request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // Chỉ set authentication nếu chưa có trong context (tránh override)
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String username = jwtTokenProvider.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT từ {@code Authorization: Bearer <token>} header.
     *
     * @return token string hoặc null nếu không có / sai format
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}

