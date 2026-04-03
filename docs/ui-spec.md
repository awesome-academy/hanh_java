# UI Spec — PSMS GUI Reference

Tài liệu này trích xuất từ `psms_gui_mockup.html`.
Khi implement UI, tham chiếu file này để đảm bảo đúng layout,
class CSS, màu sắc và component pattern.

---

## Design tokens (CSS variables — định nghĩa trong base.css)

```css
:root {
  /* Client layout */
  --nav:     #0D3B7C;   /* topbar background */
  --gold:    #C9A84C;   /* logo accent */
  --gold2:   #F0D080;   /* active nav link */
  --bg:      #F0F4FA;   /* page background */
  --card:    #ffffff;   /* card background */
  --border:  #DDE3ED;   /* border color */
  --text:    #1A2540;   /* primary text */
  --muted:   #64748B;   /* secondary text */
  --light:   #EEF2F8;   /* subtle background */
  --success: #16A34A;
  --warn:    #D97706;
  --danger:  #DC2626;
  --info:    #2563EB;

  /* Admin layout */
  --side:    #111827;   /* sidebar background */
  --side2:   #1F2937;   /* sidebar hover */
  --side-ac: #3B82F6;   /* sidebar active item */
  --side-text: rgba(255,255,255,.65);

  --radius: 10px;
  --shadow: 0 2px 12px rgba(0,0,0,.07);
  --font: 'IBM Plex Sans', system-ui, sans-serif;
  --mono: 'IBM Plex Mono', monospace;
}
```

---

## Status badge classes (pill)

```html
<!-- Dùng class kết hợp: pill + màu theo status -->
<span class="pill p-blue">Đang xử lý</span>
<span class="pill p-amber">Tiếp nhận / Đang chờ</span>
<span class="pill p-green">Đã duyệt / Hoạt động</span>
<span class="pill p-red">Từ chối / Bị khóa</span>
<span class="pill p-gray">Nháp / Tạm dừng</span>
<span class="pill p-purple">Quản lý (role badge)</span>
```

```css
/* components.css */
.pill          { display:inline-flex; align-items:center; padding:2px 8px;
                 border-radius:99px; font-size:11px; font-weight:600; }
.p-blue        { background:#DBEAFE; color:#1D4ED8; }
.p-green       { background:#DCFCE7; color:#15803D; }
.p-amber       { background:#FEF9C3; color:#92400E; }
.p-red         { background:#FEE2E2; color:#B91C1C; }
.p-gray        { background:#F1F5F9; color:#475569; }
.p-purple      { background:#EDE9FE; color:#5B21B6; }
```

---

## ApplicationStatus → badge class mapping

| Status | Badge class | Label tiếng Việt |
|---|---|---|
| `DRAFT` | `p-gray` | Nháp |
| `SUBMITTED` | `p-amber` | Đã nộp |
| `RECEIVED` | `p-amber` | Đã tiếp nhận |
| `PROCESSING` | `p-blue` | Đang xử lý |
| `ADDITIONAL_REQUIRED` | `p-amber` | Yêu cầu bổ sung |
| `APPROVED` | `p-green` | Đã duyệt |
| `REJECTED` | `p-red` | Từ chối |

Dùng trong Thymeleaf:
```html
<span th:class="'pill ' + ${app.statusBadgeClass}"
      th:text="${app.statusLabel}">
</span>
```

Thêm vào `ApplicationResponse`:
```java
public String getStatusBadgeClass() {
    return switch (this.status) {
        case APPROVED             -> "p-green";
        case REJECTED             -> "p-red";
        case PROCESSING           -> "p-blue";
        case DRAFT                -> "p-gray";
        default                   -> "p-amber"; // SUBMITTED, RECEIVED, ADDITIONAL_REQUIRED
    };
}
public String getStatusLabel() {
    return switch (this.status) {
        case DRAFT                -> "Nháp";
        case SUBMITTED            -> "Đã nộp";
        case RECEIVED             -> "Đã tiếp nhận";
        case PROCESSING           -> "Đang xử lý";
        case ADDITIONAL_REQUIRED  -> "Yêu cầu bổ sung";
        case APPROVED             -> "Đã duyệt";
        case REJECTED             -> "Từ chối";
    };
}
```

