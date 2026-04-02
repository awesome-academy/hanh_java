package com.psms.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psms.dto.response.ApiResponse;
import com.psms.entity.RefreshToken;
import com.psms.entity.Role;
import com.psms.entity.User;
import com.psms.enums.RoleName;
import com.psms.exception.BusinessException;
import com.psms.repository.CitizenRepository;
import com.psms.repository.RefreshTokenRepository;
import com.psms.repository.RevokedAccessTokenRepository;
import com.psms.repository.RoleRepository;
import com.psms.repository.UserRepository;
import com.psms.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test cho toàn bộ auth flow: register, login, role access,
 * token rotation, logout + blacklist.
 *
 * <p>Dùng H2 in-memory (profile=test), full Spring context (không mock service).
 * Mỗi test method sử dụng email riêng để tránh conflict.
 *
 * <p>Covers: #03-19, #03-20, #03-21, #03-22
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthControllerIntegrationTest {

    // MockMvc build thủ công — @AutoConfigureMockMvc đã tách module riêng trong Spring Boot 4.x
    private MockMvc mockMvc;

    // ObjectMapper với Java 8 time module để parse LocalDateTime trong ApiResponse.timestamp
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CitizenRepository citizenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RevokedAccessTokenRepository revokedTokenRepository;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private PasswordEncoder passwordEncoder;

    // ----------------------------------------------------------------
    // Setup
    // ----------------------------------------------------------------

    @BeforeAll
    void setUp() {
        // Build MockMvc với springSecurity() để đảm bảo filter chain chạy đúng
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Seed các role cần thiết — chỉ insert nếu chưa có
        for (RoleName name : RoleName.values()) {
            if (roleRepository.findByName(name).isEmpty()) {
                Role role = new Role();
                role.setName(name);
                role.setDescription(name.name());
                roleRepository.save(role);
            }
        }
    }

    @BeforeEach
    void cleanupUsers() {
        // Xóa theo thứ tự FK dependency
        refreshTokenRepository.deleteAll();
        revokedTokenRepository.deleteAll();
        citizenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ----------------------------------------------------------------
    // #03-19: register → login → access protected endpoint
    // ----------------------------------------------------------------

    @Test
    @DisplayName("#03-19: register → login → logout (endpoint bảo vệ) thành công")
    void registerLoginAccessProtected() throws Exception {
        // Step 1: Register → 201
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterJson("test19@example.com", "001234567890")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // Step 2: Login → 200, accessToken trong body
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginJson("test19@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.roles[0]").value("CITIZEN"))
                .andReturn();

        String accessToken = extractAccessToken(loginResult);
        assertThat(accessToken).isNotBlank();

        // Step 3: Logout với Bearer token → 200 (chứng minh token hợp lệ và auth thành công)
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("#03-19: Đăng ký email trùng → 400 với thông báo lỗi")
    void register_duplicateEmail_shouldReturn400() throws Exception {
        // Register lần 1
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterJson("dup@example.com", "001234567891")))
                .andExpect(status().isCreated());

        // Register lần 2 cùng email → 400
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterJson("dup@example.com", "001234567892")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("#03-19: Login sai password → 401")
    void login_wrongPassword_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterJson("wrong@example.com", "001234567893")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginJson("wrong@example.com", "WrongPassword!99")))
                .andExpect(status().isUnauthorized());
    }

    // ----------------------------------------------------------------
    // #03-20: Role access control
    // ----------------------------------------------------------------

    @Test
    @DisplayName("#03-20a: Không có token → /api/admin/** → 401")
    void noToken_adminEndpoint_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("#03-20b: Không có token → /api/auth/logout (protected) → 401")
    void noToken_protectedEndpoint_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("#03-20c: CITIZEN token → /api/admin/** → 403 Forbidden")
    void citizenToken_adminRoute_shouldReturn403() throws Exception {
        // Register + login as CITIZEN
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterJson("citizen@example.com", "001234567894")))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginJson("citizen@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        String citizenToken = extractAccessToken(loginResult);

        // CITIZEN token → admin route → 403
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("#03-20d: Token giả mạo (invalid signature) → 401")
    void fakeToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer fake.invalid.token"))
                .andExpect(status().isUnauthorized());
    }

    // ----------------------------------------------------------------
    // #03-21: Token Rotation
    // ----------------------------------------------------------------

    @Test
    @DisplayName("#03-21a: HTTP — Token Rotation thành công, response chứa access token mới")
    void tokenRotation_http_firstRotateSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterJson("rotate1@example.com", "001234567895")))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginJson("rotate1@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isNotBlank();

        // Rotate → 200, access token mới trong body
        MvcResult rotateResult = mockMvc.perform(post("/api/auth/refresh-token")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        String newAccessToken = extractAccessToken(rotateResult);
        assertThat(newAccessToken).isNotBlank();
    }

    @Test
    @DisplayName("#03-21b: Service — Reuse Detection: rotate token cũ lần 2 → BusinessException")
    void tokenRotation_service_reuseOldToken_throwsBusinessException() {
        // Tạo user trực tiếp qua repository (tránh HTTP layer để test service thuần túy)
        User user = buildTestUser("service-rotate@example.com");
        userRepository.save(user);

        // Tạo refresh token
        RefreshToken token = refreshTokenService.create(user);
        String originalValue = token.getToken();

        // Rotate lần 1 → thành công
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(originalValue);
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(originalValue);

        // Rotate lần 2 với token cũ → Reuse Detection → BusinessException
        assertThrows(BusinessException.class,
                () -> refreshTokenService.rotate(originalValue),
                "Dùng lại refresh token cũ phải ném BusinessException");
    }

    @Test
    @DisplayName("#03-21c: Refresh token không có cookie → 400 Bad Request")
    void tokenRotation_noCookie_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token"))
                .andExpect(status().isBadRequest());
    }


    // ----------------------------------------------------------------
    // #03-22: Logout → access token bị blacklist → 401
    // ----------------------------------------------------------------

    @Test
    @DisplayName("#03-22: Logout → dùng lại access token cũ → 401 (blacklisted)")
    void logout_thenReuseAccessToken_shouldReturn401() throws Exception {
        // Register + Login
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterJson("logout@example.com", "001234567896")))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginJson("logout@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractAccessToken(loginResult);
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");

        // Logout với access token → jti bị insert vào revoked_access_tokens
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(refreshCookie != null ? refreshCookie : new Cookie("x", "x")))
                .andExpect(status().isOk());

        // Kiểm tra DB: revoked_access_tokens có JTI
        assertThat(revokedTokenRepository.count()).isGreaterThan(0);

        // Dùng lại access token đã logout → JwtAuthenticationFilter phát hiện JTI bị revoke
        // → không set SecurityContext → Spring Security trả 401
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("#03-22: Logout → refresh token bị xóa khỏi DB")
    void logout_refreshTokenRemovedFromDB() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterJson("logout2@example.com", "001234567897")))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginJson("logout2@example.com")))
                .andExpect(status().isOk())
                .andReturn();

        // Sau login: có 1 refresh token trong DB
        assertThat(refreshTokenRepository.count()).isEqualTo(1);

        String accessToken = extractAccessToken(loginResult);
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");

        // Logout → refresh token bị xóa
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(refreshCookie != null ? refreshCookie : new Cookie("x", "x")))
                .andExpect(status().isOk());

        assertThat(refreshTokenRepository.count()).isEqualTo(0);
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private String buildRegisterJson(String email, String nationalId) {
        return """
                {
                    "fullName": "Test User",
                    "email": "%s",
                    "password": "Test@1234",
                    "nationalId": "%s",
                    "dateOfBirth": "1990-01-01",
                    "gender": "MALE"
                }
                """.formatted(email, nationalId);
    }

    private String buildLoginJson(String email) {
        return buildLoginJson(email, "Test@1234");
    }

    private String buildLoginJson(String email, String password) {
        return """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, password);
    }

    /** Tạo User entity trực tiếp (không qua HTTP) — dùng cho service-level tests */
    private User buildTestUser(String email) {
        Role citizenRole = roleRepository.findByName(RoleName.CITIZEN)
                .orElseThrow(() -> new IllegalStateException("CITIZEN role chưa được seed"));
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Test@1234"));
        user.setFullName("Test User");
        user.setRoles(Set.of(citizenRole));
        return user;
    }

    @SuppressWarnings("unchecked")
    private String extractAccessToken(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        ApiResponse<Map<String, Object>> response = objectMapper.readValue(
                body, new TypeReference<>() {});
        return (String) response.getData().get("accessToken");
    }
}

