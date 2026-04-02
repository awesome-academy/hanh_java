# TASKS_v3.md — PSMS Feature Branch Plan
> **Phiên bản:** 3.0 — Feature Branch Workflow
> **Mô hình:** Mỗi branch = 1 PR merge vào `main`
> **Cập nhật:** 2026-03-30

---

## Tổng quan các PR

| # | Branch | Nội dung | Iteration | Phụ thuộc |
|---|---|---|---|---|
| #01 | `task/init-project` | Project setup, config, Docker | I1 | — |
| #02 | `task/entities-repositories` | Schema, Entity, Repository, Mapper | I1 | #01 |
| #03 | `feature/security-auth` | JWT, Security, Login/Register API + UI | I1 | #02 |
| #04 | `feature/base-layout` | Layout Thymeleaf, CSS base, JS utils | I1 | #03 |
| #05 | `feature/client-service-catalog` | Trang chủ, Danh mục dịch vụ, Chi tiết DV | I1 | #04 |
| #06 | `feature/client-application` | Nộp hồ sơ, Danh sách HS, Chi tiết HS + Timeline | I1 | #05 |
| #07 | `feature/admin-dashboard-application` | Dashboard, Quản lý hồ sơ + State machine | I1 | #06 |
| #08 | `feature/file-upload` | Schema bổ sung, Upload/Download tài liệu | I2 | #07 |
| #09 | `feature/client-profile-notification` | Profile, Đổi mật khẩu, Thông báo + Badge | I2 | #08 |
| #10 | `feature/admin-user-management` | CRUD User, Phân quyền, Khóa/Mở TK | I2 | #07 |
| #11 | `feature/admin-service-department-staff` | CRUD Service, Department, Staff Assignment | I2 | #10 |
| #12 | `feature/notification-email-logging` | Email async, AOP Activity Log, Notification Service | I2 | #08 |
| #13 | `feature/admin-activity-log-dashboard` | Admin Log UI, Dashboard đầy đủ | I2 | #11 #12 |
| #14 | `feature/import-export-csv` | Export/Import CSV 5 loại | I3 | #13 |
| #15 | `task/testing-hardening-cicd` | Test coverage, Security, Responsive, CI/CD | I3 | #14 |

---

## Chi tiết từng branch

---

### #01 · `task/init-project`
> **Mục tiêu:** Project chạy được, kết nối DB, Swagger hiển thị
> **Base:** `main`

- [x] `#01-01` Khởi tạo Spring Boot project (pom.xml đầy đủ dependencies)
- [x] `#01-02` Cấu hình `application.yml` + `application-dev.yml` + `application-prod.yml`
- [x] `#01-03` Viết `Dockerfile` + `docker-compose.yml` (app + mysql)
- [x] `#01-04` Cấu hình `GlobalExceptionHandler` + `ApiResponse<T>` wrapper
- [x] `#01-05` Thêm error pages: `404.html`, `403.html`, `500.html`
- [x] `#01-06` Cấu hình Swagger UI (chỉ bật dev, 2 groups: client + admin)
- [x] `#01-07` Tạo Postman Environment (`base_url`, `citizen_token`, `admin_token`)

**Definition of Done:**
```
□ ./mvnw spring-boot:run → khởi động OK, không lỗi
□ docker-compose up → app + mysql start thành công
□ localhost:8080/swagger-ui.html → hiển thị 2 groups
```

---

### #02 · `task/entities-repositories`
> **Mục tiêu:** Toàn bộ JPA entity, repository, mapper sẵn sàng
> **Base:** `task/init-project`

**Schema & Seed**
- [x] `#02-01` Chạy `psms_schema.sql` vào MySQL (15 bảng + indexes)
- [x] `#02-02` Chạy `psms_seed.sql` — 4 roles, 5 departments, 6 service types, 1 admin, 2 citizens mẫu

**Entities — Domain 1 (Auth)**
- [x] `#02-03` Entity `Role` + Enum `RoleName`
- [x] `#02-04` Entity `User` implement `UserDetails` — field: `email`, `fullName`, `password`, `isActive`, `isLocked`
- [x] `#02-05` Entity `Citizen` (1-1 User) + Entity `Department` + Entity `Staff`

**Entities — Domain 2 (Services)**
- [x] `#02-06` Entity `ServiceCategory` + Entity `ServiceType`