---

## C-01 · Trang chủ (/)

**Layout:** `layout/client.html` — topbar + content

**Hero section:**
```html
<div class="hero">
  <div class="hero-text">
    <div class="hero-eyebrow">Cổng Dịch vụ công Quốc gia</div>
    <h1>Nộp hồ sơ trực tuyến<br>nhanh chóng, tiện lợi</h1>
    <p>Tra cứu và nộp hơn 200 dịch vụ hành chính công...</p>
    <div class="hero-search">
      <input class="search-inp" placeholder="Tìm kiếm dịch vụ công...">
      <button class="hero-btn">Tìm kiếm</button>
    </div>
  </div>
  <div class="hero-stats">
    <!-- stat-box × 3: Dịch vụ / Hồ sơ/tháng / Hài lòng -->
  </div>
</div>
```

**Category grid:** 4 cột, 8 lĩnh vực
```html
<div class="cats">  <!-- grid-template-columns: repeat(4,1fr) -->
  <div class="cat">
    <div class="cat-ic" style="background:#EFF6FF">🏛️</div>
    <p>Hành chính công</p>
    <small>52 dịch vụ</small>
  </div>
  <!-- ... 7 lĩnh vực còn lại -->
</div>
```

**Model attributes cần:**
```java
model.addAttribute("categories", categoryService.findAllActive());
model.addAttribute("popularServices", serviceTypeService.findTop5Popular());
model.addAttribute("stats", dashboardService.getPublicStats());
```

---

## C-02 · Danh mục dịch vụ (/services)

**Filter bar:**
```html
<form class="fbar" method="get" th:action="@{/services}">
  <input class="fi" type="text" name="keyword" th:value="${keyword}">
  <select class="fi-s" name="categoryId">
    <option value="">Tất cả lĩnh vực</option>
    <option th:each="c : ${categories}"
            th:value="${c.id}" th:text="${c.name}"
            th:selected="${c.id == categoryId}"></option>
  </select>
</form>
```

**Service row:**
```html
<div class="svc">
  <div class="svc-ic" style="background:#EFF6FF">📋</div>
  <div class="svc-info">
    <div class="svc-name" th:text="${svc.name}"></div>
    <div class="svc-meta">
      <span class="pill p-blue" th:text="${svc.categoryName}"></span>
      <span class="svc-time" th:text="${svc.processingTimeDays} + ' ngày'"></span>
      <span th:text="${svc.fee == 0} ? 'Miễn phí' : ${#numbers.formatDecimal(svc.fee,0,'COMMA')} + ' VNĐ'"></span>
    </div>
  </div>
  <span class="svc-dept" th:text="${svc.departmentName}"></span>
</div>
```

---

## C-03 · Danh sách hồ sơ (/applications)

**Table structure:**
```html
<table class="tbl">
  <thead>
    <tr>
      <th>Mã hồ sơ</th>
      <th>Tên dịch vụ</th>
      <th>Ngày nộp</th>
      <th>Hạn XL</th>
      <th>Trạng thái</th>
    </tr>
  </thead>
  <tbody>
    <tr th:each="app : ${applications.content}">
      <td>
        <a class="lnk"
           th:href="@{/applications/{id}(id=${app.id})}"
           th:text="${app.applicationCode}"></a>
      </td>
      <td th:text="${app.serviceTypeName}"></td>
      <td th:text="${#temporals.format(app.submittedAt,'dd/MM/yyyy')}"></td>
      <td th:text="${#temporals.format(app.processingDeadline,'dd/MM')}"></td>
      <td>
        <span th:class="'pill ' + ${app.statusBadgeClass}"
              th:text="${app.statusLabel}"></span>
      </td>
    </tr>
  </tbody>
</table>
```

---

## C-04 · Chi tiết hồ sơ (/applications/{id})

