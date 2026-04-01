# SPECS.md — Public Service Management System (PSMS)
> **Phiên bản:** 1.0
> **Ngày tạo:** 2026-03-30
> **Trạng thái:** Confirmed

---

## 1. Tổng quan dự án

### 1.1 Mô tả

**Public Service Management System (PSMS)** là hệ thống quản lý dịch vụ công trực tuyến, cho phép công dân nộp hồ sơ hành chính qua internet và cán bộ nhà nước tiếp nhận, xử lý, phê duyệt hồ sơ trên cùng một nền tảng.

Hệ thống hướng đến việc số hoá toàn bộ quy trình hành chính công — từ lúc công dân nộp hồ sơ đến khi nhận kết quả — nhằm giảm thiểu thủ tục giấy tờ, rút ngắn thời gian xử lý và tăng tính minh bạch.

### 1.2 Mục tiêu

| Mục tiêu | Chỉ số đo lường |
|---|---|
| Số hoá tiếp nhận hồ sơ | 100% hồ sơ được nộp qua hệ thống, không cần đến trực tiếp |
| Minh bạch tiến trình | Công dân tra cứu được trạng thái hồ sơ theo thời gian thực |
| Giảm thời gian xử lý | Rút ngắn ≥ 30% so với quy trình giấy tờ truyền thống |
| Quản lý tập trung | Toàn bộ hồ sơ, cán bộ, phòng ban quản lý trên 1 hệ thống |
| Truy xuất lịch sử | Mọi thao tác đều có audit log, có thể truy vết |

### 1.3 Phạm vi hệ thống

**Trong phạm vi (In scope)**
- Cổng công dân: đăng ký, đăng nhập, nộp hồ sơ, tra cứu, thông báo
- Cổng quản trị: tiếp nhận, xử lý, phê duyệt hồ sơ; quản lý danh mục dịch vụ, phòng ban, cán bộ
- Hệ thống thông báo: email + in-app notification
- Nhật ký hoạt động: ghi lại toàn bộ thao tác
- Import / Export dữ liệu CSV

**Ngoài phạm vi (Out of scope)**
- Thanh toán lệ phí trực tuyến (tích hợp cổng thanh toán)
- Chữ ký số / xác thực eID
- App mobile (iOS / Android)
- Tích hợp hệ thống bên ngoài (cơ sở dữ liệu dân cư quốc gia...)

### 1.4 Tech Stack

| Tầng | Công nghệ |
|---|---|
| Backend | Spring Boot 4.x |
| ORM | Spring Data JPA + Hibernate |
| Bảo mật | Spring Security + JWT + OAuth2 |
| Database | MySQL 8.0+ |
| Frontend | Thymeleaf (SSR) + Pure CSS + Vanilla JS |
| Build | Maven |
| Deploy | Docker + docker-compose |
| Docs | SpringDoc OpenAPI (Swagger UI) |

---

## 2. Người dùng & Phân quyền

### 2.1 Các vai trò (Roles)

| Role | Mô tả | Phạm vi truy cập |
|---|---|---|
| `CITIZEN` | Công dân sử dụng dịch vụ công | Cổng công dân (`/`, `/services`, `/applications`, `/profile`, `/notifications`) |
| `STAFF` | Cán bộ xử lý hồ sơ | Cổng admin — xem và cập nhật hồ sơ được phân công |
| `MANAGER` | Quản lý phòng ban | Cổng admin — phê duyệt, chuyển tiếp, trả hồ sơ; xem tất cả hồ sơ trong phòng ban |
| `SUPER_ADMIN` | Quản trị viên hệ thống | Toàn quyền — CRUD user, service, department, staff; xem logs; purge logs |

### 2.2 Bảng quyền chi tiết

| Chức năng | CITIZEN | STAFF | MANAGER | SUPER_ADMIN |
|---|:---:|:---:|:---:|:---:|
| Xem danh sách dịch vụ | ✓ | ✓ | ✓ | ✓ |
| Nộp hồ sơ | ✓ | — | — | — |
| Xem hồ sơ của mình | ✓ | — | — | — |
| Xem tất cả hồ sơ | — | ✓ (phân công) | ✓ (phòng ban) | ✓ |
| Cập nhật trạng thái HS | — | ✓ | ✓ | ✓ |
| Phân công cán bộ | — | — | ✓ | ✓ |
| CRUD User | — | — | — | ✓ |
| CRUD Service Type | — | — | — | ✓ |
| CRUD Department | — | — | — | ✓ |
| CRUD Staff | — | — | ✓ | ✓ |
| Xem Activity Log | — | — | ✓ | ✓ |
| Xóa Activity Log | — | — | — | ✓ |
| Import / Export CSV | — | — | ✓ | ✓ |

---

## 3. Cơ sở dữ liệu

### 3.1 Sơ đồ quan hệ (tóm tắt)

