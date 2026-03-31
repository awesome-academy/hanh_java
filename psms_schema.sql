-- =============================================================
--  PUBLIC SERVICE MANAGEMENT SYSTEM — Database Schema
--  Target : MySQL 8.0+ / MySQL Workbench
--  Charset: utf8mb4 / COLLATE utf8mb4_unicode_ci
--  Author : PSMS Dev Team
--  Version: 1.1.0  — DDL only (schema + indexes, không có seed data)
-- =============================================================

SET FOREIGN_KEY_CHECKS = 0;
SET SQL_MODE = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- -------------------------------------------------------------
-- DROP các bảng cũ (thứ tự ngược FK dependency)
-- Chạy an toàn nhiều lần — không mất data ngoài ý muốn nếu
-- bạn chỉ muốn update schema, hãy comment block này lại
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `activity_logs`;
DROP TABLE IF EXISTS `notifications`;
DROP TABLE IF EXISTS `application_status_history`;
DROP TABLE IF EXISTS `application_documents`;
DROP TABLE IF EXISTS `applications`;
DROP TABLE IF EXISTS `service_types`;
DROP TABLE IF EXISTS `service_categories`;
DROP TABLE IF EXISTS `staff`;
DROP TABLE IF EXISTS `citizens`;
DROP TABLE IF EXISTS `departments`;
DROP TABLE IF EXISTS `user_roles`;
DROP TABLE IF EXISTS `refresh_tokens`;        -- FK → users, phải drop trước users
DROP TABLE IF EXISTS `revoked_access_tokens`; -- không có FK, drop ở đây cho gọn
DROP TABLE IF EXISTS `users`;
DROP TABLE IF EXISTS `roles`;

-- -------------------------------------------------------------
-- 0. CREATE & USE DATABASE
-- -------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `psms`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `psms`;


-- =============================================================
-- DOMAIN 1 — AUTH & USERS
-- =============================================================

-- -------------------------------------------------------------
-- 1.1  roles
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `roles` (
  `id`          TINYINT      UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(30)  NOT NULL COMMENT 'CITIZEN | STAFF | MANAGER | SUPER_ADMIN',
  `description` VARCHAR(255) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_role_name` (`name`)
    COMMENT 'Lookup bằng tên role'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Hệ thống vai trò (RBAC)';

INSERT INTO `roles` (`name`, `description`) VALUES
  ('CITIZEN',     'Công dân — nộp và tra cứu hồ sơ'),
  ('STAFF',       'Cán bộ — xử lý hồ sơ trong phạm vi phân công'),
  ('MANAGER',     'Quản lý — phê duyệt, chuyển tiếp, trả hồ sơ'),
  ('SUPER_ADMIN', 'Quản trị viên — toàn quyền hệ thống');


-- -------------------------------------------------------------
-- 1.2  users
--
--  INDEX STRATEGY:
--    uq_users_email            → Login: WHERE email = ?
--    uq_users_eid              → OAuth2: WHERE eid_provider=? AND eid_subject=?
--    idx_users_active          → Filter tài khoản: WHERE is_active=1 AND is_locked=0
--    idx_users_created_at  [+] → Admin list: ORDER BY created_at DESC (sort mặc định)
--    ft_users_search       [+] → Admin search: MATCH(full_name,email) AGAINST('keyword')
--                                Thay thế LIKE '%keyword%' không dùng được index
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `id`                     BIGINT       UNSIGNED NOT NULL AUTO_INCREMENT,
  `email`                  VARCHAR(180) NOT NULL,
  `password`               VARCHAR(255) NOT NULL COMMENT 'BCrypt hash',
  `full_name`              VARCHAR(100) NOT NULL,
  `phone`                  VARCHAR(20)  NULL,
  `is_active`              TINYINT(1)   NOT NULL DEFAULT 1,
  `is_locked`              TINYINT(1)   NOT NULL DEFAULT 0,
  `failed_login_count`     TINYINT      NOT NULL DEFAULT 0 COMMENT 'Brute force: đếm lần login sai liên tiếp, reset khi thành công',
  `locked_until`           DATETIME     NULL     COMMENT 'Brute force: tạm khóa đến thời điểm này, NULL = không bị khóa tạm',
  `email_notif_enabled`    TINYINT(1)   NOT NULL DEFAULT 1,
  `eid_provider`           VARCHAR(50)  NULL     COMMENT 'VD: VNeID, Google',
  `eid_subject`            VARCHAR(255) NULL     COMMENT 'Subject ID từ nhà cung cấp eID',
  `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_login_at`          DATETIME     NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_users_email`
    (`email`)
    COMMENT 'Đảm bảo email duy nhất + tốc độ login',
  UNIQUE KEY `uq_users_eid`
    (`eid_provider`, `eid_subject`)
    COMMENT 'OAuth2/eID: tránh duplicate account từ 1 provider',
  KEY `idx_users_active`
    (`is_active`, `is_locked`)
    COMMENT 'Filter tài khoản hoạt động: WHERE is_active=1 AND is_locked=0',
  KEY `idx_users_created_at`
    (`created_at` DESC)
    COMMENT '[MỚI] Admin user list: ORDER BY created_at DESC — sort mặc định',
  FULLTEXT KEY `ft_users_search`
    (`full_name`, `email`)
    COMMENT '[MỚI] Admin search: MATCH(full_name,email) AGAINST(?) — nhanh hơn LIKE %keyword%'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Tài khoản người dùng (công dân + cán bộ + admin)';


-- -------------------------------------------------------------
-- 1.3  user_roles  (many-to-many)
--
--  INDEX STRATEGY:
--    PK (user_id, role_id)      → Lookup roles của 1 user; tránh duplicate
--    FK role_id                 → MySQL tự tạo index khi có FK
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_roles` (
  `user_id`     BIGINT   UNSIGNED NOT NULL,
  `role_id`     TINYINT  UNSIGNED NOT NULL,
  `assigned_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `role_id`),
  CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Bảng trung gian user ↔ role';