**2-column layout:**
```html
<div class="detail-g">  <!-- grid-template-columns: 1fr 240px -->
  <!-- Cột trái: thông tin + tài liệu + upload bổ sung -->
  <!-- Cột phải: timeline trạng thái -->
</div>
```

**Timeline item:**
```html
<div class="tl-item" th:each="h, iter : ${app.statusHistory}">
  <div style="position:relative">
    <div class="tl-dot" ...>icon</div>
    <div class="tl-line" th:unless="${iter.last}"></div>
  </div>
  <div>
    <div class="tl-ttl" th:text="${h.newStatusLabel}"></div>
    <div class="tl-dt"  th:text="${#temporals.format(h.changedAt,'dd/MM HH:mm')}"></div>
    <div class="tl-note" th:if="${h.notes}" th:text="${h.notes}"></div>
  </div>
</div>
```

---

## C-05 · Hồ sơ cá nhân (/profile)

**Profile header:**
```html
<div class="prof-hd">
  <div class="prof-ava" th:text="${#strings.substring(user.fullName,0,2)}"></div>
  <div>
    <h2 th:text="${user.fullName}"></h2>
    <p th:text="'Tham gia ' + ${#temporals.format(user.createdAt,'dd/MM/yyyy')}"></p>
  </div>
</div>
```

**Form 2 cột:**
```html
<div class="form-g">  <!-- grid-template-columns: 1fr 1fr -->
  <div class="fg">
    <label class="fl">Số CCCD</label>
    <input class="fi2 dis" th:value="${citizen.nationalId}" disabled>
    <!-- nationalId KHÔNG có th:field — chỉ hiển thị, không submit -->
  </div>
</div>
```

**Địa chỉ thường trú — display (view mode, full-width):**
```html
<!--
  Địa chỉ hiển thị là chuỗi ghép: permanent_address, ward, province.
  Format: "[số nhà, đường], [Phường/Xã], [Tỉnh/TP]"
-->
<div class="fg full">
  <div class="fl">Địa chỉ thường trú</div>
  <div class="fi2"
       th:text="${citizen.permanentAddress + ', ' + citizen.ward + ', ' + citizen.province}">
    123 Thanh Niên, Phường Tây Mỗ, TP.Hà Nội
  </div>
</div>
```

**Địa chỉ thường trú — edit mode (grid 2 cột, không có district):**
```html
<!--
  Trường permanentAddress (số nhà, đường) full-width.
  Ward + Province hiển thị song song — grid 2 cột.
-->
<div class="fg full">
  <label class="fl">Địa chỉ thường trú</label>
  <input class="fi2" th:field="*{permanentAddress}" placeholder="Số nhà, đường, khu vực...">
</div>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
  <div class="fg">
    <label class="fl">Phường/Xã</label>
    <input class="fi2" th:field="*{ward}" placeholder="Phường Tây Mỗ">
  </div>
  <div class="fg">
    <label class="fl">Tỉnh/TP</label>
    <input class="fi2" th:field="*{province}" placeholder="TP. Hà Nội">
  </div>
</div>
```

---

## C-06 · Thông báo (/notifications)

**Notification item:**
```html
<div class="notif-item" th:classappend="${!notif.isRead} ? ' ur'">
  <div class="notif-ava" ...>icon theo type</div>
  <div class="notif-body">
    <div class="notif-ttl" th:text="${notif.title}"></div>
    <div class="notif-d"   th:text="${notif.content}"></div>
    <div class="notif-t"   th:text="${#temporals.format(notif.createdAt,'dd/MM/yyyy HH:mm')}"></div>
  </div>
  <div class="ur-dot" th:if="${!notif.isRead}"></div>
</div>
```

**NotificationType → icon + background:**
```java
// Trong NotificationResponse hoặc Thymeleaf utility
APPLICATION_RECEIVED → icon "📨", bg "#EFF6FF"
ADDITIONAL_REQUIRED  → icon "📋", bg "#FEF9C3"
STATUS_UPDATED       → icon "⚙️", bg "#EFF6FF"
APPROVED             → icon "✅", bg "#F0FDF4"
REJECTED             → icon "❌", bg "#FEE2E2"
SYSTEM               → icon "🔔", bg "#F1F5F9"
```

