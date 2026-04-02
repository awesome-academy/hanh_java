-- =============================================================
--  PUBLIC SERVICE MANAGEMENT SYSTEM — Seed Data
--  Target : MySQL 8.0+ / MySQL Workbench
--  Charset: utf8mb4 / COLLATE utf8mb4_unicode_ci
--  Author : PSMS Dev Team
--  Version: 1.1.0
-- =============================================================
--
--  MỤC ĐÍCH: Dữ liệu mẫu cho môi trường DEV / TEST
--  ⚠️  KHÔNG chạy trên PRODUCTION
--
--  PHỤ THUỘC: Chạy psms_schema.sql TRƯỚC file này
--
--  THỨ TỰ CHẠY:
--    1. psms_schema.sql  → tạo database + tables + indexes
--    2. psms_seed.sql        → insert dữ liệu mẫu (file này)
--
--  ĐỂ RESET VÀ CHẠY LẠI:
--    Chạy lại psms_schema.sql (có DROP TABLE IF EXISTS)
--    rồi chạy lại file này.
-- =============================================================

USE `psms`;

SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------
-- 1. Departments (phải insert trước vì users.leader_id FK)
-- -------------------------------------------------------------
INSERT INTO `departments` (`code`, `name`, `address`, `phone`, `email`) VALUES
  ('PB-001', 'UBND Phường Hoàn Kiếm',      'Q.Hoàn Kiếm, TP.Hà Nội',  '02439421234', 'hoankiem@hanoi.gov.vn'),
  ('PB-002', 'Sở Xây dựng TP.Hà Nội',      'Q.Ba Đình, TP.Hà Nội',     '02438295678', 'sxd@hanoi.gov.vn'),
  ('PB-003', 'Sở Giáo dục & Đào tạo',      'Q.Đống Đa, TP.Hà Nội',     '02438391234', 'sgd@hanoi.gov.vn'),
  ('PB-004', 'Sở Tài nguyên & Môi trường', 'Q.Cầu Giấy, TP.Hà Nội',   '02438481234', 'stnmt@hanoi.gov.vn'),
  ('PB-005', 'Sở Y tế TP.Hà Nội',          'Q.Hai Bà Trưng, TP.Hà Nội','02438651234', 'syt@hanoi.gov.vn');


-- -------------------------------------------------------------
-- 2. Users (password = "Admin@123" BCrypt hash — chỉ để test)
-- -------------------------------------------------------------
INSERT INTO `users` (`email`, `password`, `full_name`, `phone`) VALUES
  ('superadmin@dvcqg.gov.vn', '$2a$12$K9Z2XVpAnIPV5uyGRCqkMO.B2ZB9f9YdoWX3uv5IJ6fVxbxCmn5Qi', 'Super Admin',    '0900000001'),
  ('tmkhoa@dvc.gov.vn',       '$2a$12$K9Z2XVpAnIPV5uyGRCqkMO.B2ZB9f9YdoWX3uv5IJ6fVxbxCmn5Qi', 'Trần Minh Khoa', '0900000002'),
  ('nhduc@dvc.gov.vn',        '$2a$12$K9Z2XVpAnIPV5uyGRCqkMO.B2ZB9f9YdoWX3uv5IJ6fVxbxCmn5Qi', 'Nguyễn Hữu Đức', '0900000003'),
  ('nguyen.thi.an@email.com', '$2a$12$K9Z2XVpAnIPV5uyGRCqkMO.B2ZB9f9YdoWX3uv5IJ6fVxbxCmn5Qi', 'Nguyễn Thị An',  '0901234567'),
  ('le.van.minh@email.com',   '$2a$12$K9Z2XVpAnIPV5uyGRCqkMO.B2ZB9f9YdoWX3uv5IJ6fVxbxCmn5Qi', 'Lê Văn Minh',    '0912345678');