```
users ──< user_roles >── roles
users ──── citizens          (1-1, profile công dân)
users ──── staff             (1-1, profile cán bộ)
staff >── departments
departments ──── users       (leader_id, 1 trưởng phòng)

service_categories ──< service_types
service_types >── departments

users (citizen) ──< applications
applications >── service_types
applications >── users (assigned_staff)
applications ──< application_documents
applications ──< application_status_history

users ──< notifications
applications ──< notifications
users ──< activity_logs
```

### 3.2 Danh sách bảng

| Bảng | Mô tả | Số cột chính |
|---|---|---|
| `roles` | 4 vai trò cố định | id, name, description |
| `users` | Tài khoản đăng nhập (tất cả vai trò) | id, email, password, full_name, phone, is_active, is_locked, failed_login_count, locked_until, eid_provider, eid_subject |
| `user_roles` | Liên kết user ↔ role (nhiều-nhiều) | user_id, role_id |
| `citizens` | Profile mở rộng của công dân (1-1 users) | national_id, date_of_birth, gender, permanent_address, ward, district, province |
| `departments` | Cơ quan / phòng ban | code, name, address, phone, email, leader_id |
| `staff` | Profile mở rộng của cán bộ (1-1 users) | staff_code, department_id, position, is_available |
| `service_categories` | Lĩnh vực dịch vụ (8 lĩnh vực mặc định) | code, name, description, icon, sort_order |
| `service_types` | Loại dịch vụ công cụ thể | code, name, category_id, department_id, requirements, processing_time_days, fee |
| `applications` | Hồ sơ công dân nộp | application_code, citizen_id, service_type_id, status, submitted_at, processing_deadline, assigned_staff_id |
| `application_documents` | File đính kèm của hồ sơ | application_id, file_name, file_path, file_type, file_size, is_response, validation_status |
| `application_status_history` | Lịch sử thay đổi trạng thái | application_id, old_status, new_status, changed_by, notes, changed_at |
| `notifications` | Thông báo đến người dùng | user_id, application_id, type, title, content, is_read |
| `activity_logs` | Nhật ký toàn bộ hoạt động hệ thống | user_id, action, entity_type, entity_id, description, ip_address, user_agent |
| `refresh_tokens` | Refresh token hợp lệ (token rotation) | user_id, token, expires_at |
| `revoked_access_tokens` | Blacklist JTI access token (logout before expiry) | jti, expires_at |

### 3.3 Enum quan trọng

**ApplicationStatus** — trạng thái hồ sơ:
```
DRAFT → SUBMITTED → RECEIVED → PROCESSING → APPROVED
                                    ↓
                          ADDITIONAL_REQUIRED
                                    ↓
                               SUBMITTED → REJECTED
```

| Trạng thái | Ý nghĩa | Người chuyển |
|---|---|---|
| `DRAFT` | Nháp, chưa nộp | Citizen |
| `SUBMITTED` | Đã nộp, chờ tiếp nhận | Citizen |
| `RECEIVED` | Cán bộ đã tiếp nhận | Staff/Manager |
| `PROCESSING` | Đang xử lý | Staff/Manager |
| `ADDITIONAL_REQUIRED` | Yêu cầu bổ sung hồ sơ | Staff/Manager |
| `APPROVED` | Đã phê duyệt | Staff/Manager |
| `REJECTED` | Từ chối, có lý do | Staff/Manager |

**NotificationType:** `APPLICATION_RECEIVED` · `ADDITIONAL_REQUIRED` · `STATUS_UPDATED` · `APPROVED` · `REJECTED` · `SYSTEM`

**ActivityLog Action:** `LOGIN` · `LOGOUT` · `CREATE_APP` · `UPDATE_STATUS` · `APPROVE` · `REJECT` · `CREATE_SERVICE` · `UPDATE_SERVICE` · `DELETE_SERVICE` · `CREATE_USER` · `UPDATE_USER` · `DELETE_USER` · `LOCK_USER` · `CREATE_DEPT` · `UPDATE_DEPT` · `DELETE_DEPT` · `ASSIGN_STAFF` · `UPLOAD_DOC` · `EXPORT` · `IMPORT`

---

## 4. Chức năng chi tiết — Cổng Công Dân

> **Ghi chú kiến trúc dual-layer:**
> Các route trong tài liệu này mô tả **MVC form** (dùng `GET/POST` — HTML form chỉ hỗ trợ 2 verb này).
> REST API tương ứng nằm ở `/api/` prefix và sử dụng HTTP verb chuẩn (`PUT`, `DELETE`).
> Cả hai layer gọi cùng Service — xem TASKS.md để biết REST endpoint cụ thể.

### 4.1 Đăng ký / Đăng nhập

