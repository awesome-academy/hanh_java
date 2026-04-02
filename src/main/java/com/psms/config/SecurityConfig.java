package com.psms.config;

import com.psms.security.JwtAuthenticationFilter;
import com.psms.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cấu hình Spring Security.
 *
 * <p><b>Kiến trúc dual-layer:</b>
 * <ul>
 *   <li>REST API ({@code /api/**}): stateless JWT, CSRF disabled</li>
 *   <li>Thymeleaf MVC ({@code /auth/**, /admin/**, ...}): session + CSRF enabled</li>
 * </ul>
 *
 * <p><b>URL Access Rules:</b>
 * <pre>
 *   Public (permitAll):
 *     GET /api/client/service-categories, /api/client/services/**  → xem DV không cần login
 *     POST /api/auth/**                                            → register, login, refresh
 *     /auth/**, /admin/login, /error/**, /static/**                → MVC pages
 *
 *   CITIZEN:
 *     /api/client/applications/**, /api/client/profile, /api/client/notifications/**
 *
 *   STAFF | MANAGER | SUPER_ADMIN:
 *     /api/admin/**
 *
 *   SUPER_ADMIN only:
 *     /api/admin/users/**, /api/admin/logs/purge
 *     (enforce thêm bằng @PreAuthorize ở controller)
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // bật @PreAuthorize trên controller/service
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    // ----------------------------------------------------------------
    // Filter chain cho REST API (/api/**)
    // ----------------------------------------------------------------

    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            // Chỉ áp dụng cho /api/**
            .securityMatcher("/api/**")

            // REST API stateless — không dùng session
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF disabled cho REST (client dùng JWT Bearer, không dùng cookie session)
            .csrf(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(auth -> auth
                // ── Public ──────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST,
                    "/api/auth/register",
                    "/api/auth/login",
                    "/api/auth/refresh-token").permitAll()

                .requestMatchers(HttpMethod.POST,
                    "/api/admin/auth/login").permitAll()

                // Xem danh mục + dịch vụ không cần login (SEO-friendly)
                .requestMatchers(HttpMethod.GET,
                    "/api/client/service-categories",
                    "/api/client/services",
                    "/api/client/services/**").permitAll()

                // ── Citizen ─────────────────────────────────────────────
                .requestMatchers("/api/client/**").hasRole("CITIZEN")

                // ── Staff trở lên ────────────────────────────────────────
                .requestMatchers("/api/admin/**")
                    .hasAnyRole("STAFF", "MANAGER", "SUPER_ADMIN")

                // Tất cả còn lại phải authenticate
                .anyRequest().authenticated()
            )

            // JWT filter chạy trước UsernamePasswordAuthenticationFilter
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // Không có config này: Spring Security trả 403 cho CẢ HAI trường hợp
            //   - Không có token / token không hợp lệ (unauthenticated) → nên là 401
            //   - Có token nhưng không đủ quyền (unauthorized) → vẫn là 403
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }

    // ----------------------------------------------------------------
    // Filter chain cho Thymeleaf MVC (/, /auth/**, /admin/**)
    // ----------------------------------------------------------------

    @Bean
    public SecurityFilterChain mvcFilterChain(HttpSecurity http) throws Exception {
        http
            // Áp dụng cho tất cả route còn lại (MVC)
            .securityMatcher("/**")

            // Session-based — Thymeleaf SSR đọc access token từ HttpSession
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            // CSRF bật cho Thymeleaf form (Spring Security tự inject csrf token)
            // csrf mặc định là enabled — không cần ghi tường minh

            .authorizeHttpRequests(auth -> auth
                // ── Static resources ─────────────────────────────────────
                .requestMatchers(
                    "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()

                // ── Error pages ──────────────────────────────────────────
                .requestMatchers("/error/**", "/error").permitAll()

                // ── Auth pages (login/register) ──────────────────────────
                .requestMatchers(
                    "/auth/login", "/auth/register",
                    "/admin/login").permitAll()

                // ── Swagger (chỉ dev — prod tắt qua springdoc.api-docs.enabled) ──
                .requestMatchers(
                    "/swagger-ui.html", "/swagger-ui/**",
                    "/api-docs/**", "/v3/api-docs/**").permitAll()

                // ── Actuator ─────────────────────────────────────────────
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // ── Admin MVC ────────────────────────────────────────────
                .requestMatchers("/admin/**")
                    .hasAnyRole("STAFF", "MANAGER", "SUPER_ADMIN")

                // Tất cả còn lại cần đăng nhập
                .anyRequest().authenticated()
            )

            // Redirect về login page khi chưa authenticate
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login")
                .usernameParameter("email")     // form uses name="email"
                .defaultSuccessUrl("/", true)
                .failureUrl("/auth/login?error=true")
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/auth/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("refresh_token")
                .permitAll()
            )

            // JWT filter cũng chạy trên MVC chain để đọc token từ session
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ----------------------------------------------------------------
    // Beans
    // ----------------------------------------------------------------

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt cost factor 12 — cân bằng giữa bảo mật và hiệu năng
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Spring Security 6.2+: UserDetailsService bắt buộc qua constructor
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}