-- -------------------------------------------------------------
-- 3. User Roles
-- -------------------------------------------------------------
INSERT INTO `user_roles` (`user_id`, `role_id`) VALUES
  (1, 4), -- superadmin@dvcqg.gov.vn  → SUPER_ADMIN
  (2, 2), -- tmkhoa@dvc.gov.vn        → STAFF
  (3, 3), -- nhduc@dvc.gov.vn         → MANAGER
  (4, 1), -- nguyen.thi.an@email.com  → CITIZEN
  (5, 1); -- le.van.minh@email.com    → CITIZEN


-- -------------------------------------------------------------
-- 4. Citizens (công dân mẫu)
-- -------------------------------------------------------------
INSERT INTO `citizens` (`user_id`, `national_id`, `date_of_birth`, `gender`,
                         `permanent_address`, `ward`, `province`) VALUES
  (4, '079090012345', '1990-06-15', 'FEMALE',
   '123 Đinh Tiên Hoàng', 'Phường Hoàn Kiếm', 'TP.Hà Nội'),
  (5, '001090023456', '1988-03-22', 'MALE',
   '45 Nguyễn Trãi',      'Phường Thượng Đình', 'TP.Hà Nội');


-- -------------------------------------------------------------
-- 5. Staff (cán bộ mẫu)
-- -------------------------------------------------------------
INSERT INTO `staff` (`user_id`, `staff_code`, `department_id`, `position`) VALUES
  (2, 'CB-001', 1, 'Chuyên viên'),   -- tmkhoa → UBND Phường Hoàn Kiếm
  (3, 'CB-002', 2, 'Trưởng phòng');  -- nhduc  → Sở Xây dựng TP.Hà Nội


-- -------------------------------------------------------------
-- 6. Service Types (roles + service_categories đã có từ DDL)
-- -------------------------------------------------------------
INSERT INTO `service_types`
  (`code`, `name`, `category_id`, `department_id`,
   `processing_time_days`, `fee`, `requirements`, `created_by`) VALUES
  ('DV-001', 'Đăng ký khai sinh',
   1, 1,  5,       0,
   'CCCD/CMND bố mẹ; Giấy chứng sinh; Mẫu khai sinh DC-01', 1),

  ('DV-002', 'Đăng ký kết hôn',
   1, 1,  7,       0,
   'CCCD/CMND 2 bên; Giấy xác nhận tình trạng hôn nhân; Mẫu TP/HN-2014', 1),

  ('DV-003', 'Cấp giấy phép xây dựng',
   4, 2, 20, 200000,
   'Hồ sơ thiết kế; Giấy chứng nhận QSDĐ; Mẫu đơn xin cấp phép', 1),

  ('DV-004', 'Xác nhận học bổng sinh viên',
   2, 3,  7,       0,
   'Đơn xin xác nhận; Bảng điểm học kỳ gần nhất; CCCD', 1),

  ('DV-005', 'Cấp GCNQSDĐ (sổ đỏ)',
   5, 4, 45,       0,
   'Đơn đề nghị; Bản đồ thửa đất; Giấy tờ nguồn gốc đất; CCCD', 1),

  ('DV-006', 'Cấp phép hành nghề y',
   3, 5, 30, 100000,
   'Đơn xin cấp phép; Bằng cấp chuyên môn; Lý lịch tư pháp; Ảnh 4x6', 1);


SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- VERIFY — Kiểm tra sau khi seed
-- =============================================================
/*
SELECT 'departments'    AS tbl, COUNT(*) AS rows FROM departments    UNION ALL
SELECT 'users'          AS tbl, COUNT(*) AS rows FROM users          UNION ALL
SELECT 'user_roles'     AS tbl, COUNT(*) AS rows FROM user_roles     UNION ALL
SELECT 'citizens'       AS tbl, COUNT(*) AS rows FROM citizens       UNION ALL
SELECT 'staff'          AS tbl, COUNT(*) AS rows FROM staff          UNION ALL
SELECT 'service_types'  AS tbl, COUNT(*) AS rows FROM service_types;
-- Kết quả mong đợi: 5 / 5 / 5 / 2 / 2 / 6
*/