**Đăng ký tài khoản (`GET/POST /auth/register`)**
- Form nhập: họ tên, email, mật khẩu, CCCD/CMND, ngày sinh, giới tính, địa chỉ thường trú
- Validation: email phải unique, CCCD phải unique, mật khẩu tối thiểu 8 ký tự
- Sau khi đăng ký: tự động tạo `User` + `Citizen`, gán role `CITIZEN`
- Hiển thị lỗi từng field ngay dưới input nếu validation fail

**Đăng nhập (`GET/POST /auth/login`)**
- Form: email + mật khẩu
- Kiểm tra: tài khoản tồn tại, mật khẩu đúng, không bị khoá (`is_locked = false`)
- Thành công: lưu JWT vào session, cập nhật `last_login_at`, redirect trang chủ
- Thất bại: hiển thị thông báo lỗi, không tiết lộ field nào sai

**Đăng xuất (`POST /auth/logout`)**
- Xoá session, redirect về trang đăng nhập
- Logout: access token bị blacklist qua jti, refresh token bị xóa khỏi DB
- Refresh: token rotation — mỗi lần refresh sinh token mới, token cũ bị xóa
---

### 4.2 Hồ sơ cá nhân (`GET /profile`)

**Xem thông tin**
- Hiển thị: họ tên, ngày sinh, giới tính, CCCD (chỉ đọc), địa chỉ, SĐT, email
- Hiển thị: ngày tham gia, tổng số hồ sơ đã nộp, badge "Đã xác minh"

**Cập nhật thông tin (`POST /profile`)**
- Cho phép sửa: họ tên, ngày sinh, giới tính, SĐT, email, địa chỉ
- Không cho phép sửa: CCCD/CMND (field bị disabled)
- Sau khi lưu: flash message xanh "Cập nhật thành công"

**Đổi mật khẩu (`POST /profile/change-password`)**
- Form: mật khẩu cũ, mật khẩu mới, xác nhận mật khẩu mới
- Validate: mật khẩu cũ đúng, mật khẩu mới ≥ 8 ký tự, hai field mới khớp nhau

---

### 4.3 Danh mục dịch vụ công (`GET /services`)

**Trang danh sách**
- Hiển thị tất cả dịch vụ đang hoạt động (`is_active = true`), phân trang 10 item/trang
- Filter theo: lĩnh vực (`category_id`), từ khoá tên dịch vụ
- Mỗi dịch vụ hiển thị: tên, lĩnh vực (badge), cơ quan tiếp nhận, thời hạn xử lý, lệ phí
- URL cập nhật theo filter: `/services?categoryId=1&keyword=khai+sinh&page=2`

**Trang chi tiết (`GET /services/{id}`)**
- Hiển thị đầy đủ: tên, lĩnh vực, mô tả, yêu cầu hồ sơ (danh sách giấy tờ cần nộp), thời hạn xử lý (ngày làm việc), lệ phí, cơ quan tiếp nhận
- Nút "Nộp hồ sơ ngay" → chuyển đến form nộp với `serviceId` được điền sẵn

---

### 4.4 Nộp hồ sơ (`GET/POST /applications/submit`)

**Form nộp hồ sơ**
- Dropdown chọn dịch vụ (pre-select nếu đến từ trang chi tiết dịch vụ)
- Khi chọn dịch vụ: hiển thị mô tả yêu cầu hồ sơ của dịch vụ đó
- Textarea ghi chú / thông tin bổ sung
- Upload tài liệu: chọn nhiều file, preview tên + dung lượng trước khi submit
  - Định dạng cho phép: PDF, JPG, JPEG, PNG, DOCX
  - Dung lượng tối đa: 10 MB / file
- Nút "Nộp hồ sơ"

**Xử lý khi submit**
- Sinh `application_code` tự động: `HS-{YYYYMMDD}-{5 chữ số}` — ví dụ `HS-20260330-00042`
- Ghi `ApplicationStatusHistory`: null → `SUBMITTED`
- Ghi `ActivityLog`: action `CREATE_APP`
- Tạo `Notification` cho citizen: type `APPLICATION_RECEIVED`
- Gửi email xác nhận (async)
- Redirect đến trang chi tiết hồ sơ + flash message "Nộp hồ sơ thành công! Mã hồ sơ: HS-..."

---

### 4.5 Danh sách hồ sơ đã nộp (`GET /applications`)

- Chỉ hiển thị hồ sơ của citizen đang đăng nhập
- Phân trang 10 item/trang, sort theo `submitted_at DESC`
- Filter theo trạng thái (dropdown)
- Cột bảng: Mã hồ sơ (link), Tên dịch vụ, Ngày nộp, Hạn xử lý, Trạng thái (badge màu)

| Trạng thái | Màu badge |
|---|---|
| SUBMITTED / RECEIVED | Vàng (amber) |
| PROCESSING | Xanh dương (blue) |
| ADDITIONAL_REQUIRED | Vàng (amber) |
| APPROVED | Xanh lá (green) |
| REJECTED | Đỏ (red) |