**Entities — Domain 3 (Applications)**
- [x] `#02-07` Entity `Application` + Enum `ApplicationStatus` (7 trạng thái)
- [x] `#02-08` Entity `ApplicationStatusHistory`

**Repositories & Mappers**
- [x] `#02-09` Repositories cho tất cả entities (extend `JpaRepository` + `JpaSpecificationExecutor`)
- [x] `#02-10` MapStruct mappers: `UserMapper`, `CitizenMapper`, `ApplicationMapper`, `ServiceTypeMapper`
- [x] `#02-11` `ApplicationCodeGenerator` — sinh mã `HS-{YYYYMMDD}-{5 số}`, thread-safe

**Test**
- [x] `#02-12` `@DataJpaTest` cho `ApplicationRepository` — filter by status, citizen, date range

**Definition of Done:**
```
□ ./mvnw compile → BUILD SUCCESS (validate schema khớp entity)
□ DataJpaTest xanh
□ Không có FetchType.EAGER, không có @Data trên entity
```

---

### #03 · `feature/security-auth`
> **Mục tiêu:** Login/Register hoạt động end-to-end, JWT hợp lệ, phân quyền đúng
> **Base:** `task/entities-repositories`

**Backend**
- [x] `#03-01` `JwtTokenProvider` (generate accessToken 1h + `jti` claim, refreshToken 7d, validate, extract)
- [x] `#03-02` `JwtAuthenticationFilter` (`OncePerRequestFilter`) — check `revoked_access_tokens` khi validate
- [x] `#03-03` `SecurityConfig` — URL rules đầy đủ theo SPECS.md Section 8
- [x] `#03-04` `POST /api/auth/register` — tạo User + Citizen, gán role CITIZEN
- [x] `#03-05` `POST /api/auth/login` — trả accessToken + lưu HttpSession, set HttpOnly cookie cho refreshToken:
  - Lưu accessToken vào `HttpSession` (Thymeleaf SSR đọc được)
  - Set `HttpOnly; Secure; SameSite=Strict` cookie cho refreshToken
  - INSERT refreshToken vào `refresh_tokens` DB
  - Cập nhật `last_login_at`
- [x] `#03-06` `POST /api/admin/auth/login` — chỉ STAFF+, tương tự login citizen
- [x] `#03-07` `POST /api/auth/refresh-token` — **Token Rotation**:
  - Nhận refresh token → validate (tồn tại trong DB + chưa hết hạn)
  - Xóa token cũ → INSERT token mới → trả accessToken mới + refreshToken mới
  - Nếu token không tồn tại (đã dùng rồi) → revoke toàn bộ session user đó
- [x] `#03-08` `POST /api/auth/logout`:
  - Lấy `jti` từ accessToken trong HttpSession → INSERT vào `revoked_access_tokens`
  - Xóa refreshToken khỏi `refresh_tokens` (by user_id)
  - Invalidate `HttpSession`
  - Xóa HttpOnly refresh token cookie
- [x] `#03-09` `RevokedTokenService` — `revoke(jti, expiresAt)`, `isRevoked(jti)`
- [x] `#03-10` `RefreshTokenService` — `create(userId)`, `validate(token)`, `rotate(oldToken)`, `revokeAllByUser(userId)`
- [x] `#03-11` `@Scheduled` cleanup task (mỗi 1h):
  - `DELETE FROM refresh_tokens WHERE expires_at < NOW()`
  - `DELETE FROM revoked_access_tokens WHERE expires_at < NOW()`

**UI — Auth Pages**
- [x] `#03-12` CSS `base.css` — CSS variables, reset, typography
- [x] `#03-13` CSS `components.css` — button, input, form-group, badge/pill, alert, card
- [x] `#03-14` Template `auth/login.html` — form email+password, hiện lỗi `th:if="${error}"`
- [x] `#03-15` Template `auth/register.html` — form đầy đủ, `th:errors` từng field
- [x] `#03-16` Template `auth/admin-login.html` — layout riêng "Cổng quản trị nội bộ"
- [x] `#03-17` `AuthMvcController` — POST login/register/logout với PRG pattern
- [x] `#03-18` `AuthViewController` — GET /auth/login, /auth/register, /admin/login

**Test**
- [x] `#03-19` Auth flow: register → login → access protected endpoint
- [x] `#03-20` Role access: citizen → 403 admin route; no token → 401
- [x] `#03-21` Refresh token rotation: dùng token cũ sau khi rotate → 401
- [x] `#03-22` Logout → access token bị blacklist → 401 khi dùng lại
- [x] `#03-23` Cleanup task: tokens hết hạn bị xóa khỏi DB