-- -------------------------------------------------------------
-- 1.4  departments
--
--  INDEX STRATEGY:
--    uq_dept_code               → Lookup theo mã phòng ban
--    idx_dept_active            → Filter phòng ban đang hoạt động
--    idx_dept_leader        [+] → FK index: JOIN users để lấy tên trưởng phòng
--                                 Không có index → full scan users mỗi lần JOIN
--    idx_dept_active_name   [+] → Admin list: WHERE is_active=1 ORDER BY name
--                                 Composite tối ưu hơn 2 index riêng lẻ
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `departments` (
  `id`          BIGINT       UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(20)  NOT NULL COMMENT 'VD: PB-001',
  `name`        VARCHAR(200) NOT NULL,
  `address`     TEXT         NULL,
  `phone`       VARCHAR(20)  NULL,
  `email`       VARCHAR(180) NULL,
  `leader_id`   BIGINT       UNSIGNED NULL COMMENT 'FK → users.id',
  `is_active`   TINYINT(1)   NOT NULL DEFAULT 1,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_dept_code`
    (`code`)
    COMMENT 'Mã phòng ban phải duy nhất',
  KEY `idx_dept_active`
    (`is_active`)
    COMMENT 'Filter phòng ban đang hoạt động',
  KEY `idx_dept_leader`
    (`leader_id`)
    COMMENT '[MỚI] FK index: tránh full scan users khi JOIN lấy thông tin trưởng phòng',
  KEY `idx_dept_active_name`
    (`is_active`, `name`)
    COMMENT '[MỚI] Admin list: WHERE is_active=1 ORDER BY name — tránh filesort'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Cơ quan / Phòng ban tiếp nhận và xử lý hồ sơ';


-- -------------------------------------------------------------
-- 1.5  citizens
--
--  INDEX STRATEGY:
--    uq_citizen_user        → 1-1 với users, lookup profile citizen
--    uq_citizen_national_id → Tránh CCCD trùng + lookup khi đăng ký
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `citizens` (
  `id`                BIGINT       UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`           BIGINT       UNSIGNED NOT NULL,
  `national_id`       VARCHAR(20)  NOT NULL COMMENT 'Số CCCD / CMND — bất biến',
  `date_of_birth`     DATE         NULL,
  `gender`            ENUM('MALE','FEMALE','OTHER') NULL,
  `permanent_address` TEXT         NULL,
  `ward`              VARCHAR(100) NULL,
  `district`          VARCHAR(100) NULL,
  `province`          VARCHAR(100) NULL,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_citizen_user`
    (`user_id`)
    COMMENT 'Mỗi user chỉ có 1 citizen profile',
  UNIQUE KEY `uq_citizen_national_id`
    (`national_id`)
    COMMENT 'Số CCCD phải duy nhất trong hệ thống',
  CONSTRAINT `fk_citizen_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Hồ sơ công dân mở rộng (1-1 với users)';


-- -------------------------------------------------------------
-- 1.6  staff
--
--  INDEX STRATEGY:
--    uq_staff_user               → 1-1 với users, lookup profile cán bộ
--    uq_staff_code               → Lookup theo mã cán bộ
--    idx_staff_dept_avail    [↑] → Thay idx_staff_dept(department_id):
--                                   WHERE department_id=? AND is_available=1
--                                   Dùng khi phân công — chỉ hiện CB sẵn sàng
--                                   Leftmost prefix (department_id) vẫn cover
--                                   query chỉ filter theo phòng ban
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `staff` (
  `id`             BIGINT       UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT       UNSIGNED NOT NULL,
  `staff_code`     VARCHAR(20)  NOT NULL COMMENT 'VD: CB-001',
  `department_id`  BIGINT       UNSIGNED NOT NULL,
  `position`       VARCHAR(100) NULL     COMMENT 'Chức vụ / chức danh',
  `is_available`   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0 = đang nghỉ phép',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_staff_user`
    (`user_id`)
    COMMENT 'Mỗi user chỉ có 1 staff profile',
  UNIQUE KEY `uq_staff_code`
    (`staff_code`)
    COMMENT 'Mã cán bộ phải duy nhất',
  KEY `idx_staff_dept_avail`
    (`department_id`, `is_available`)
    COMMENT '[NÂNG CẤP] Thay idx_staff_dept(dept_id):
             WHERE dept_id=? AND is_available=1 — phân công hồ sơ cho CB sẵn sàng',
  CONSTRAINT `fk_staff_user`       FOREIGN KEY (`user_id`)       REFERENCES `users`       (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_staff_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Hồ sơ cán bộ mở rộng (1-1 với users)';


-- FK departments.leader_id (thêm sau khi cả 2 bảng đã tồn tại)
ALTER TABLE `departments`
  ADD CONSTRAINT `fk_dept_leader`
    FOREIGN KEY (`leader_id`) REFERENCES `users` (`id`) ON DELETE SET NULL;


-- =============================================================
-- DOMAIN 2 — SERVICES
-- =============================================================

-- -------------------------------------------------------------
-- 2.1  service_categories
--
--  INDEX STRATEGY:
--    uq_svccat_code             → Lookup theo mã lĩnh vực
--    idx_svccat_active_order[+] → Trang chủ + catalog:
--                                  WHERE is_active=1 ORDER BY sort_order
--                                  Tránh filesort khi render grid 8 lĩnh vực
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `service_categories` (
  `id`          INT          UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(20)  NOT NULL COMMENT 'VD: HANH_CHINH, GIAO_DUC',
  `name`        VARCHAR(100) NOT NULL,
  `description` TEXT         NULL,
  `icon`        VARCHAR(100) NULL     COMMENT 'Icon slug hoặc URL',
  `sort_order`  SMALLINT     NOT NULL DEFAULT 0,
  `is_active`   TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_svccat_code`
    (`code`)
    COMMENT 'Mã lĩnh vực phải duy nhất',
  KEY `idx_svccat_active_order`
    (`is_active`, `sort_order`)
    COMMENT '[MỚI] Trang chủ & catalog: WHERE is_active=1 ORDER BY sort_order — không filesort'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Lĩnh vực / danh mục dịch vụ công';

INSERT INTO `service_categories` (`code`, `name`, `sort_order`) VALUES
  ('HANH_CHINH',  'Hành chính công',         1),
  ('GIAO_DUC',    'Giáo dục & Đào tạo',      2),
  ('Y_TE',        'Y tế & Sức khỏe',          3),
  ('XAY_DUNG',    'Xây dựng',                 4),
  ('TAI_NGUYEN',  'Tài nguyên & Môi trường',  5),
  ('GIAO_THONG',  'Giao thông vận tải',       6),
  ('LAO_DONG',    'Lao động & Việc làm',      7),
  ('TU_PHAP',     'Tư pháp',                  8);


-- -------------------------------------------------------------
-- 2.2  service_types
--
--  INDEX STRATEGY:
--    uq_svctype_code              → Lookup theo mã DV
--    idx_svctype_cat_active   [↑] → Thay idx_svctype_category(cat_id):
--                                    WHERE category_id=? AND is_active=1
--                                    Service catalog filter theo lĩnh vực
--    idx_svctype_dept_active  [↑] → Thay idx_svctype_dept(dept_id):
--                                    WHERE department_id=? AND is_active=1
--                                    Dropdown chọn DV theo phòng ban
--    idx_svctype_active           → WHERE is_active=1 (không filter category)
--    idx_svctype_created_by   [+] → FK index: JOIN users — tránh full scan
--    ft_svctype_search        [+] → Service catalog search:
--                                    MATCH(name,description) AGAINST('keyword' IN BOOLEAN MODE)
--                                    Hiệu năng vượt trội so với LIKE '%keyword%'
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `service_types` (
  `id`                    BIGINT        UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`                  VARCHAR(30)   NOT NULL     COMMENT 'VD: DV-001',
  `name`                  VARCHAR(200)  NOT NULL,
  `category_id`           INT           UNSIGNED NOT NULL,
  `department_id`         BIGINT        UNSIGNED NOT NULL,
  `description`           TEXT          NULL,
  `requirements`          TEXT          NULL         COMMENT 'Danh sách giấy tờ yêu cầu (Markdown/plain)',
  `processing_time_days`  SMALLINT      NOT NULL DEFAULT 5  COMMENT 'Số ngày làm việc tối đa',
  `fee`                   DECIMAL(12,0) NOT NULL DEFAULT 0  COMMENT 'Lệ phí (VNĐ), 0 = miễn phí',
  `fee_description`       VARCHAR(255)  NULL,
  `is_active`             TINYINT(1)    NOT NULL DEFAULT 1,
  `created_by`            BIGINT        UNSIGNED NULL,
  `created_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_svctype_code`
    (`code`)
    COMMENT 'Mã dịch vụ phải duy nhất',
  KEY `idx_svctype_cat_active`
    (`category_id`, `is_active`)
    COMMENT '[NÂNG CẤP] Thay idx_svctype_category:
             WHERE category_id=? AND is_active=1 — catalog filter theo lĩnh vực',
  KEY `idx_svctype_dept_active`
    (`department_id`, `is_active`)
    COMMENT '[NÂNG CẤP] Thay idx_svctype_dept:
             WHERE department_id=? AND is_active=1 — DV theo phòng ban',
  KEY `idx_svctype_active`
    (`is_active`)
    COMMENT 'List tất cả DV active, không filter theo lĩnh vực',
  KEY `idx_svctype_created_by`
    (`created_by`)
    COMMENT '[MỚI] FK index: tránh full scan users khi JOIN lấy tên người tạo DV',
  FULLTEXT KEY `ft_svctype_search`
    (`name`, `description`)
    COMMENT '[MỚI] Service catalog keyword search:
             MATCH(name,description) AGAINST(? IN BOOLEAN MODE) — thay LIKE %keyword%',
  CONSTRAINT `fk_svctype_category`   FOREIGN KEY (`category_id`)   REFERENCES `service_categories` (`id`),
  CONSTRAINT `fk_svctype_department` FOREIGN KEY (`department_id`) REFERENCES `departments`        (`id`),
  CONSTRAINT `fk_svctype_creator`    FOREIGN KEY (`created_by`)    REFERENCES `users`              (`id`) ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Danh mục dịch vụ công trực tuyến';


-- =============================================================
-- DOMAIN 3 — APPLICATIONS
-- =============================================================

-- -------------------------------------------------------------
-- 3.1  applications
--
--  INDEX STRATEGY:
--    uq_app_code                  → Tra cứu HS-20260330-00001
--
--    idx_app_citizen_submitted[↑] → Thay idx_app_citizen_status(citizen_id, status):
--                                    3-column composite:
--                                    (1) Citizen list no filter: WHERE citizen_id=? ORDER BY submitted_at DESC
--                                        → prefix (citizen_id, -, submitted_at)
--                                    (2) Citizen list with status: WHERE citizen_id=? AND status=? ORDER BY submitted_at DESC
--                                        → prefix (citizen_id, status, submitted_at)
--                                    Column submitted_at tránh filesort ORDER BY
--
--    idx_app_staff_status         → Workload CB: WHERE assigned_staff_id=? AND status NOT IN(...)
--    idx_app_service              → Dashboard chart: GROUP BY service_type_id theo tháng
--    idx_app_status_deadline      → Dashboard KPI quá hạn: WHERE status=? AND deadline < NOW()
--
--    idx_app_status_submitted [+] → (1) Admin list: WHERE status=? ORDER BY submitted_at DESC
--                                    (2) Dashboard recent pending:
--                                        WHERE status IN('SUBMITTED','RECEIVED') ORDER BY submitted_at LIMIT 10
--                                    → Không cần filesort vì submitted_at là cột thứ 2
--
--    idx_app_submitted        [+] → Export CSV: WHERE submitted_at BETWEEN ? AND ?
--                                    Admin multi-filter: ORDER BY submitted_at DESC (no status filter)
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `applications` (
  `id`                  BIGINT       UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_code`    VARCHAR(30)  NOT NULL     COMMENT 'VD: HS-20260330-00001',
  `citizen_id`          BIGINT       UNSIGNED NOT NULL,
  `service_type_id`     BIGINT       UNSIGNED NOT NULL,
  `status`              ENUM(
                          'DRAFT',
                          'SUBMITTED',
                          'RECEIVED',
                          'PROCESSING',
                          'ADDITIONAL_REQUIRED',
                          'APPROVED',
                          'REJECTED'
                        ) NOT NULL DEFAULT 'DRAFT',
  `submitted_at`        DATETIME     NULL,
  `received_at`         DATETIME     NULL,
  `processing_deadline` DATE         NULL         COMMENT 'Ngày hết hạn xử lý',
  `completed_at`        DATETIME     NULL,
  `assigned_staff_id`   BIGINT       UNSIGNED NULL,
  `notes`               TEXT         NULL         COMMENT 'Ghi chú nội bộ của cán bộ',
  `rejection_reason`    TEXT         NULL,
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_app_code`
    (`application_code`)
    COMMENT 'Tra cứu nhanh theo mã hồ sơ',
  KEY `idx_app_citizen_submitted`
    (`citizen_id`, `status`, `submitted_at` DESC)
    COMMENT '[NÂNG CẤP] Thay idx_app_citizen_status:
             3-column composite tránh filesort ORDER BY submitted_at;
             prefix (citizen_id) cover query không filter status',
  KEY `idx_app_staff_status`
    (`assigned_staff_id`, `status`)
    COMMENT 'Workload cán bộ: WHERE assigned_staff_id=? AND status NOT IN(APPROVED,REJECTED)',
  KEY `idx_app_service`
    (`service_type_id`, `created_at`)
    COMMENT 'Dashboard chart: đếm HS theo dịch vụ + tháng',
  KEY `idx_app_status_deadline`
    (`status`, `processing_deadline`)
    COMMENT 'Dashboard KPI quá hạn: WHERE status NOT IN(...) AND processing_deadline < NOW()',
  KEY `idx_app_status_submitted`
    (`status`, `submitted_at` DESC)
    COMMENT '[MỚI] Admin list: WHERE status=? ORDER BY submitted_at DESC;
             Dashboard recent pending: WHERE status IN(...) ORDER BY submitted_at LIMIT 10',
  KEY `idx_app_submitted`
    (`submitted_at` DESC)
    COMMENT '[MỚI] Export CSV date range: WHERE submitted_at BETWEEN ? AND ?;
             Admin filter từ ngày–đến ngày không kèm status',
  CONSTRAINT `fk_app_citizen`      FOREIGN KEY (`citizen_id`)        REFERENCES `users`         (`id`),
  CONSTRAINT `fk_app_service_type` FOREIGN KEY (`service_type_id`)   REFERENCES `service_types` (`id`),
  CONSTRAINT `fk_app_staff`        FOREIGN KEY (`assigned_staff_id`) REFERENCES `users`         (`id`) ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Hồ sơ dịch vụ công do công dân nộp';


-- -------------------------------------------------------------
-- 3.2  application_documents
--
--  INDEX STRATEGY:
--    idx_appdoc_app              → Load tất cả tài liệu của 1 hồ sơ
--    idx_appdoc_uploader         → FK index: tài liệu do ai upload
--    idx_appdoc_app_response [+] → Tách tài liệu citizen nộp vs phản hồi cán bộ:
--                                   WHERE application_id=? AND is_response=0 (citizen docs)
--                                   WHERE application_id=? AND is_response=1 (staff response)
--                                   Trang chi tiết HS render 2 section riêng biệt
--    idx_appdoc_app_valid    [+] → Filter tài liệu theo trạng thái validate:
--                                   WHERE application_id=? AND validation_status='PENDING'
--                                   Cán bộ xem danh sách file cần kiểm tra
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `application_documents` (
  `id`                BIGINT       UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_id`    BIGINT       UNSIGNED NOT NULL,
  `file_name`         VARCHAR(255) NOT NULL,
  `file_path`         VARCHAR(500) NOT NULL COMMENT 'Đường dẫn lưu trữ (S3 / local)',
  `file_type`         VARCHAR(20)  NOT NULL COMMENT 'pdf | jpg | png | docx ...',
  `file_size`         BIGINT       NOT NULL DEFAULT 0 COMMENT 'Bytes',
  `document_type`     VARCHAR(50)  NULL     COMMENT 'CCCD | KHAI_SINH | GPXD ...',
  `uploaded_by`       BIGINT       UNSIGNED NOT NULL,
  `uploaded_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_response`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 = tài liệu phản hồi từ cán bộ',
  `validation_status` ENUM('PENDING','VALID','INVALID') NOT NULL DEFAULT 'PENDING',
  PRIMARY KEY (`id`),
  KEY `idx_appdoc_app`
    (`application_id`)
    COMMENT 'Load tất cả tài liệu của 1 hồ sơ',
  KEY `idx_appdoc_uploader`
    (`uploaded_by`)
    COMMENT 'FK index: tài liệu do ai upload',
  KEY `idx_appdoc_app_response`
    (`application_id`, `is_response`)
    COMMENT '[MỚI] Tách citizen doc (0) vs staff response doc (1) trên trang chi tiết HS',
  KEY `idx_appdoc_app_valid`
    (`application_id`, `validation_status`)
    COMMENT '[MỚI] Filter tài liệu theo trạng thái validate — cán bộ kiểm tra từng file',
  CONSTRAINT `fk_appdoc_app`      FOREIGN KEY (`application_id`) REFERENCES `applications` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_appdoc_uploader` FOREIGN KEY (`uploaded_by`)    REFERENCES `users`        (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Tài liệu / tệp đính kèm của hồ sơ';


-- -------------------------------------------------------------
-- 3.3  application_status_history
--
--  INDEX STRATEGY:
--    idx_ash_app         → Timeline hồ sơ: WHERE application_id=? ORDER BY changed_at
--                          Column thứ 2 (changed_at) tránh filesort khi render timeline
--    idx_ash_changed_by  → FK index: lịch sử thao tác của 1 cán bộ cụ thể
--    [+]                   Dùng trong admin — "hồ sơ cán bộ đã xử lý"
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `application_status_history` (
  `id`             BIGINT      UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT      UNSIGNED NOT NULL,
  `old_status`     VARCHAR(30) NULL     COMMENT 'NULL khi tạo mới',
  `new_status`     VARCHAR(30) NOT NULL,
  `changed_by`     BIGINT      UNSIGNED NOT NULL,
  `notes`          TEXT        NULL,
  `changed_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ash_app`
    (`application_id`, `changed_at`)
    COMMENT 'Timeline hồ sơ: WHERE app_id=? ORDER BY changed_at — tránh filesort',
  KEY `idx_ash_changed_by`
    (`changed_by`)
    COMMENT '[MỚI] FK index: lịch sử thao tác của cán bộ; tránh full scan khi JOIN users',
  CONSTRAINT `fk_ash_app`        FOREIGN KEY (`application_id`) REFERENCES `applications` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ash_changed_by` FOREIGN KEY (`changed_by`)     REFERENCES `users`        (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Lịch sử thay đổi trạng thái hồ sơ (audit trail)';


-- =============================================================
-- DOMAIN 4 — NOTIFICATIONS & ACTIVITY LOGS
-- =============================================================

-- -------------------------------------------------------------
-- 4.1  notifications
--
--  INDEX STRATEGY:
--    idx_notif_user_read  → (1) Danh sách: WHERE user_id=? ORDER BY created_at DESC
--                           (2) Filter unread: WHERE user_id=? AND is_read=0 ORDER BY created_at DESC
--                           (3) Badge count: SELECT COUNT(*) WHERE user_id=? AND is_read=0
--                           Prefix (user_id) cover tất cả 3 query trên
--    idx_notif_app        → FK index: notification liên quan đến hồ sơ nào
--    idx_notif_user_type  → Filter thông báo theo loại:
--    [+]                    WHERE user_id=? AND type='APPROVED'
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `notifications` (
  `id`             BIGINT       UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT       UNSIGNED NOT NULL,
  `application_id` BIGINT       UNSIGNED NULL,
  `type`           ENUM(
                     'APPLICATION_RECEIVED',
                     'ADDITIONAL_REQUIRED',
                     'STATUS_UPDATED',
                     'APPROVED',
                     'REJECTED',
                     'SYSTEM'
                   ) NOT NULL,
  `title`          VARCHAR(200) NOT NULL,
  `content`        TEXT         NOT NULL,
  `is_read`        TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_at`        DATETIME     NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notif_user_read`
    (`user_id`, `is_read`, `created_at` DESC)
    COMMENT 'Danh sách thông báo + đếm unread badge:
             WHERE user_id=? [AND is_read=0] ORDER BY created_at DESC',
  KEY `idx_notif_app`
    (`application_id`)
    COMMENT 'FK index: notification liên quan đến hồ sơ nào',
  KEY `idx_notif_user_type`
    (`user_id`, `type`)
    COMMENT '[MỚI] Filter thông báo theo loại: WHERE user_id=? AND type=?',
  CONSTRAINT `fk_notif_user` FOREIGN KEY (`user_id`)        REFERENCES `users`        (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_notif_app`  FOREIGN KEY (`application_id`) REFERENCES `applications` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Thông báo gửi đến người dùng';


-- -------------------------------------------------------------
-- 4.2  activity_logs
--
--  INDEX STRATEGY  (đã tối ưu từ v1.0 — giữ nguyên):
--    idx_log_user_date  → Log của 1 user: WHERE user_id=? ORDER BY created_at DESC
--    idx_log_entity     → Lịch sử 1 entity: WHERE entity_type=? AND entity_id=?
--    idx_log_action     → Filter theo action: WHERE action=? ORDER BY created_at DESC
--    idx_log_date       → Sort toàn bộ log: ORDER BY created_at DESC (no user filter)
--
--  Lý do KHÔNG thêm index ip_address:
--    Cardinality thấp (nhiều request cùng IP qua proxy/NAT/load-balancer)
--    Security audit thường filter theo date trước → full scan trên subset nhỏ
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `activity_logs` (
  `id`          BIGINT      UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT      UNSIGNED NULL     COMMENT 'NULL nếu hành động bởi hệ thống',
  `action`      VARCHAR(50) NOT NULL,
  `entity_type` VARCHAR(50) NULL              COMMENT 'applications | users | service_types | departments | staff',
  `entity_id`   VARCHAR(50) NULL              COMMENT 'ID của entity bị tác động',
  `description` TEXT        NOT NULL,
  `ip_address`  VARCHAR(45) NULL              COMMENT 'IPv4 hoặc IPv6',
  `user_agent`  TEXT        NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log_user_date`
    (`user_id`, `created_at` DESC)
    COMMENT 'Log của 1 user theo thứ tự thời gian',
  KEY `idx_log_entity`
    (`entity_type`, `entity_id`)
    COMMENT 'Lịch sử của 1 entity cụ thể (application/user/dept)',
  KEY `idx_log_action`
    (`action`, `created_at` DESC)
    COMMENT 'Admin log filter theo loại action',
  KEY `idx_log_date`
    (`created_at` DESC)
    COMMENT 'Sort toàn bộ log theo thời gian — không kèm filter user hay action',
  CONSTRAINT `fk_log_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Nhật ký hoạt động toàn hệ thống (audit log)';


-- =============================================================
-- DOMAIN 5 — TOKEN MANAGEMENT
-- =============================================================

-- -------------------------------------------------------------
-- 5.1  refresh_tokens
--
--  Mục đích: Lưu refresh token hợp lệ — hỗ trợ token rotation
--  và revoke khi logout hoặc phát hiện bất thường.
--
--  INDEX STRATEGY:
--    uq_refresh_token          → Lookup token khi client gửi lên (UNIQUE)
--    idx_refresh_tokens_user   → Lấy tất cả token của 1 user (revoke all devices)
--    idx_refresh_tokens_expires→ Cleanup job: DELETE WHERE expires_at < NOW()
--
--  FLOW:
--    Login  → INSERT refresh_token
--    Refresh→ SELECT by token → validate → DELETE old → INSERT new (rotation)
--    Logout → DELETE by token (hoặc by user_id để revoke all)
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `refresh_tokens` (
  `id`         BIGINT       UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       UNSIGNED NOT NULL,
  `token`      VARCHAR(512) NOT NULL           COMMENT 'JWT refresh token string',
  `expires_at` DATETIME     NOT NULL           COMMENT 'Thời điểm hết hạn',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_refresh_token`
    (`token`(255))
    COMMENT 'Lookup nhanh khi client gửi refresh token — prefix 255 vì VARCHAR(512)',
  KEY `idx_refresh_tokens_user`
    (`user_id`)
    COMMENT 'Revoke all sessions: DELETE WHERE user_id = ?',
  KEY `idx_refresh_tokens_expires`
    (`expires_at`)
    COMMENT 'Cleanup scheduler: DELETE WHERE expires_at < NOW()',
  CONSTRAINT `fk_rt_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Refresh token hợp lệ — hỗ trợ token rotation và multi-device logout';


-- -------------------------------------------------------------
-- 5.2  revoked_access_tokens
--
--  Mục đích: Blacklist access token sau khi logout (trước khi hết hạn).
--  Chỉ lưu JTI (JWT ID) + expires_at — không lưu full token.
--
--  INDEX STRATEGY:
--    PK jti                    → Lookup O(1) khi validate access token
--    idx_revoked_expires       → Cleanup job: DELETE WHERE expires_at < NOW()
--
--  GHI CHÚ:
--  - Bảng này chỉ cần thiết nếu dùng stateless JWT + logout before expiry
--  - Cleanup scheduler chạy mỗi giờ để xóa JTI đã hết hạn (không cần giữ)
--  - Kích thước bảng tự giới hạn bởi TTL của access token (mặc định 1h)
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `revoked_access_tokens` (
  `jti`        VARCHAR(36) NOT NULL           COMMENT 'JWT ID claim (UUID format)',
  `expires_at` DATETIME    NOT NULL           COMMENT 'Thời điểm access token hết hạn — sau đó xóa khỏi bảng',
  PRIMARY KEY (`jti`),
  KEY `idx_revoked_access_tokens_expires`
    (`expires_at`)
    COMMENT 'Cleanup scheduler: DELETE WHERE expires_at < NOW()'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Blacklist JTI của access token bị revoke trước hạn (logout)';


SET FOREIGN_KEY_CHECKS = 1;