---

### 4.6 Chi tiết hồ sơ (`GET /applications/{id}`)

**Thông tin hồ sơ**
- Mã hồ sơ, tên dịch vụ, cơ quan tiếp nhận, cán bộ phụ trách, ngày nộp, hạn xử lý, lệ phí, trạng thái hiện tại

**Tài liệu đã nộp**
- Danh sách file: tên, dung lượng, định dạng, validation status (Đang kiểm tra / Hợp lệ / Không hợp lệ)
- Link download từng file (có kiểm tra quyền truy cập)
- Nếu có tài liệu phản hồi từ cán bộ (`is_response = true`): hiển thị riêng

**Upload bổ sung** (chỉ khi status = `ADDITIONAL_REQUIRED`)
- Hiển thị form upload
- Sau khi upload → status tự động chuyển `SUBMITTED`
- Ghi history + notification

**Timeline trạng thái**
- Hiển thị từng mốc thay đổi trạng thái theo thời gian
- Mỗi mốc: icon, tên trạng thái, thời gian, ghi chú (nếu có)

---

### 4.7 Thông báo (`GET /notifications`)

**Danh sách thông báo**
- Phân trang 10 item/trang
- Filter: tất cả / chưa đọc
- Mỗi thông báo: icon theo loại, tiêu đề, mô tả, thời gian, dấu chấm xanh nếu chưa đọc
- Click vào → đánh dấu đã đọc + redirect đến hồ sơ liên quan (nếu có `application_id`); thông báo hệ thống chỉ mark read, không redirect

**Thao tác**
- "Đánh dấu tất cả đã đọc" → `POST /notifications/read-all`
- Badge số thông báo chưa đọc trên topbar — tự cập nhật mỗi 30 giây qua `fetch()`

**Cài đặt**
- Toggle bật/tắt email notification trong trang Profile

---

## 5. Chức năng chi tiết — Cổng Quản Trị

### 5.1 Đăng nhập Admin (`GET/POST /admin/login`)

- Form tách biệt với cổng công dân
- Chỉ cho phép role `STAFF`, `MANAGER`, `SUPER_ADMIN`
- Hiển thị thông tin role sau khi đăng nhập để frontend render đúng menu

---

### 5.2 Dashboard (`GET /admin/dashboard`)

**KPI Cards (4 thẻ)**
- Tổng hồ sơ trong hệ thống
- Đang xử lý (RECEIVED + PROCESSING + ADDITIONAL_REQUIRED)
- Đã hoàn thành (APPROVED + REJECTED)
- Số hồ sơ quá hạn (`processing_deadline < NOW()` và chưa hoàn thành)

**Biểu đồ phân bố hồ sơ theo lĩnh vực**
- Bar chart CSS — không cần thư viện JS
- Hiển thị top 6 lĩnh vực có nhiều hồ sơ nhất trong tháng hiện tại

**Biểu đồ trạng thái (Donut CSS)**
- Tỷ lệ phần trăm theo từng trạng thái

**Bảng hồ sơ mới nhất cần xử lý**
- 10 hồ sơ gần nhất có status `SUBMITTED` hoặc `RECEIVED`
- Link trực tiếp đến trang chi tiết

---

### 5.3 Quản lý hồ sơ (`GET /admin/applications`)

**Danh sách hồ sơ**
- Phân trang 20 item/trang
- Filter đa chiều: trạng thái, loại dịch vụ, phòng ban, cán bộ phụ trách, ngày nộp từ–đến
- Sort theo: ngày nộp (mặc định DESC), hạn xử lý
- Cột: Mã hồ sơ, Công dân, Dịch vụ, Ngày nộp, Cán bộ XL, Trạng thái, Thao tác

**Chi tiết hồ sơ (`GET /admin/applications/{id}`)**
- Đầy đủ thông tin: dịch vụ, công dân, ngày nộp, hạn xử lý, cán bộ phụ trách
- Timeline lịch sử trạng thái (với người thực hiện + ghi chú)
- Danh sách tài liệu: xem, download, validate (VALID / INVALID)
- Form upload tài liệu phản hồi

**Cập nhật trạng thái (`POST /admin/applications/{id}/status`)**
- Dropdown chỉ hiện các transition hợp lệ theo state machine
- Bắt buộc nhập ghi chú khi chuyển sang `REJECTED` hoặc `ADDITIONAL_REQUIRED`
- Sau khi cập nhật: ghi `ApplicationStatusHistory` + `ActivityLog` + tạo `Notification` + gửi email (async)
- Transition không hợp lệ → flash message đỏ, không cập nhật DB

**Phân công cán bộ (`POST /admin/applications/{id}/assign`)**
- Dropdown chọn cán bộ theo phòng ban của dịch vụ
- Chỉ hiện cán bộ có `is_available = true`

