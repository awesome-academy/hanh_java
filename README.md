# PSMS — Public Service Management System

Hệ thống quản lý dịch vụ công trực tuyến, xây dựng trên **Spring Boot 4 · Thymeleaf SSR · MySQL 8 · JWT**.

---

## Mục lục

1. [Kiến trúc tổng quan](#kiến-trúc-tổng-quan)
2. [Yêu cầu hệ thống](#yêu-cầu-hệ-thống)
3. [Cài đặt và chạy local](#cài-đặt-và-chạy-local)
4. [Biến môi trường](#biến-môi-trường)
5. [Tài khoản mặc định (seed)](#tài-khoản-mặc-định-seed)
6. [Chạy tests](#chạy-tests)
7. [API Documentation (Swagger)](#api-documentation-swagger)
8. [Postman Collection](#postman-collection)
9. [Docker](#docker)
10. [Cấu trúc project](#cấu-trúc-project)

---

## Kiến trúc tổng quan

```
Browser ──► Thymeleaf MVC (SSR)      ──► Admin / Citizen HTML pages
          ──► REST API /api/**        ──► JSON responses
                  │
          Spring Security (dual-layer)
          ├── apiFilterChain   (@Order 1)  /api/**     STATELESS · JWT Bearer
          └── mvcFilterChain   (@Order 2)  /**         SESSION   · CSRF enabled
                  │
          RateLimitFilter (auth endpoints) → JwtAuthenticationFilter
                  │
          Service Layer → JPA Repositories (@EntityGraph) → MySQL 8
```

**Roles:**
| Role | Mô tả | Truy cập |
|---|---|---|
| `CITIZEN` | Công dân | Portal công dân (`/`, `/services`, `/applications`, `/profile`) |
| `STAFF` | Cán bộ | Admin (`/admin/**`) — xem + xử lý hồ sơ |
| `MANAGER` | Trưởng phòng | Admin — quản lý cán bộ, dịch vụ, phòng ban + Staff |
| `SUPER_ADMIN` | Quản trị viên | Toàn quyền — CRUD users, log, purge |

---

## Yêu cầu hệ thống

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (hoặc dùng `./mvnw`) |
| MySQL | 8.0+ |
| Docker (optional) | 24+ |

---

## Cài đặt và chạy local

### 1. Clone & khởi tạo DB

```bash
git clone <repo-url>
cd psms

# Tạo database và schema
mysql -u root -p -e "CREATE DATABASE psms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p psms < psms_schema.sql

# (Tùy chọn) Seed dữ liệu mẫu
mysql -u root -p psms < docs/seeds/psms_seed.sql
mysql -u root -p psms < docs/seeds/service_types.sql
```

### 2. Cấu hình biến môi trường

```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/psms?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true"
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=your_password
export JWT_SECRET=your-secret-key-at-least-256-bits-long-please-change-this
```

### 3. Chạy ứng dụng

```bash
# Dev mode (SQL logging bật, Thymeleaf cache tắt, Swagger bật)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Production mode
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

Ứng dụng chạy tại: **http://localhost:8080**

---

## Biến môi trường

| Biến | Mô tả | Default (dev) |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL MySQL | `jdbc:mysql://localhost:3306/psms?...` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `root` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `Aa@123456` |
| `JWT_SECRET` | Secret key ký JWT (≥256 bits) | `dev-secret-key-change-this-...` |
| `APP_UPLOAD_DIR` | Thư mục lưu file upload | `./uploads` |
| `APP_BASE_URL` | Base URL cho file download links | `http://localhost:8080` |
| `SPRING_MAIL_HOST` | SMTP host | — |
| `SPRING_MAIL_USERNAME` | SMTP username | — |
| `SPRING_MAIL_PASSWORD` | SMTP password | — |

> ⚠️ **Production:** Thay `JWT_SECRET` bằng giá trị ngẫu nhiên ≥ 32 ký tự. Không commit secret vào git.

---

## Tài khoản mặc định (seed)

Sau khi chạy `docs/seeds/psms_seed.sql`:

| Email | Password | Role |
|---|---|---|
| `superadmin@psms.local` | `Admin@123` | `SUPER_ADMIN` |
| `manager@psms.local` | `Admin@123` | `MANAGER` |
| `staff@psms.local` | `Admin@123` | `STAFF` |
| `citizen@psms.local` | `Citizen@123` | `CITIZEN` |

---

## Chạy tests

```bash
# Chạy tất cả tests
./mvnw test

# Chạy một test class cụ thể
./mvnw test -Dtest=AdminApplicationServiceTest

# Chạy với output chi tiết
./mvnw test -Dsurefire.useFile=false

# Build không chạy test
./mvnw clean package -DskipTests
```

Test stack: **JUnit 5 · Mockito · H2 in-memory · @WebMvcTest · @DataJpaTest**

Hiện có **138+ tests** covering:
- State machine transitions (valid + invalid)
- Service layer (AdminUserService, AdminApplicationService, DocumentService, v.v.)
- N+1 protection với @EntityGraph
- CSV Import/Export (partial failure handling)
- ActivityLog AOP aspect

---

## API Documentation (Swagger)

Chỉ bật trên profile `dev`:

```
http://localhost:8080/swagger-ui.html
```

Có 2 API groups:
- **Client API** — `/api/client/**` — dành cho CITIZEN
- **Admin API** — `/api/admin/**` — dành cho STAFF / MANAGER / SUPER_ADMIN

---

## Postman Collection

Import file `psms-collection.json` vào Postman:

1. Mở Postman → **Import** → chọn file `psms-collection.json`
2. Tạo Environment với các variables:
   - `base_url`: `http://localhost:8080`
   - `citizen_token`: (điền sau khi login)
   - `admin_token`: (điền sau khi login)
3. Chạy request **POST Login (Citizen)** → copy `accessToken` → set vào `citizen_token`

---

## Docker

```bash
# Khởi động app + MySQL
docker-compose up -d

# Xem logs
docker-compose logs -f app

# Tắt
docker-compose down

# Reset hoàn toàn (xóa cả data DB)
docker-compose down -v
```

---

## Cấu trúc project

```
src/main/java/com/psms/
├── annotation/       # @LogActivity — AOP activity logging
├── aop/              # ActivityLogAspect
├── config/           # SecurityConfig (dual-layer), JwtProperties, FileStorageProperties
├── security/         # JwtAuthenticationFilter, RateLimitFilter (brute-force protection)
├── scheduler/        # TokenCleanupScheduler (@Scheduled hourly)
├── controller/
│   ├── client/       # REST /api/client/**   (CITIZEN)
│   ├── admin/        # REST /api/admin/**    (STAFF / MANAGER / SUPER_ADMIN)
│   └── web/          # MVC → Thymeleaf HTML  (PRG pattern)
├── service/          # Business logic, @Transactional(readOnly=true) class-level
├── repository/       # JPA + @EntityGraph (N+1 protection)
├── entity/base/      # BaseEntity, LongBaseEntity, AuditableLongEntity
├── dto/              # request/ + response/
├── mapper/           # MapStruct (MapStructCentralConfig base)
├── exception/        # GlobalExceptionHandler, BusinessException, ResourceNotFoundException
├── util/             # ApplicationCodeGenerator (HS-YYYYMMDD-NNNNN, thread-safe)
└── enums/            # ApplicationStatus, RoleName, ActionType, ExportType, ImportType

src/main/resources/
├── templates/        # layout/ auth/ client/ admin/
├── static/css/       # base.css, layout.css, components.css, client.css, admin.css
├── static/js/        # main.js, client.js, admin.js, admin-dept.js, admin-service.js, admin-staff.js
└── messages.properties  # Validation messages (tiếng Việt)
```

### Key patterns

| Pattern | Mô tả |
|---|---|
| **PRG** | MVC POST → xử lý → redirect với flash message (RedirectAttributes) |
| **State machine** | `ApplicationStateMachine.isValidTransition()` bắt buộc khi update status |
| **Soft delete** | `ApplicationDocument.isDeleted` — file giữ lại cho audit trail |
| **IDOR protection** | `findByIdAndCitizenId()` — kết hợp id + ownerId |
| **@EntityGraph** | Eager-fetch relations theo context, tránh N+1 |
| **AOP Activity Log** | `@LogActivity` → `ActivityLogAspect` ghi log sau method thành công |
| **Rate Limiting** | `RateLimitFilter` — 10 req/phút/IP trên auth endpoints |

---

## Lưu ý bảo mật

- JWT secret phải ≥ 256 bits và random — bắt buộc đổi trước khi deploy production
- Rate limiter: 10 req/phút/IP trên `/api/auth/login`, `/api/auth/register`, `/api/admin/auth/login`
- Refresh token rotation: dùng lại token cũ → revoke toàn bộ session user
- File upload: chỉ `.pdf .jpg .jpeg .png .docx`, max 10MB, path traversal protection
- Tất cả admin route đều có `@PreAuthorize` ở class hoặc method level
