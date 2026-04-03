# 🔐 Toàn bộ flow xây dựng trang Login

---

## Tổng quan kiến trúc

```
Browser                     Spring MVC (Thymeleaf)           Spring Security / JWT
   │                               │                                    │
   │  GET /auth/login              │                                    │
   │──────────────────────────────▶│ AuthViewController.loginPage()     │
   │◀────────────────── login.html │                                    │
   │                               │                                    │
   │  POST /auth/login             │                                    │
   │  (email + password + CSRF)    │                                    │
   │──────────────────────────────────────────────────────────────────▶│
   │                               │              DaoAuthenticationProvider
   │                               │              → CustomUserDetailsService
   │                               │              → BCrypt verify password
   │                               │              → redirect /  hoặc /auth/login?error=true
   │◀──────────────────────────────│                                    │
```

---

## BƯỚC 1 — DTO: Định nghĩa dữ liệu vào/ra

### 1.1 `LoginRequest` — Input

```
src/main/java/com/psms/dto/request/LoginRequest.java
```

```java
@NotBlank(message = "{validation.email.required}")
@Email(message = "{validation.email.invalid}")
private String email;

@NotBlank(message = "{validation.password.required}")
private String password;
```

**Điểm quan trọng:**
- Dùng `{validation.xxx}` → Spring đọc từ `messages.properties`
- `@Email` chỉ kiểm tra format, KHÔNG kiểm tra email có tồn tại không (lý do bảo mật)

### 1.2 `AuthResponse` — Output

```
src/main/java/com/psms/dto/response/AuthResponse.java
```

| Field | Ý nghĩa |
|---|---|
| `accessToken` | JWT Bearer token, TTL ngắn (~15 phút) |
| `tokenType` | Luôn là `"Bearer"` |
| `expiresIn` | Số giây token còn sống (client dùng để schedule refresh) |
| `email` | Email user vừa đăng nhập |
| `fullName` | Tên hiển thị |
| `roles` | Danh sách role: `["CITIZEN"]` hoặc `["STAFF", "MANAGER"]` |

---

## BƯỚC 2 — Security Config: Hai filter chain song song

```
src/main/java/com/psms/config/SecurityConfig.java
```

### Tại sao cần **2 filter chain**?

| Chain | Route | Session | CSRF | Auth |
|---|---|---|---|---|
| `apiFilterChain` | `/api/**` | Stateless | ❌ Disabled | JWT Bearer |
| `mvcFilterChain` | `/**` | Session | ✅ Enabled | Form Login |

**Login page dùng `mvcFilterChain`:**

```java
.formLogin(form -> form
    .loginPage("/auth/login")           // ← GET: hiển thị form
    .loginProcessingUrl("/auth/login")  // ← POST: Spring Security xử lý
    .usernameParameter("email")         // ← input name="email"
    .defaultSuccessUrl("/", true)       // ← đăng nhập OK → về trang chủ
    .failureUrl("/auth/login?error=true") // ← sai pass → quay lại form
    .permitAll()
)
```

> **Key insight:** Spring Security tự xử lý `POST /auth/login` — controller **KHÔNG** cần viết `@PostMapping("/auth/login")`. Đây là "magic" của `formLogin()`.

---

## BƯỚC 3 — CustomUserDetailsService: Kết nối DB với Spring Security

```
src/main/java/com/psms/service/CustomUserDetailsService.java
```

Spring Security cần interface `UserDetailsService` để biết cách load user từ DB:

```
Spring Security                   CustomUserDetailsService           DB
     │                                      │                         │
     │  loadUserByUsername(email)           │                         │
     │─────────────────────────────────────▶│                         │
     │                                      │  userRepository         │
     │                                      │  .findWithRolesByEmail()│
     │                                      │─────────────────────────▶
     │                                      │◀─────────────────────────
     │                                      │  → UserDetails          │
     │◀─────────────────────────────────────│    (email, password,    │
     │                                      │     authorities/roles)  │
```

`DaoAuthenticationProvider` sẽ:
1. Gọi `loadUserByUsername(email)` → lấy `UserDetails`
2. Dùng `BCryptPasswordEncoder` để `matches(rawPassword, encodedPassword)`
3. Nếu OK → tạo `Authentication` object → lưu vào `SecurityContextHolder`

---