---

### 5.4 Quản lý người dùng (`GET /admin/users`) — SUPER_ADMIN

**Danh sách**
- Phân trang 20 item/trang
- Filter: role, is_active, từ khoá (tên, email, CCCD)
- Cột: Họ tên, Email, CCCD / Mã CB, Vai trò (badge), Trạng thái, Ngày tạo, Thao tác

**Tạo tài khoản mới**
- Form modal: họ tên, email, mật khẩu tạm, role, SĐT, địa chỉ
- Nếu role = STAFF/MANAGER: thêm field phòng ban, chức vụ

**Sửa thông tin**
- Cập nhật tất cả trừ CCCD

**Khoá / Mở khoá tài khoản**
- Xác nhận qua confirm dialog trước khi thực hiện
- Tài khoản bị khoá không thể đăng nhập

**Xoá tài khoản**
- Soft delete: `is_active = false`
- Không xoá cứng để giữ lịch sử hồ sơ

**Gán / thu hồi role**
- Một user có thể có nhiều role

---

### 5.5 Quản lý dịch vụ công (`GET /admin/services`) — SUPER_ADMIN

**Danh sách**
- Phân trang 20 item/trang
- Filter: lĩnh vực, từ khoá, trạng thái (hoạt động / tạm dừng)
- Cột: Mã DV, Tên, Lĩnh vực, Cơ quan tiếp nhận, Thời hạn, Lệ phí, Trạng thái, Thao tác

**Tạo / Sửa dịch vụ (modal form)**
- Trường bắt buộc: tên, mã, lĩnh vực, phòng ban tiếp nhận, thời hạn xử lý (ngày)
- Trường tuỳ chọn: mô tả, yêu cầu hồ sơ (textarea Markdown), lệ phí, mô tả lệ phí

**Bật / Tắt dịch vụ**
- Toggle `is_active` — dịch vụ tắt không hiển thị cho công dân

**Xoá dịch vụ**
- Chặn xoá nếu có hồ sơ đang ở trạng thái chưa hoàn thành (SUBMITTED / RECEIVED / PROCESSING / ADDITIONAL_REQUIRED)
- Hiển thị thông báo lý do nếu bị chặn

---

### 5.6 Quản lý phòng ban (`GET /admin/departments`) — SUPER_ADMIN

**Danh sách**
- Phân trang
- Cột: Mã PB, Tên, Địa chỉ, SĐT, Trưởng phòng, Số cán bộ, Số dịch vụ đang quản lý, Thao tác

**Tạo / Sửa phòng ban (modal form)**
- Trường: tên, mã, địa chỉ, SĐT, email, trưởng phòng (dropdown từ danh sách users có role MANAGER)

**Xoá phòng ban**
- Chặn nếu còn cán bộ đang hoạt động trong phòng ban

---

### 5.7 Phân công cán bộ (`GET /admin/staff`) — MANAGER, SUPER_ADMIN

**Danh sách**
- Filter: phòng ban, trạng thái (`is_available`)
- Cột: Mã CB, Họ tên, Email, Phòng ban, Chức vụ, Số hồ sơ đang xử lý (badge màu), Trạng thái, Thao tác
- Workload indicator: 0–4 HS → xanh; 5–7 HS → vàng; ≥ 8 HS → đỏ

**Thêm cán bộ vào phòng ban**
- Tạo record `Staff` liên kết `User` (đã có role STAFF) với `Department`

**Chuyển phòng ban**
- Cập nhật `department_id` của staff

**Gán hồ sơ cho cán bộ**
- Dropdown chọn hồ sơ đang `RECEIVED` chưa có cán bộ phụ trách

**Xoá cán bộ khỏi phòng ban**
- Cảnh báo nếu cán bộ đang có hồ sơ chưa xử lý xong

---

### 5.8 Activity Log (`GET /admin/logs`) — MANAGER, SUPER_ADMIN

**Danh sách log**
- Phân trang 50 item/trang, sort `created_at DESC`
- Filter: action type, user (tên hoặc email), từ ngày – đến ngày
- Mỗi log: thời gian (timestamp), action tag (màu theo loại), tên người thực hiện, mô tả, IP

**Action tag màu sắc**
| Nhóm action | Màu |
|---|---|
| LOGIN / LOGOUT | Xanh dương |
| CREATE_APP / UPDATE_STATUS / APPROVE / REJECT | Xanh lá |
| CREATE_SERVICE / UPDATE_SERVICE | Xanh dương nhạt |
| CREATE_USER / UPDATE_USER / LOCK_USER | Vàng |
| DELETE_USER / DELETE_SERVICE / DELETE_DEPT | Đỏ |
| ASSIGN_STAFF | Cam |
| EXPORT / IMPORT | Xám |

