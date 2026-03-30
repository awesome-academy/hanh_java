# Hướng dẫn thêm cross-link vào SPECS.md và ui-spec.md

Không rewrite toàn bộ file. Chỉ thêm 1 dòng comment vào đầu
mỗi section để agent biết cần đọc file nào khi implement.

---

## Thêm vào SPECS.md

Tìm từng heading và thêm dòng `> UI:` ngay bên dưới:

```markdown
### 4.3 Danh mục dịch vụ công (`GET /services`)
> UI: **C-02** trong `docs/ui-spec.md` — filter bar, service row, pagination

### 4.4 Nộp hồ sơ (`GET/POST /applications/submit`)
> UI: form trong `docs/ui-spec.md` — xem phần C-01 (search) + C-04 detail

### 4.5 Danh sách hồ sơ đã nộp (`GET /applications`)
> UI: **C-03** trong `docs/ui-spec.md` — table structure, badge classes

### 4.6 Chi tiết hồ sơ (`GET /applications/{id}`)
> UI: **C-04** trong `docs/ui-spec.md` — 2-column layout, timeline

### 4.2 Hồ sơ cá nhân (`GET /profile`)
> UI: **C-05** trong `docs/ui-spec.md` — form 2 cột, disabled field CCCD

### 4.7 Thông báo (`GET /notifications`)
> UI: **C-06** trong `docs/ui-spec.md` — notification item, unread dot

### 5.2 Dashboard (`GET /admin/dashboard`)
> UI: **A-01** trong `docs/ui-spec.md` — KPI card, CSS bar chart

### 5.3 Quản lý hồ sơ (`GET /admin/applications`)
> UI: **A-02** trong `docs/ui-spec.md` — admin table, action buttons, filter bar
```

---

## Thêm vào docs/ui-spec.md

Tìm từng heading màn hình và thêm dòng `> Specs:` ngay bên dưới:

```markdown
## C-02 · Danh mục dịch vụ (/services)
> Specs: SPECS.md **Section 4.3** — filter rules, is_active=true only, pagination size

## C-03 · Danh sách hồ sơ (/applications)
> Specs: SPECS.md **Section 4.5** — chỉ xem hồ sơ của mình, sort submitted_at DESC

## C-04 · Chi tiết hồ sơ (/applications/{id})
> Specs: SPECS.md **Section 4.6** — upload chỉ khi ADDITIONAL_REQUIRED, history đầy đủ

## C-05 · Hồ sơ cá nhân (/profile)
> Specs: SPECS.md **Section 4.2** — không sửa CCCD (disabled), validate phone/email

## C-06 · Thông báo (/notifications)
> Specs: SPECS.md **Section 4.7** — badge count, toggle email notif

## A-01 · Admin Dashboard (/admin/dashboard)
> Specs: SPECS.md **Section 5.2** — 4 KPI definitions, recent pending = SUBMITTED+RECEIVED

## A-02 → A-07 · Admin pages
> Specs: SPECS.md **Sections 5.3–5.8** — business rules và phân quyền từng trang
```

---

## Kết quả sau khi thêm

Agent khi đọc SPECS section 4.5 sẽ thấy ngay: "→ đọc C-03 trong ui-spec.md".
Agent khi đọc ui-spec C-03 sẽ thấy ngay: "→ business rules ở SPECS 4.5".

Không có thông tin nào bị duplicate giữa 2 file.