## BƯỚC 4 — JwtTokenProvider: Tạo JWT sau khi authenticate thành công

```
src/main/java/com/psms/util/JwtTokenProvider.java
```

```
Access Token structure (JWT):
┌─────────────────┬─────────────────────────────────────┬──────────┐
│     Header      │              Payload                 │ Signature│
│  { alg: HS256 } │ sub=email  jti=UUID  iat=now        │  HMAC    │
│                 │ exp=now+15min                        │  SHA256  │
└─────────────────┴─────────────────────────────────────┴──────────┘
```

**Tại sao cần `jti` (JWT ID)?**

JWT stateless → không thể "xóa" token khi logout. Giải pháp:
- Gán mỗi token 1 UUID unique (`jti`)
- Khi logout → lưu `jti` vào bảng `revoked_access_tokens`
- Mỗi request: `JwtAuthenticationFilter` kiểm tra `jti` trong blacklist

---

## BƯỚC 5 — AuthService.login(): Business logic đăng nhập

```
src/main/java/com/psms/service/AuthService.java
```

```
AuthService.login() flow:
┌─────────────────────────────────────────────────────────────┐
│ 1. authenticationManager.authenticate(email, password)      │
│    → Spring Security xác thực, ném exception nếu sai       │
│                                                             │
│ 2. Kiểm tra role (nếu requiredRoles không rỗng)            │
│    → Admin portal cần STAFF/MANAGER/SUPER_ADMIN            │
│                                                             │
│ 3. jwtTokenProvider.generateAccessToken(user)               │
│    → JWT với jti, sub=email, exp=now+15min                  │
│                                                             │
│ 4. refreshTokenService.create(user)                         │
│    → Lưu refresh token vào DB (refresh_tokens table)       │
│                                                             │
│ 5. session.setAttribute("ACCESS_TOKEN", accessToken)        │
│    → Thymeleaf SSR đọc từ session khi gọi API nội bộ       │
│                                                             │
│ 6. setRefreshTokenCookie(response, ...)                     │
│    → HttpOnly; Secure; SameSite=Strict cookie               │
│                                                             │
│ 7. user.setLastLoginAt(now) + reset failedLoginCount = 0    │
│    → Cập nhật DB                                           │
│                                                             │
│ 8. return AuthResponse                                      │
└─────────────────────────────────────────────────────────────┘
```

> **Lưu ý:** `AuthService.login()` dùng chung cho cả citizen lẫn admin (REST API).
> Citizen portal: `requiredRoles = List.of()` (không giới hạn).
> Admin portal: `requiredRoles = List.of(STAFF, MANAGER, SUPER_ADMIN)`.

---

## BƯỚC 6 — JwtAuthenticationFilter: Bảo vệ mọi request sau login

```
src/main/java/com/psms/config/JwtAuthenticationFilter.java
```

```
Mỗi request vào /api/**:
                                             ┌─────────────────────┐
Request → Filter → Extract "Bearer <token>" │  Có token?          │
                                             └────────┬────────────┘
                                                      │ Có
                                             ┌────────▼────────────┐
                                             │ isTokenValid()?     │ Không → 401
                                             └────────┬────────────┘
                                                      │ Có
                                             ┌────────▼────────────┐
                                             │ isRevoked(jti)?     │ Bị revoke → 401
                                             └────────┬────────────┘
                                                      │ Không bị revoke
                                             ┌────────▼────────────┐
                                             │ loadUserByUsername() │
                                             │ set SecurityContext  │
                                             └────────┬────────────┘
                                                      │
                                                  ✅ Request đi tiếp
```

---

## BƯỚC 7 — AuthViewController: Render trang HTML (GET)

```
src/main/java/com/psms/controller/view/AuthViewController.java
```

```java
@GetMapping("/auth/login")
public String loginPage(Authentication authentication) {
    // Đã login rồi → không cần xem form nữa
    if (authentication != null && authentication.isAuthenticated()) {
        return "redirect:/";
    }
    return "auth/login";  // → render templates/auth/login.html
}
```

**Nguyên tắc:** View controller chỉ render template, KHÔNG có business logic.

---

## BƯỚC 8 — login.html: Giao diện trang đăng nhập

```
src/main/resources/templates/auth/login.html
```