**Xoá log cũ** — SUPER_ADMIN only
- Xoá toàn bộ log cũ hơn N ngày (mặc định 90 ngày)
- Bắt buộc confirm dialog trước khi xoá
- Ghi log chính hành động xoá này

---

## 6. Import / Export CSV

### 6.1 Export

Tất cả export đều:
- Set header `Content-Disposition: attachment; filename="..."`
- Encoding UTF-8 BOM (để Excel đọc tiếng Việt đúng)
- Có thể filter trước khi export (áp dụng filter đang active trên trang list)

| Loại | Endpoint | Columns |
|---|---|---|
| Công dân | `GET /api/admin/export/citizens` | Họ tên, Email, CCCD, Ngày sinh, Giới tính, Địa chỉ, Số HS đã nộp |
| Hồ sơ | `GET /api/admin/export/applications` | Mã HS, Tên DV, Công dân, Ngày nộp, Ngày hoàn thành, Trạng thái, Cán bộ XL, Thời gian xử lý (ngày) |
| Dịch vụ | `GET /api/admin/export/services` | Mã DV, Tên, Lĩnh vực, Cơ quan, Thời hạn, Lệ phí, Trạng thái |
| Phòng ban | `GET /api/admin/export/departments` | Mã PB, Tên, Địa chỉ, SĐT, Trưởng phòng, Số CB |
| Cán bộ | `GET /api/admin/export/staff` | Mã CB, Họ tên, Email, Phòng ban, Chức vụ, Số HS đang XL |

### 6.2 Import

**Quy tắc chung**
- Không fail toàn bộ nếu một số row lỗi — xử lý từng row độc lập
- Response trả về: `{ total, success, failed, errors: [{row, field, message}] }`
- Download file CSV mẫu trước khi import: `GET /api/admin/import/templates/{type}`

| Loại | Endpoint | Validation |
|---|---|---|
| Công dân | `POST /api/admin/import/citizens` | email unique, national_id unique, required fields |
| Dịch vụ | `POST /api/admin/import/services` | code unique, category tồn tại, department tồn tại |
| Phòng ban | `POST /api/admin/import/departments` | code unique |
| Cán bộ | `POST /api/admin/import/staff` | user tồn tại, department tồn tại |

---

## 7. Thông báo & Email

### 7.1 In-app Notification

| Sự kiện | Người nhận | Type |
|---|---|---|
| Citizen nộp hồ sơ thành công | Citizen | `APPLICATION_RECEIVED` |
| Cán bộ chuyển trạng thái HS | Citizen | `STATUS_UPDATED` |
| Cán bộ yêu cầu bổ sung | Citizen | `ADDITIONAL_REQUIRED` |
| Hồ sơ được phê duyệt | Citizen | `APPROVED` |
| Hồ sơ bị từ chối | Citizen | `REJECTED` |

### 7.2 Email Notification (Async)

- Gửi bất đồng bộ (`@Async`) — không làm chậm request
- Template HTML (Thymeleaf) trong `templates/email/`
- Chỉ gửi nếu citizen có `email_notif_enabled = true`
- Nội dung email bao gồm: mã hồ sơ, tên dịch vụ, trạng thái mới, ghi chú (nếu có), link xem chi tiết

---

## 8. Bảo mật

### 8.1 Authentication & Token Management

**Access Token**
- JWT Bearer token, TTL: 1 giờ
- Mỗi token có claim `jti` (JWT ID, UUID) để hỗ trợ revoke
- Stateless validation — server verify signature + expiry
- Khi logout hoặc phát hiện bất thường: `jti` bị insert vào
  bảng `revoked_access_tokens` (blacklist)
- `JwtAuthenticationFilter` kiểm tra blacklist trước khi cho
  request đi qua

**Refresh Token**
- TTL: 7 ngày, lưu trong bảng `refresh_tokens`
- Áp dụng **Token Rotation**: mỗi lần `/api/auth/refresh-token`
  được gọi → xóa token cũ → sinh token mới (cả access + refresh)
- Nếu refresh token đã rotate bị dùng lại (Reuse Detection)
  → revoke toàn bộ session của user đó (xóa hết rows trong
  `refresh_tokens` theo `user_id`)
- Client chủ động gọi `/refresh-token` khi nhận 401 từ API

**Lưu trữ token phía client (Thymeleaf SSR)**
- Access token lưu trong `HttpSession` để Thymeleaf đọc được
  khi render template server-side
- Refresh token lưu trong `HttpOnly cookie` (không accessible
  từ JavaScript — chống XSS)
- Khi session hết hoặc logout: xóa session + xóa cookie

**Cleanup**
- `@Scheduled` chạy mỗi 1 giờ:
  - `DELETE FROM refresh_tokens WHERE expires_at < NOW()`
  - `DELETE FROM revoked_access_tokens WHERE expires_at < NOW()`

---

### 8.2 Authorization