---

## A-01 · Admin Dashboard (/admin/dashboard)

**KPI card:**
```html
<div class="kpi">
  <div class="kpi-ic">📁</div>  <!-- positioned absolute, opacity 0.13 -->
  <div class="kpi-lbl">Tổng hồ sơ</div>
  <div class="kpi-val" th:text="${stats.totalApplications}">0</div>
  <div class="kpi-d d-up" th:text="'↑ +' + ${stats.weeklyNew} + ' tuần này'"></div>
</div>
```

**CSS bar chart (không dùng JS library):**
```html
<div class="bar-r" th:each="item : ${categoryStats}">
  <div class="bar-lbl" th:text="${item.categoryName}"></div>
  <div class="bar-trk">
    <div class="bar-fill"
         th:style="'width:' + ${item.percentage} + '%;background:' + ${item.color}">
    </div>
  </div>
  <div class="bar-num" th:text="${item.count}"></div>
</div>
```

---

## A-02 → A-07 · Admin pages — layout pattern chung

**Sidebar active state:**
```html
<!-- layout/admin.html — xác định active item theo URL -->
<a class="sb-it"
   th:classappend="${#httpServletRequest.requestURI.startsWith('/admin/applications')} ? ' on'"
   th:href="@{/admin/applications}">
  <span class="sb-ic">📁</span>Hồ sơ
  <span class="sb-badge" th:if="${pendingCount > 0}"
        th:text="${pendingCount}"></span>
</a>
```

**Admin table action buttons:**
```html
<td style="display:flex;gap:4px">
  <a  th:href="@{/admin/{path}/{id}(id=${item.id})}" class="bsm bv">Xem</a>
  <button th:onclick="'openEditModal(' + ${item.id} + ')'"    class="bsm be">Sửa</button>
  <button th:onclick="'confirmDelete(' + ${item.id} + ')'"
          sec:authorize="hasRole('SUPER_ADMIN')"              class="bsm bd">Xóa</button>
</td>
```

**Button small classes:**
```css
.bsm  { padding:3px 9px; font-size:11px; border-radius:5px; border:none; cursor:pointer; font-weight:600; }
.bv   { background:#EFF6FF; color:var(--info); }     /* Xem */
.be   { background:#F0FDF4; color:var(--success); }  /* Sửa/Edit */
.bd   { background:#FEE2E2; color:var(--danger); }   /* Xóa/Delete */
.ba   { background:#DCFCE7; color:var(--success); }  /* Approve/Duyệt */
```

---

## Topbar — Client layout

```html
<!-- layout/client.html -->
<div class="topbar">
  <div class="topbar-logo">
    <div class="logo-icon">DV</div>
    <div class="logo-text">
      <strong>Cổng DVCQG</strong>
      <span>Dịch vụ công trực tuyến</span>
    </div>
  </div>
  <div class="nav-links">
    <a class="nl" th:classappend="${activeNav == 'home'} ? ' active'"
       th:href="@{/}">Trang chủ</a>
    <a class="nl" th:classappend="${activeNav == 'services'} ? ' active'"
       th:href="@{/services}">Dịch vụ công</a>
    <a class="nl" th:classappend="${activeNav == 'applications'} ? ' active'"
       th:href="@{/applications}">Hồ sơ của tôi</a>
    <a class="nl" th:classappend="${activeNav == 'profile'} ? ' active'"
       th:href="@{/profile}">Hồ sơ cá nhân</a>
    <a class="nl" th:classappend="${activeNav == 'notifications'} ? ' active'"
       th:href="@{/notifications}">Thông báo</a>
  </div>
  <div class="topbar-r">
    <button class="notif-btn" onclick="window.location='/notifications'">
      🔔
      <span class="notif-dot" id="notif-badge"
            th:if="${unreadCount > 0}"
            th:text="${unreadCount}"></span>
    </button>
    <div class="ava"
         sec:authentication="name"
         th:text="${#strings.substring(#authentication.principal.username,0,2)}">
    </div>
  </div>
</div>
```