**Definition of Done:**
```
□ Đăng ký → Đăng nhập → Redirect trang chủ thành công
□ Sai password → hiện lỗi, không crash
□ Citizen gọi /api/admin/** → 403
□ Không có token → 401
□ JWT secret đọc từ ${jwt.secret}
□ Logout → dùng lại access token cũ → 401
□ Dùng refresh token sau khi đã rotate → 401 + toàn bộ session bị revoke
□ DB: refresh_tokens và revoked_access_tokens được cleanup tự động
```
 
---

---

### #04 · `feature/base-layout`
> **Mục tiêu:** Layout client + admin hoàn chỉnh, flash message, navigation hoạt động
> **Base:** `feature/security-auth`

**CSS**
- [ ] `#04-01` `layout.css` — `.topbar`, `.sidebar`, `.page-content`, `.admin-shell`
- [ ] `#04-02` `client.css` — hero section, category grid, service card, stat box
- [ ] `#04-03` `admin.css` — KPI card, bar chart placeholder, log item, sidebar badge

**Thymeleaf Layout**
- [ ] `#04-04` `layout/client.html` — topbar (logo, nav links, notif bell, avatar), `layout:fragment="content"`
  - `sec:authorize`, `sec:authentication`, active nav state theo `${activeNav}`
- [ ] `#04-05` `layout/admin.html` — sidebar (menu items, pending badge), topbar breadcrumb, `layout:fragment="content"`
- [ ] `#04-06` `js/main.js` — `showToast()`, `confirmDialog()`, auto-hide flash sau 3s
- [ ] `#04-07` `js/client.js` — skeleton (sẽ bổ sung ở #06, #09)
- [ ] `#04-08` `js/admin.js` — skeleton (sẽ bổ sung ở #10, #13, #14)
- [ ] `#04-09` Flash message pattern trong cả 2 layout (success/error)

**Definition of Done:**
```
□ GET / → thấy topbar client đúng design
□ GET /admin/dashboard → thấy sidebar admin đúng
□ Flash message hiện và tự ẩn sau 3s
□ sec:authorize ẩn/hiện đúng theo role
```

---

### #05 · `feature/client-service-catalog`
> **Mục tiêu:** Citizen xem được trang chủ, danh sách và chi tiết dịch vụ
> **Base:** `feature/base-layout`

**Backend**
- [ ] `#05-01` `GET /api/client/service-categories` — danh sách lĩnh vực active
- [ ] `#05-02` `GET /api/client/services` — danh sách DV (phân trang, filter keyword + categoryId)
- [ ] `#05-03` `GET /api/client/services/{id}` — chi tiết dịch vụ

**UI**
- [ ] `#05-04` `ClientViewController.home()` + Template `client/home.html`:
  - Hero section (tiêu đề, search box, 3 stats)
  - Grid 8 lĩnh vực (icon + tên + số DV)
  - Danh sách 5 DV phổ biến
- [ ] `#05-05` `ClientViewController.serviceList()` + Template `client/service-list.html`:
  - Filter bar (keyword + dropdown lĩnh vực)
  - Danh sách DV theo row
  - Pagination giữ nguyên filter params
- [ ] `#05-06` `ClientViewController.serviceDetail()` + Template `client/service-detail.html`:
  - Tên, mô tả, yêu cầu hồ sơ, thời hạn, lệ phí, cơ quan
  - Button "Nộp hồ sơ ngay" → `/applications/submit?serviceId={id}`

**Definition of Done:**
```
□ Trang chủ hiển thị đúng 8 lĩnh vực từ DB
□ Filter keyword → URL update, kết quả đúng
□ Pagination hoạt động, giữ filter khi chuyển trang
□ Click DV → xem chi tiết → có nút "Nộp hồ sơ"
```

---

### #06 · `feature/client-application`
> **Mục tiêu:** Citizen nộp hồ sơ, xem danh sách và timeline trạng thái
> **Base:** `feature/client-service-catalog`

**Backend**
- [ ] `#06-01` `ApplicationService.submit()` — sinh mã HS, ghi history (null→SUBMITTED)
- [ ] `#06-02` `POST /api/client/applications` — nộp hồ sơ (chưa có file upload)
- [ ] `#06-03` `GET /api/client/applications` — danh sách HS của citizen (phân trang, filter status)
- [ ] `#06-04` `GET /api/client/applications/{id}` — chi tiết HS + `statusHistory`

**UI**
- [ ] `#06-05` Form submit + Template `client/application-submit.html`:
  - Dropdown chọn DV (pre-select nếu có `serviceId`)
  - Textarea ghi chú
  - PRG: thành công → redirect + flash "Nộp thành công! Mã: HS-..."
- [ ] `#06-06` Template `client/application-list.html`:
  - Bảng: Mã HS (link), Tên DV, Ngày nộp, Hạn XL, Trạng thái (badge màu)
  - Filter dropdown status, pagination
- [ ] `#06-07` Template `client/application-detail.html`:
  - Grid 2 cột: thông tin HS bên trái, timeline bên phải
  - Timeline: icon + label + timestamp + ghi chú

**Test**
- [ ] `#06-08` Submit → kiểm tra DB có record đúng, mã HS format `HS-YYYYMMDD-NNNNN`
- [ ] `#06-09` Citizen chỉ xem được HS của mình (ownership check)

**Definition of Done:**
```
□ Nộp hồ sơ → flash thành công + mã HS xuất hiện
□ Danh sách hiển thị đúng badge màu theo status
□ Timeline hiển thị đúng thứ tự thời gian
□ Citizen dùng ID của người khác → 403
```

---

### #07 · `feature/admin-dashboard-application`
> **Mục tiêu:** Admin xem dashboard, quản lý và cập nhật trạng thái hồ sơ
> **Base:** `feature/client-application`

**Backend**
- [ ] `#07-01` `GET /api/admin/dashboard/stats` — 4 KPI
- [ ] `#07-02` `GET /api/admin/dashboard/recent-applications` — 10 HS pending mới nhất
- [ ] `#07-03` `GET /api/admin/applications` — filter đa chiều (status, serviceTypeId, staffId, date range)
- [ ] `#07-04` `GET /api/admin/applications/{id}` — chi tiết + history
- [ ] `#07-05` `PUT /api/admin/applications/{id}/status` — **state machine bắt buộc**
  - Valid: SUBMITTED→RECEIVED→PROCESSING→APPROVED/REJECTED
  - PROCESSING→ADDITIONAL_REQUIRED → SUBMITTED (citizen bổ sung)
  - Invalid transition → 400
- [ ] `#07-06` `PUT /api/admin/applications/{id}/assign` — phân công cán bộ

**UI**
- [ ] `#07-07` `AdminViewController.dashboard()` + Template `admin/dashboard.html`:
  - 4 KPI cards
  - CSS bar chart theo lĩnh vực (width % từ data)
  - Bảng 10 HS mới nhất cần xử lý
- [ ] `#07-08` `AdminViewController.applicationList()` + Template `admin/application-list.html`:
  - Filter bar: status + dịch vụ + date range
  - Bảng: Mã HS, Công dân, DV, Ngày nộp, Cán bộ, Trạng thái, Actions
- [ ] `#07-09` Template `admin/application-detail.html`:
  - Form cập nhật trạng thái (dropdown valid transitions)
  - Bắt buộc nhập ghi chú khi REJECTED / ADDITIONAL_REQUIRED
  - Form phân công cán bộ

**Test**
- [ ] `#07-10` State machine: tất cả valid transitions pass
- [ ] `#07-11` State machine: tất cả invalid transitions → 400, DB không thay đổi
- [ ] `#07-12` Role: CITIZEN không truy cập được admin endpoint

**Definition of Done:**
```
□ Dashboard KPI đúng số liệu từ DB
□ Admin cập nhật SUBMITTED→RECEIVED → timeline update
□ Transition sai (APPROVED→RECEIVED) → flash đỏ, DB không đổi
□ Tất cả state machine tests xanh
```

---

### #08 · `feature/file-upload`
> **Mục tiêu:** Upload/download tài liệu hồ sơ, validate type + size
> **Base:** `feature/admin-dashboard-application`

**Schema**
- [ ] `#08-01` Entity `ApplicationDocument` + Enum `ValidationStatus` + Repository + Mapper
- [ ] `#08-02` Verify entity mapping khớp với bảng `application_documents` trong `psms_schema.sql`

**Backend**
- [ ] `#08-04` `FileStorageService` interface + `LocalFileStorageService`
  - Validate: `.pdf/.jpg/.jpeg/.png/.docx`, max 10MB, sanitize filename
- [ ] `#08-05` `GET /api/files/{filename}` — serve file (kiểm tra quyền)
- [ ] `#08-06` Update `POST /api/client/applications` — xử lý `List<MultipartFile>`
- [ ] `#08-07` `POST /api/client/applications/{id}/documents` — bổ sung (chỉ khi ADDITIONAL_REQUIRED)
- [ ] `#08-08` `POST /api/admin/applications/{id}/documents` — phản hồi (`is_response=true`)

**UI**
- [ ] `#08-09` CSS: thêm `.file-upload-area`, `.doc-item`, `.doc-badge` vào `components.css`
- [ ] `#08-10` Update `client/application-submit.html` — thêm file input + JS preview
- [ ] `#08-11` `js/client.js` — `previewFiles()`: hiển thị tên + size trước khi submit
- [ ] `#08-12` Update `client/application-detail.html` — danh sách tài liệu + download link + form upload bổ sung
- [ ] `#08-13` Update `admin/application-detail.html` — xem tài liệu + validate (VALID/INVALID) + upload phản hồi

**Test**
- [ ] `#08-14` Upload PDF 5MB → OK; upload .exe → 400; file > 10MB → 400
- [ ] `#08-15` Upload khi status sai → 403/400 có message
- [ ] `#08-16` Download → đúng file, đúng quyền

**Definition of Done:**
```
□ Upload file → preview trên UI trước khi submit
□ Sau nộp → danh sách tài liệu hiển thị với validation status
□ Download → mở được file đúng
□ File .exe / > 10MB → thấy lỗi rõ ràng
```

---

### #09 · `feature/client-profile-notification`
> **Mục tiêu:** Citizen quản lý profile, xem và tương tác với thông báo
> **Base:** `feature/file-upload`

**Schema**
- [ ] `#09-01` Entity `Notification` + Enum `NotificationType` + Repository + Mapper

**Backend — Profile**
- [ ] `#09-03` `GET /api/client/profile` — xem thông tin
- [ ] `#09-04` `PUT /api/client/profile` — cập nhật (không cho sửa `national_id`)
- [ ] `#09-05` `PUT /api/client/profile/change-password`

**Backend — Notification**
- [ ] `#09-06` `GET /api/client/notifications` — danh sách (filter isRead, phân trang)
- [ ] `#09-07` `GET /api/client/notifications/unread-count` — đếm badge
- [ ] `#09-08` `PUT /api/client/notifications/{id}/read`
- [ ] `#09-09` `PUT /api/client/notifications/read-all`
- [ ] `#09-10` `PUT /api/client/notifications/settings` — toggle email

**UI**
- [ ] `#09-11` Template `client/profile.html`:
  - Avatar initials, form 2 cột, CCCD disabled, đổi mật khẩu collapsible
- [ ] `#09-12` Template `client/notifications.html`:
  - Feed unread/read, icon theo type, unread dot, "Đánh dấu tất cả đã đọc"
- [ ] `#09-13` `js/client.js` — `updateNotifBadge()` poll mỗi 30s

**Definition of Done:**
```
□ Sửa họ tên → lưu đúng, flash success
□ Sửa CCCD → field disabled, không submit được
□ Notification badge cập nhật sau 30s
□ Mark as read → dot biến mất
```

---

### #10 · `feature/admin-user-management`
> **Mục tiêu:** Admin CRUD người dùng, phân quyền, khóa/mở tài khoản
> **Base:** `feature/admin-dashboard-application`

**Backend**
- [ ] `#10-01` `GET /api/admin/users` — filter role, isActive, keyword (FULLTEXT)
- [ ] `#10-02` `GET /api/admin/users/{id}`
- [ ] `#10-03` `POST /api/admin/users` — tạo tài khoản (staff/citizen)
- [ ] `#10-04` `PUT /api/admin/users/{id}` — cập nhật
- [ ] `#10-05` `PUT /api/admin/users/{id}/lock` + `/unlock`
- [ ] `#10-06` `DELETE /api/admin/users/{id}` — soft delete (`is_active=false`)
- [ ] `#10-07` `PUT /api/admin/users/{id}/roles` — gán/thu hồi role

**UI**
- [ ] `#10-08` CSS: thêm `.modal-overlay`, `.modal-box` vào `components.css`
- [ ] `#10-09` `js/admin.js` — `openModal()`, `closeModal()`, confirm delete pattern
- [ ] `#10-10` Template `admin/user-list.html`:
  - Filter bar (role + status + search)
  - Bảng: Họ tên, Email, CCCD/Mã CB, Vai trò badge, Trạng thái, Actions
  - Modal tạo/sửa tài khoản (form + validation)
  - Confirm dialog cho khóa/xóa

**Test**
- [ ] `#10-11` Chỉ SUPER_ADMIN truy cập được `/api/admin/users`
- [ ] `#10-12` Xóa user → `is_active=false`, data vẫn còn trong DB
- [ ] `#10-13` Lock user → không thể login

**Definition of Done:**
```
□ CRUD đầy đủ qua modal (không reload trang)
□ Confirm dialog: bấm Hủy → không thực hiện
□ Tài khoản bị khóa → đăng nhập bị chặn
□ STAFF gọi /api/admin/users → 403
```

---

### #11 · `feature/admin-service-department-staff`
> **Mục tiêu:** Admin CRUD dịch vụ, phòng ban, phân công cán bộ
> **Base:** `feature/admin-user-management`

**Backend**
- [ ] `#11-01` CRUD `/api/admin/services` — block xóa nếu có HS đang xử lý; toggle is_active
- [ ] `#11-02` CRUD `/api/admin/departments` — block xóa nếu còn staff hoạt động
- [ ] `#11-03` CRUD `/api/admin/staff` — filter dept + is_available; gán hồ sơ cho cán bộ

**UI**
- [ ] `#11-04` Template `admin/service-list.html` — modal tạo/sửa, toggle bật/tắt, workload count
- [ ] `#11-05` Template `admin/department-list.html` — modal tạo/sửa, thông tin trưởng phòng
- [ ] `#11-06` Template `admin/staff-list.html`:
  - Filter phòng ban + trạng thái
  - Workload badge: 0-4 HS=xanh, 5-7 HS=vàng, ≥8 HS=đỏ
  - Modal gán hồ sơ

**Definition of Done:**
```
□ Xóa DV đang có HS xử lý → bị chặn, hiện thông báo lý do
□ Xóa PB còn staff → bị chặn
□ Toggle DV → citizen không thấy DV đã tắt
□ Gán HS cho CB → hiện đúng trong application-detail
```

---

### #12 · `feature/notification-email-logging`
> **Mục tiêu:** Email async, AOP ghi log, NotificationService tích hợp vào các luồng
> **Base:** `feature/file-upload`

**Schema**
- [ ] `#12-01` Entity `ActivityLog` + Repository

**Email**
- [ ] `#12-03` Cấu hình `JavaMailSender` + `@EnableAsync` + `ThreadPoolTaskExecutor`
- [ ] `#12-04` 5 email templates Thymeleaf trong `templates/email/`
  - `application-received.html`, `status-update.html`, `additional-required.html`
  - `approved.html`, `rejected.html`
- [ ] `#12-05` `EmailService` với `@Async` — gắn vào submit HS + update status

**Notification Service**
- [ ] `#12-06` `NotificationService.createAndSave()` — tạo record vào DB
- [ ] `#12-07` Gắn vào: submit HS (APPLICATION_RECEIVED), update status (STATUS_UPDATED/APPROVED/REJECTED/ADDITIONAL_REQUIRED)

**AOP Activity Log**
- [ ] `#12-08` Annotation `@LogActivity(action = "...")`
- [ ] `#12-09` `ActivityLogAspect` — `@Around` tự động ghi log
- [ ] `#12-10` Gắn vào: login, submit HS, update status, CRUD user/service/dept, assign staff

**Test**
- [ ] `#12-11` Email gửi đúng template (mock SMTP/Mailtrap), không block request thread
- [ ] `#12-12` Nộp HS → notification tạo trong DB
- [ ] `#12-13` Update status → activity log ghi đúng action + entity_id

**Definition of Done:**
```
□ Submit HS → email gửi async (request trả về trước khi email xong)
□ Notification tạo đúng type khi status thay đổi
□ Activity log có đủ: user_id, action, entity_type, entity_id, timestamp
□ EmailTest mock SMTP xanh
```

---

### #13 · `feature/admin-activity-log-dashboard`
> **Mục tiêu:** Admin xem Activity Log, Dashboard đầy đủ biểu đồ
> **Base:** `feature/admin-service-department-staff` + `feature/notification-email-logging`

**Backend**
- [ ] `#13-01` `GET /api/admin/logs` — filter user, action, date range; phân trang 50/trang
- [ ] `#13-02` `DELETE /api/admin/logs/purge` — xóa log cũ hơn N ngày (SUPER_ADMIN)
- [ ] `#13-03` `GET /api/admin/dashboard/by-category` — phân bố hồ sơ theo lĩnh vực
- [ ] `#13-04` `GET /api/admin/dashboard/by-status` — phân bố theo trạng thái

**UI**
- [ ] `#13-05` Template `admin/log-list.html`:
  - Filter: action type, user, date range
  - Feed: timestamp (monospace) + action tag màu + actor + mô tả
  - Button "Xóa log cũ" (chỉ SUPER_ADMIN) + confirm dialog
- [ ] `#13-06` Update `admin/dashboard.html`:
  - Thêm CSS bar chart phân bố theo lĩnh vực
  - Thêm donut-style stats theo trạng thái (CSS thuần)

**Definition of Done:**
```
□ Log list filter theo action type hoạt động đúng
□ Purge log → SUPER_ADMIN OK; STAFF → 403
□ Dashboard bar chart hiển thị đúng % từ DB
```

---

### #14 · `feature/import-export-csv`
> **Mục tiêu:** Export/Import dữ liệu dạng CSV cho admin
> **Base:** `feature/admin-activity-log-dashboard`

**Backend**
- [ ] `#14-01` Cấu hình `Apache Commons CSV`
- [ ] `#14-02` Export: `GET /api/admin/export/{type}` (5 loại: citizens, applications, services, departments, staff)
  - UTF-8 BOM cho Excel đọc tiếng Việt đúng
  - `Content-Disposition: attachment; filename="..."`
- [ ] `#14-03` Import: `POST /api/admin/import/{type}` (4 loại: citizens, services, departments, staff)
  - Không fail toàn bộ nếu 1 row lỗi — collect errors, tiếp tục
  - Response: `{ total, success, failed, errors:[{row, field, message}] }`
- [ ] `#14-04` `GET /api/admin/import/templates/{type}` — download file CSV mẫu

**UI**
- [ ] `#14-05` Thêm button "↓ Export CSV" vào toolbar mỗi trang admin list
- [ ] `#14-06` Shared modal `admin/import-modal.html`:
  - File input CSV + button "Tải file mẫu"
  - Submit qua `fetch()` (không reload trang)
  - Hiển thị kết quả: summary + bảng lỗi từng row
- [ ] `#14-07` `js/admin.js` — `importCsv(type, file)` + `renderImportResult(result)`

**Test**
- [ ] `#14-08` Import 100 rows, 3 rows lỗi → `{total:100, success:97, failed:3, errors:[...]}`
- [ ] `#14-09` Export → mở được bằng Excel, tiếng Việt đúng

**Definition of Done:**
```
□ Export CSV → Excel mở OK, tiếng Việt không bị lỗi encoding
□ Import partial failure → báo đúng row/field lỗi, 97 rows vẫn import thành công
□ Download CSV mẫu → đúng format
```

---

### #15 · `task/testing-hardening-cicd`
> **Mục tiêu:** Test coverage ≥ 70%, security hardening, CI/CD, go-live ready
> **Base:** `feature/import-export-csv`

**Test Coverage**
- [ ] `#15-01` Unit: `JwtTokenProviderTest`, `ApplicationCodeGeneratorTest`, `FileStorageServiceTest`
- [ ] `#15-02` Unit: `CsvImportServiceTest` (row hợp lệ, thiếu field, duplicate)
- [ ] `#15-03` Integration: SUBMITTED → APPROVED (kèm file upload + email)
- [ ] `#15-04` Integration: ADDITIONAL_REQUIRED → citizen bổ sung → APPROVED
- [ ] `#15-05` Integration: SUBMITTED → REJECTED (có lý do)
- [ ] `#15-06` Hoàn thiện Postman Collection + export `psms-collection.json`

**Security Hardening**
- [ ] `#15-07` Rate limiting `/auth/**` — chống brute force (bucket4j hoặc custom filter)
- [ ] `#15-08` CORS: chỉ whitelist domain chính thức
- [ ] `#15-09` File upload path traversal test
- [ ] `#15-10` Kiểm tra tất cả admin route có `@PreAuthorize` đúng role
- [ ] `#15-11` JWT secret từ env var, không hardcode

**Performance**
- [ ] `#15-12` EXPLAIN trên các query critical — verify index được dùng
- [ ] `#15-13` Fix N+1 query với `@EntityGraph` / `JOIN FETCH`
- [ ] `#15-14` Thymeleaf cache bật trên prod

**UI Polish**
- [ ] `#15-15` Responsive breakpoints (375px mobile — không vỡ layout)
- [ ] `#15-16` Empty states cho danh sách rỗng
- [ ] `#15-17` Loading state: disable submit button + text "Đang xử lý..."
- [ ] `#15-18` Print-friendly CSS cho trang chi tiết hồ sơ

**CI/CD**
- [ ] `#15-19` GitHub Actions `ci.yml`: push/PR → build → test → coverage
- [ ] `#15-20` GitHub Actions `deploy.yml`: push main → build image → deploy
- [ ] `#15-21` `application-prod.yml`: env vars, Swagger tắt, cache bật
- [ ] `#15-22` Actuator: health public, metrics internal
- [ ] `#15-23` Structured logging JSON + log rotation

**Documentation**
- [ ] `#15-24` `README.md`: setup local, run tests, env vars, Postman link
- [ ] `#15-25` Swagger description đầy đủ
- [ ] `#15-26` `CHANGELOG.md`

**Definition of Done:**
```
□ Test coverage ≥ 70%
□ 3 regression flows xanh
□ CI pipeline xanh trên GitHub Actions
□ docker-compose.prod.yml up → health OK
□ /swagger-ui.html → 404 trên prod
□ Mobile 375px: không vỡ layout
□ README: người mới setup được trong < 15 phút
```

---

## Thống kê task

| Branch | Số task | Từ | Đến |
|---|---|---|---|
| #01 task/init-project | 7 | #01-01 | #01-07 |
| #02 task/entities-repositories | 12 | #02-01 | #02-12 |
| #03 feature/security-auth | 23 | #03-01 | #03-23 |
| #04 feature/base-layout | 9 | #04-01 | #04-09 |
| #05 feature/client-service-catalog | 6 | #05-01 | #05-06 |
| #06 feature/client-application | 9 | #06-01 | #06-09 |
| #07 feature/admin-dashboard-application | 12 | #07-01 | #07-12 |
| #08 feature/file-upload | 15 | #08-01 | #08-16 |
| #09 feature/client-profile-notification | 12 | #09-01 | #09-13 |
| #10 feature/admin-user-management | 13 | #10-01 | #10-13 |
| #11 feature/admin-service-department-staff | 6 | #11-01 | #11-06 |
| #12 feature/notification-email-logging | 12 | #12-01 | #12-13 |
| #13 feature/admin-activity-log-dashboard | 6 | #13-01 | #13-06 |
| #14 feature/import-export-csv | 9 | #14-01 | #14-09 |
| #15 task/testing-hardening-cicd | 26 | #15-01 | #15-26 |
| **Tổng** | **177** | | |

---

## Sơ đồ dependency

```
#01 init-project
  └── #02 entities-repositories
        └── #03 security-auth
              └── #04 base-layout
                    └── #05 client-service-catalog
                          └── #06 client-application
                                └── #07 admin-dashboard-application
                                      ├── #08 file-upload
                                      │     ├── #09 client-profile-notification
                                      │     └── #12 notification-email-logging
                                      │           └── #13 admin-activity-log-dashboard ←─┐
                                      └── #10 admin-user-management                      │
                                            └── #11 admin-service-department-staff ──────┘
                                                  └── #14 import-export-csv
                                                        └── #15 testing-hardening-cicd
```

---

## Git workflow

```bash
# Tạo branch mới từ base
git checkout main
git pull origin main
git checkout -b feature/client-service-catalog

# Commit theo task ID
git commit -m "feat(#05-01): GET /api/client/service-categories"
git commit -m "feat(#05-04): home page hero + category grid"
git commit -m "feat(#05-05): service list with filter + pagination"
git commit -m "feat(#05-06): service detail page"

# Push và tạo PR
git push origin feature/client-service-catalog
# → PR title: "Feature/05 client service catalog"
```

## Quy ước commit message

```
feat(#PR-TASK): thêm tính năng mới
fix(#PR-TASK): sửa bug
test(#PR-TASK): thêm/sửa test
style(#PR-TASK): CSS, UI không ảnh hưởng logic
refactor(#PR-TASK): tái cấu trúc code
chore(#PR-TASK): config, dependencies, CI/CD
docs(#PR-TASK): README, Swagger, CHANGELOG
```

---

*Cập nhật: 2026-03-31 · v3.1 — Fix compatibility issues · 15 PRs · 177 tasks*