- URL-level security: `SecurityConfig` định nghĩa rule cho từng path pattern
- Method-level security: `@PreAuthorize` trên các service method nhạy cảm
- Thymeleaf Security: `sec:authorize` ẩn/hiện UI element theo role

### 8.3 Các biện pháp bảo vệ

| Biện pháp | Mô tả |
|---|---|
| CSRF Protection | Spring Security tự động thêm CSRF token vào Thymeleaf form |
| Rate Limiting | `/auth/login` và `/auth/register` giới hạn 5 request/phút/IP |
| Brute Force | Khoá tài khoản sau 5 lần đăng nhập sai liên tiếp (`failed_login_count`). Tự động mở sau 15 phút (`locked_until`). Reset counter khi login thành công |
| File Upload Security | Validate MIME type thực tế (không chỉ extension), giới hạn 10MB/file |
| Path Traversal | Sanitize tên file upload, không cho phép `../` trong path |
| SQL Injection | Dùng JPA/Hibernate parameterized query, không string concat |
| Password | BCrypt với cost factor 12 |
| JWT Secret | Đọc từ environment variable, không hardcode |
| CORS | Chỉ whitelist domain frontend chính thức trên production |

---

## 9. Giao diện người dùng

### 9.1 Thiết kế tổng thể

| Thuộc tính | Giá trị |
|---|---|
| Rendering | Server-side (Thymeleaf SSR) |
| CSS Framework | Pure CSS (không dùng Bootstrap / Tailwind) |
| JS | Vanilla JS (không dùng React / Vue) |
| Font | IBM Plex Sans (Google Fonts) |
| Màu chủ đạo Client | Navy `#0D3B7C` + Gold `#C9A84C` |
| Màu chủ đạo Admin | Dark sidebar `#111827` + Blue accent `#3B82F6` |
| Responsive | Hỗ trợ tối thiểu 375px (mobile) đến 1440px (desktop) |

### 9.2 Màn hình Client (8 màn hình)

| ID | Màn hình | Route |
|---|---|---|
| C-01 | Trang chủ | `GET /` |
| C-02 | Danh mục dịch vụ | `GET /services` |
| C-02b | Chi tiết dịch vụ | `GET /services/{id}` |
| C-03 | Danh sách hồ sơ | `GET /applications` |
| C-03b | Nộp hồ sơ | `GET/POST /applications/submit` |
| C-04 | Chi tiết hồ sơ + Timeline | `GET /applications/{id}` |
| C-05 | Hồ sơ cá nhân | `GET /profile` |
| C-06 | Thông báo | `GET /notifications` |

### 9.3 Màn hình Admin (7 màn hình)

| ID | Màn hình | Route |
|---|---|---|
| A-01 | Dashboard tổng quan | `GET /admin/dashboard` |
| A-02 | Quản lý hồ sơ | `GET /admin/applications` |
| A-03 | Quản lý người dùng | `GET /admin/users` |
| A-04 | Quản lý dịch vụ công | `GET /admin/services` |
| A-05 | Quản lý phòng ban | `GET /admin/departments` |
| A-06 | Phân công cán bộ | `GET /admin/staff` |
| A-07 | Activity Log | `GET /admin/logs` |

### 9.4 Luồng tương tác chính

**PRG Pattern** — tất cả form POST đều redirect sau khi xử lý thành công:
```
User submit form → POST controller → xử lý → redirect GET → hiện flash message
```

**Flash message** — tự ẩn sau 3 giây:
- Xanh lá: thành công
- Đỏ: lỗi / validation fail
- Vàng: cảnh báo

**Confirm dialog** — bắt buộc trước khi: xoá user, khoá user, xoá dịch vụ, xoá phòng ban, xoá log

---

## 10. Hiệu năng & Giới hạn

| Tham số | Giá trị |
|---|---|
| Phân trang mặc định | 10 item/trang (client) · 20 item/trang (admin list) · 50 item/trang (log) |
| File upload tối đa | 10 MB / file |
| Định dạng file chấp nhận | PDF, JPG, JPEG, PNG, DOCX |
| Session timeout | 8 giờ không hoạt động |
| Email gửi | Bất đồng bộ (async), timeout 30 giây |
| CSV import tối đa | 1.000 rows / lần |
| Log retention | Xoá log cũ hơn 90 ngày (thủ công bởi SUPER_ADMIN) |

---

## 11. Cấu trúc dự án

