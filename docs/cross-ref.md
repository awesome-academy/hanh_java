# Cross-reference: SPECS ↔ UI

Đây là file cầu nối duy nhất giữa `SPECS.md` và `docs/ui-spec.md`.
Khi implement một task, tra bảng này để biết cần đọc section nào ở cả hai file.

---

## Map: SPECS section → UI screen

| SPECS section | Nội dung | UI screen | ui-spec section |
|---|---|---|---|
| 4.1 Đăng ký / Đăng nhập | Rules: email unique, CCCD unique, is_locked check | `auth/login`, `auth/register` | — (auth pages không có trong ui-spec, dùng layout chuẩn) |
| 4.2 Hồ sơ cá nhân | Rules: không sửa CCCD, validate phone/email | **C-05** Profile | `## C-05 · Hồ sơ cá nhân` |
| 4.3 Danh mục dịch vụ | Rules: chỉ hiện is_active=true, filter categoryId | **C-02** Service list, **C-02b** Service detail | `## C-02 · Danh mục dịch vụ` |
| 4.4 Nộp hồ sơ | Rules: sinh mã HS-*, file max 10MB, các type cho phép | **C-03b** Submit form (CTA từ C-01 + C-02b) | — (chưa có section riêng trong ui-spec) |
| 4.5 Danh sách hồ sơ | Rules: chỉ xem của mình, sort submitted_at DESC | **C-03** Application list | `## C-03 · Danh sách hồ sơ` |
| 4.6 Chi tiết hồ sơ | Rules: upload chỉ khi ADDITIONAL_REQUIRED, có history | **C-04** Application detail | `## C-04 · Chi tiết hồ sơ` |
| 4.7 Thông báo | Rules: badge count, toggle email, mark-as-read | **C-06** Notifications | `## C-06 · Thông báo` |
| 5.1 Đăng nhập Admin | Rules: chỉ STAFF+ | `auth/admin-login` | — |
| 5.2 Dashboard | Rules: 4 KPI, recent pending | **A-01** Dashboard | `## A-01 · Admin Dashboard` |
| 5.3 Quản lý hồ sơ | Rules: filter đa chiều, state machine | **A-02** Application list/detail | `## A-02 → A-07 · Admin pages` |
| 5.4 Quản lý người dùng | Rules: soft delete, gán role, khóa/mở | **A-03** User list | `## A-02 → A-07` |
| 5.5 Quản lý dịch vụ | Rules: block xóa nếu có HS đang xử lý | **A-04** Service list | `## A-02 → A-07` |
| 5.6 Quản lý phòng ban | Rules: block xóa nếu còn staff | **A-05** Department list | `## A-02 → A-07` |
| 5.7 Phân công cán bộ | Rules: workload indicator, chỉ CB available | **A-06** Staff list | `## A-02 → A-07` |
| 5.8 Activity Log | Rules: color-coded tags, purge chỉ SUPER_ADMIN | **A-07** Log list | `## A-02 → A-07` |
| 6. Import/Export | Rules: UTF-8 BOM, partial failure CSV | Nút trên mọi admin list page | `## Admin table action buttons` |
| 7. Notification | Rules: async email, type enum | Badge trên topbar, C-06 feed | `## C-06`, `## Topbar — Client` |

---

## Map ngược: UI screen → SPECS section

| UI screen | Implement theo SPECS section | Lưu ý khi thay đổi |
|---|---|---|
| **C-01** Trang chủ | 4.3 (search), 4.4 (CTA nộp hồ sơ) | Thêm lĩnh vực mới → cập nhật SPECS 4.3 + seed data |
| **C-02** Service catalog | 4.3 đầy đủ | Thay đổi filter rule → SPECS 4.3 trước, ui-spec sau |
| **C-02b** Service detail | 4.3 (chi tiết DV) | Thêm field → SPECS 4.3 trước |
| **C-03** My applications | 4.5 đầy đủ | Thêm cột mới → SPECS 4.5 trước |
| **C-03b** Submit form | 4.4 đầy đủ | Thêm field upload / validation → SPECS 4.4 trước |
| **C-04** App detail | 4.6 đầy đủ | Thêm section mới (vd: rating) → SPECS trước |
| **C-05** Profile | 4.2 đầy đủ | Field nào disabled → do SPECS 4.2 quyết định |
| **C-06** Notifications | 4.7 đầy đủ | Thêm notification type → SPECS 7 + enums trước |
| **A-01** Dashboard | 5.2 đầy đủ | Thêm KPI → SPECS 5.2 trước |
| **A-02** App management | 5.3 đầy đủ | State machine → `psms_schema.sql` enum trước |
| **A-03** User management | 5.4 đầy đủ | Thêm role → SPECS 2.1 trước |
| **A-04** Service management | 5.5 đầy đủ | |
| **A-05** Department management | 5.6 đầy đủ | |
| **A-06** Staff assignment | 5.7 đầy đủ | |
| **A-07** Activity log | 5.8 đầy đủ | Thêm action type → SPECS 3.3 enum trước |

---

## Khi SPECS thay đổi → UI cần update gì

| Loại thay đổi SPECS | UI impact | Cần update |
|---|---|---|
| Thêm validation rule mới (vd: CCCD format) | Client-side: thêm error message | `ui-spec.md` phần form field error pattern |
| Thêm ApplicationStatus mới | Badge color + label | `ui-spec.md` phần badge mapping, `ApplicationResponse.getStatusBadgeClass()` |
| Thêm NotificationType mới | Icon + background mới trong C-06 | `ui-spec.md` phần NotificationType mapping |
| Thêm màn hình mới | Thêm route vào SPECS 9.2/9.3 | Tạo section mới trong `ui-spec.md` + thêm row vào bảng này |
| Đổi phân quyền (role nào được xem gì) | `sec:authorize` trên nút/section | `ui-spec.md` phần admin action buttons |
| Thêm lĩnh vực dịch vụ mới | Grid lĩnh vực trên C-01 | Seed data, không cần update ui-spec |
| Đổi giới hạn upload (vd: 10MB → 20MB) | Error message | SPECS 10, `application.yml`, error message trong template |

---

## Quy tắc khi thêm tính năng mới

```
1. Viết vào SPECS.md trước (mô tả WHAT + rules)
2. Viết vào ui-spec.md (mô tả HOW nó trông)
3. Thêm row vào bảng Map trên (link 2 file)
4. Implement code
```

Không bao giờ implement UI trước khi có entry trong SPECS.md.
Không bao giờ implement business logic trước khi có entry trong SPECS.md.