**Active nav — truyền từ controller:**
```java
// Trong ClientViewController
@GetMapping("/services")
public String serviceList(Model model, ...) {
    model.addAttribute("activeNav", "services");
    // ...
}
```

---

## Sidebar — Admin layout

```html
<!-- layout/admin.html -->
<div class="sidebar">
  <div class="sb-logo">
    <div class="sb-logo-ic">DV</div>
    <div class="sb-ltext">
      <strong>Admin Portal</strong>
      <span>DVCQG System</span>
    </div>
  </div>
  <div style="flex:1;overflow-y:auto;padding-top:4px">
    <div class="sb-sec">Tổng quan</div>
    <a class="sb-it" th:classappend="${activePage == 'dashboard'} ? ' on'"
       th:href="@{/admin/dashboard}">
      <span class="sb-ic">📊</span>Dashboard
    </a>
    <div class="sb-sec">Quản lý</div>
    <a class="sb-it" th:classappend="${activePage == 'applications'} ? ' on'"
       th:href="@{/admin/applications}">
      <span class="sb-ic">📁</span>Hồ sơ
      <span class="sb-badge" th:if="${pendingCount > 0}"
            th:text="${pendingCount}"></span>
    </a>
    <!-- ... các menu items còn lại -->
  </div>
  <div class="sb-footer">
    <div class="sb-user">
      <div class="sb-ava" sec:authentication="name"
           th:text="${#strings.substring(#authentication.principal.username,0,2)}">
      </div>
      <div class="sb-uname">
        <strong sec:authentication="name"></strong>
        <span sec:authorize="hasRole('SUPER_ADMIN')">Super Admin</span>
        <span sec:authorize="hasRole('MANAGER')">Manager</span>
        <span sec:authorize="hasRole('STAFF')">Cán bộ</span>
      </div>
    </div>
  </div>
</div>
```

**Active page — truyền từ controller:**
```java
// Trong AdminViewController
@GetMapping("/admin/applications")
public String applicationList(Model model, ...) {
    model.addAttribute("activePage", "applications");
    model.addAttribute("pendingCount", applicationService.countPending());
    // ...
}
```

---

## Flash message — layout chung

```html
<!-- Đặt trong cả layout/client.html và layout/admin.html,
     ngay sau thẻ mở <div class="content"> hoặc <div class="adm-content"> -->
<div th:if="${success}" class="flash flash-success"
     th:text="${success}" id="flash-msg"></div>
<div th:if="${error}"   class="flash flash-error"
     th:text="${error}"  id="flash-msg"></div>
```

```css
/* components.css */
.flash              { padding:10px 16px; border-radius:8px; font-size:13px;
                      font-weight:500; margin-bottom:14px; animation: fadeIn .2s; }
.flash-success      { background:#DCFCE7; color:#15803D; border:1px solid #86EFAC; }
.flash-error        { background:#FEE2E2; color:#B91C1C; border:1px solid #FCA5A5; }
```

```javascript
// main.js — tự ẩn sau 3 giây
const flash = document.getElementById('flash-msg');
if (flash) setTimeout(() => flash.style.opacity = '0', 3000);
```

---

## Confirm dialog — dùng cho delete/lock

```javascript
// main.js
function confirmAction(message, formId) {
  if (confirm(message)) {
    document.getElementById(formId).submit();
  }
}
```

```html
<!-- Trong template -->
<form th:id="'del-' + ${item.id}"
      th:action="@{/admin/users/{id}(id=${item.id})}"
      method="post" style="display:none">
  <input type="hidden" name="_method" value="DELETE">
</form>
<button onclick="confirmAction('Xóa người dùng này?', 'del-[[${item.id}]]')"
        class="bsm bd">Xóa</button>
```