```html
<!--
  Spring Security tự xử lý POST /auth/login.
  th:action tự inject CSRF token (hidden input).
-->
<form th:action="@{/auth/login}" method="post">

    <input type="email" name="email" ...>     <!-- usernameParameter="email" -->
    <input type="password" name="password" ...>

    <button type="submit">Đăng nhập</button>
</form>
```

**Xử lý thông báo lỗi/thành công từ URL params:**

| Condition | URL | Hiển thị |
|---|---|---|
| Sai password | `?error=true` | "Email hoặc mật khẩu không đúng" |
| Đăng xuất xong | `?logout=true` | "Bạn đã đăng xuất thành công" |
| Vừa đăng ký | Flash attribute | "Đăng ký thành công!" |

---

## BƯỚC 9 — AuthController (REST API): Dành cho client mobile/SPA

```
src/main/java/com/psms/controller/client/AuthController.java
```

> ⚠️ **Quan trọng:** Đây là **REST API** endpoint, KHÁC với Thymeleaf form login.
> Swagger UI dùng endpoint này để test.

| Endpoint | Mô tả |
|---|---|
| `POST /api/auth/register` | Đăng ký tài khoản mới |
| `POST /api/auth/login` | Đăng nhập, nhận JWT |
| `POST /api/auth/refresh-token` | Làm mới access token (Token Rotation) |
| `POST /api/auth/logout` | Logout, blacklist JWT |

---

## Sơ đồ tổng hợp: Login flow hoàn chỉnh

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        THYMELEAF SSR (Browser)                          │
│                                                                         │
│  1. GET /auth/login                                                     │
│  ──────────────────▶ AuthViewController.loginPage()                     │
│                      └─▶ return "auth/login" (templates/auth/login.html)│
│                                                                         │
│  2. User nhập email + password + click Submit                           │
│                                                                         │
│  3. POST /auth/login (form submit, có CSRF token)                       │
│  ──────────────────▶ Spring Security intercepts (formLogin config)      │
│                      └─▶ DaoAuthenticationProvider                      │
│                           ├─▶ CustomUserDetailsService.loadUser()       │
│                           │   └─▶ UserRepository.findWithRolesByEmail() │
│                           └─▶ BCryptPasswordEncoder.matches()           │
│                                                                         │
│  4a. Thành công ──▶ defaultSuccessUrl("/")                              │
│  4b. Thất bại   ──▶ failureUrl("/auth/login?error=true")                │
│                      └─▶ login.html hiển thị lỗi                       │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                         REST API (Swagger / Mobile)                     │
│                                                                         │
│  POST /api/auth/login { email, password }                               │
│  ──────────────────▶ AuthController.login()                             │
│                      └─▶ AuthService.login()                            │
│                           ├─▶ authenticationManager.authenticate()      │
│                           ├─▶ JwtTokenProvider.generateAccessToken()    │
│                           ├─▶ RefreshTokenService.create()              │
│                           ├─▶ session.setAttribute("ACCESS_TOKEN", ...) │
│                           ├─▶ setRefreshTokenCookie (HttpOnly)          │
│                           └─▶ return AuthResponse { accessToken, ... }  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Tóm tắt file liên quan theo thứ tự

| # | File | Vai trò |
|---|---|---|
| 1 | `dto/request/LoginRequest.java` | Validate input (email, password) |
| 2 | `dto/response/AuthResponse.java` | Cấu trúc response (accessToken, roles...) |
| 3 | `config/SecurityConfig.java` | Cấu hình form login, URL rules, 2 filter chain |
| 4 | `service/CustomUserDetailsService.java` | Load user từ DB cho Spring Security |
| 5 | `util/JwtTokenProvider.java` | Sinh + validate JWT access/refresh token |
| 6 | `service/RefreshTokenService.java` | Lưu/rotate refresh token trong DB |
| 7 | `service/RevokedTokenService.java` | Blacklist JWT jti sau logout |
| 8 | `config/JwtAuthenticationFilter.java` | Kiểm tra JWT trên mỗi request /api/** |
| 9 | `service/AuthService.java` | Business logic: login, register, refresh, logout |
| 10 | `controller/view/AuthViewController.java` | GET /auth/login → render HTML |
| 11 | `controller/view/AuthMvcController.java` | POST /auth/register, POST /admin/login |
| 12 | `controller/client/AuthController.java` | REST API: /api/auth/** |
| 13 | `templates/auth/login.html` | Giao diện form đăng nhập |