```
psms/
├── src/
│   ├── main/
│   │   ├── java/com/psms/
│   │   │   ├── config/          # SecurityConfig, JwtConfig, AsyncConfig, SwaggerConfig
│   │   │   ├── controller/
│   │   │   │   ├── client/      # REST /api/client/**
│   │   │   │   ├── admin/       # REST /api/admin/**
│   │   │   │   └── view/        # MVC → trả về Thymeleaf template
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── entity/
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   └── response/
│   │   │   ├── mapper/          # MapStruct
│   │   │   ├── exception/       # GlobalExceptionHandler
│   │   │   ├── util/            # ApplicationCodeGenerator, CsvHelper
│   │   │   └── enums/
│   │   └── resources/
│   │       ├── templates/
│   │       │   ├── layout/      # client.html, admin.html (Layout Dialect)
│   │       │   ├── auth/        # login.html, register.html, admin-login.html
│   │       │   ├── client/      # 6 màn hình client
│   │       │   ├── admin/       # 7 màn hình admin
│   │       │   ├── email/       # 5 email templates
│   │       │   └── error/       # 404.html, 403.html, 500.html
│   │       └── static/
│   │           ├── css/         # base, components, layout, client, admin
│   │           └── js/          # main.js, client.js, admin.js
│   └── test/
├── docker-compose.yml
├── docker-compose.prod.yml
├── Dockerfile
├── psms_schema.sql
├── TASKS.md
├── SPECS.md
└── README.md
```

---

## 12. Môi trường & Cấu hình

### 12.1 Environment Variables (Production)

| Biến | Mô tả |
|---|---|
| `DB_HOST` | MySQL host |
| `DB_PORT` | MySQL port (default 3306) |
| `DB_NAME` | Tên database |
| `DB_USERNAME` | MySQL user |
| `DB_PASSWORD` | MySQL password |
| `JWT_SECRET` | JWT signing secret (≥ 256 bit) |
| `JWT_EXPIRATION` | Access token TTL (ms) |
| `MAIL_HOST` | SMTP server |
| `MAIL_PORT` | SMTP port |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |
| `UPLOAD_DIR` | Thư mục lưu file upload |
| `APP_BASE_URL` | URL gốc của app (dùng trong email template) |

### 12.2 Profiles

| Profile | `ddl-auto` | Swagger | `show-sql` | Template cache |
|---|---|---|---|---|
| `dev` | `validate` | Bật | Bật | Tắt |
| `prod` | `none` | Tắt | Tắt | Bật |

---

## 13. Tiêu chí chấp nhận (Acceptance Criteria)

### 13.1 Luồng cốt lõi

- [ ] Công dân đăng ký → đăng nhập → nộp hồ sơ → nhận mã hồ sơ HS-... → xem timeline
- [ ] Admin đăng nhập → xem danh sách hồ sơ → cập nhật trạng thái → citizen nhận thông báo
- [ ] State machine: transition không hợp lệ bị từ chối, hiện flash đỏ, DB không thay đổi
- [ ] Role access: citizen truy cập `/admin/**` → 403; không có token → redirect login

### 13.2 Chức năng mở rộng

- [ ] Upload file: PDF/JPG ≤ 10MB thành công; file .exe / > 10MB bị từ chối có thông báo
- [ ] Email gửi đúng template, không block request (async)
- [ ] Activity log ghi đầy đủ sau mỗi action
- [ ] Import 100 rows CSV, 3 row lỗi → kết quả `{ total:100, success:97, failed:3, errors:[...] }`
- [ ] Export CSV → Excel mở được, tiếng Việt hiển thị đúng

### 13.3 UI / UX

- [ ] Flash message xuất hiện và tự ẩn sau 3 giây
- [ ] Form validation hiển thị lỗi đúng field, không crash
- [ ] Confirm dialog xuất hiện trước khi xoá/khoá; bấm Hủy → không thực hiện
- [ ] Pagination giữ nguyên filter khi chuyển trang
- [ ] Mobile 375px: không có overflow ngang

### 13.4 Kỹ thuật

- [ ] `docker-compose up` → app start thành công, truy cập được http://localhost:8080
- [ ] `GET /actuator/health` → `{"status":"UP"}`
- [ ] Integration test coverage ≥ 70%
- [ ] Không có endpoint nào trả toàn bộ DB (pagination everywhere)
- [ ] Swagger ẩn trên production

---

## 14. Rủi ro & Phụ thuộc

| Rủi ro | Mức độ | Tác động | Hướng xử lý |
|---|---|---|---|
| Schema cần thêm bảng sau khi code | Trung bình | Cao | Migration có version; event storming kỹ trước khi code |
| Email bị spam filter trên prod | Thấp | Thấp | Dùng SendGrid / AWS SES thay Gmail SMTP |
| N+1 query trên list hồ sơ lớn | Cao | Cao | `@EntityGraph` / `JOIN FETCH`; EXPLAIN trước go-live |
| File upload lấp đầy disk | Thấp | Cao | Monitor disk usage; có thể migrate sang S3 sau |
| State machine bị bypass qua API | Thấp | Cao | Unit test toàn bộ invalid transition; enforce ở Service layer |

---

*Tài liệu liên quan: `psms_schema.sql` · `TASKS.md` · `psms_gui_mockup.html`*
*Cập nhật lần cuối: 2026-03-30 · v1.0*
