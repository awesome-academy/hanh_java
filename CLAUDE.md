# PSMS — Public Service Management System

Spring Boot monorepo · Thymeleaf SSR · MySQL 8.0 · JWT · Pure CSS

## Source of truth — thứ bậc rõ ràng

| File | Trả lời câu hỏi | Thay đổi khi |
|---|---|---|
| `SPECS.md` | Hệ thống làm GÌ, rule nghiệp vụ nào | Yêu cầu thay đổi |
| `psms_schema.sql` | DB có gì, column nào, kiểu dữ liệu nào | Schema thay đổi |
| `docs/ui-spec.md` | UI trông NHƯ THẾ NÀO, class CSS nào | Thiết kế thay đổi |
| `docs/cross-ref.md` | SPECS section nào ↔ UI screen nào | Thêm tính năng mới |
| `TASKS.md` | Thứ tự và phạm vi từng task | Planning thay đổi |

**Quy tắc:** SPECS.md không nói về CSS. ui-spec.md không nói về business rule.
Hai file không duplicate thông tin. cross-ref.md là cầu nối duy nhất.

## Khi bắt đầu một task — tra cứu theo thứ tự này

```
1. Đọc TASKS.md → xác định task thuộc Feature nào, các task liên quan, phạm vi file nào
2. Đọc SPECS.md section tương ứng → hiểu WHAT cần làm + business rules
3. Tra docs/cross-ref.md → biết UI screen nào liên quan
4. Nếu task có UI → đọc docs/ui-spec.md section đó → biết HOW trông thế nào
5. Kiểm tra psms_schema.sql → confirm entity/column trước khi code
6. Thực hiện xong, test pass → update TASKS.md (đánh dấu done, thêm note nếu có)
```
### Workflow Rules

### 1. Làm task theo từng bước — KHÔNG làm all-in

Trước khi code, phải:
1. **Thảo luận** với user về các bước sẽ làm (liệt kê rõ bước 1, bước 2...)
2. **Giải thích** keyword mới hoặc challenge kỹ thuật trước khi code
3. **Chờ xác nhận** rồi mới làm từng bước một
4. Những chỗ code tường minh rồi thì không cần comment. Chỉ comment với những chỗ logic phức tạp hoặc keyword mới
5. Sau mỗi bước, hỏi user muốn tiếp tục không. Không được làm all-in rồi mới show code. Cần chia nhỏ ra để user theo dõi và hiểu từng phần.
6. Cần đưa ra các solution như một dự án thực tế không phải là một bài tập algorithm. Cần giải thích trade-off giữa các solution, ưu nhược của từng approach, và tại sao chọn cách này mà không phải cách kia.
7. Có Annotation @Operation trên mỗi controller method để mô tả API endpoint đó làm gì, input output ra sao, và business rules liên quan. Đây là phần rất quan trọng vì nó giúp document API ngay trong code, và cũng giúp user hiểu rõ hơn về từng endpoint khi đọc code.
## Build & run commands

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # dev
./mvnw clean package -DskipTests                         # build
./mvnw test                                              # all tests
./mvnw test -Dtest=ClassName                             # single class
docker-compose up -d                                     # docker dev
mysql -u root -p psms < psms_schema.sql                  # init schema
```

## Key directories

```
src/main/java/com/psms/
├── config/       # Security, JWT, Async, Swagger
├── controller/
│   ├── client/   # REST /api/client/**
│   ├── admin/    # REST /api/admin/**
│   └── view/     # MVC → Thymeleaf (HTML)
├── service/
├── repository/
├── entity/
├── dto/          # request/ + response/
├── mapper/       # MapStruct only
├── exception/
├── util/
└── enums/

src/main/resources/
├── templates/    # layout/ auth/ client/ admin/ email/
└── static/       # css/ js/
```
