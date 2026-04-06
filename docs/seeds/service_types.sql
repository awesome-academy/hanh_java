-- =============================================================
--  SERVICE TYPES — Sample Data Only
--  Target : MySQL 8.0+ / MySQL Workbench
--  Charset: utf8mb4 / COLLATE utf8mb4_unicode_ci
--  Author : PSMS Dev Team
--  Version: 1.0.0
-- =============================================================
--  Chỉ chứa dữ liệu mẫu cho bảng service_types
--  Đảm bảo đã chạy psms_schema.sql trước khi import file này
-- =============================================================

USE `psms`;

INSERT INTO `service_types`
  (`code`, `name`, `category_id`, `department_id`,
   `processing_time_days`, `fee`, `requirements`, `created_by`) VALUES
  ('DV-007', 'Cấp giấy phép kinh doanh', 4, 2, 10, 500000, 'Đơn đề nghị; CMND/CCCD; Hợp đồng thuê mặt bằng', 2),
  ('DV-008', 'Đăng ký tạm trú', 1, 1, 3, 0, 'Đơn đăng ký; CMND/CCCD; Giấy xác nhận chủ nhà', 3),
  ('DV-009', 'Cấp giấy phép quảng cáo', 4, 2, 15, 200000, 'Đơn đề nghị; Hợp đồng quảng cáo; Bản vẽ thiết kế', 2),
  ('DV-010', 'Cấp giấy phép lao động cho người nước ngoài', 5, 4, 20, 1000000, 'Đơn đề nghị; Hộ chiếu; Hợp đồng lao động', 4),
  ('DV-011', 'Cấp giấy phép vận chuyển chất thải', 4, 2, 12, 300000, 'Đơn đề nghị; Hợp đồng xử lý chất thải; Giấy phép lái xe', 2),
  ('DV-012', 'Cấp giấy phép xây dựng nhà ở riêng lẻ', 4, 2, 15, 150000, 'Đơn đề nghị; Bản vẽ thiết kế; Giấy chứng nhận QSDĐ', 2),
  ('DV-013', 'Cấp giấy phép sửa chữa nhà', 4, 2, 7, 50000, 'Đơn đề nghị; Bản vẽ sửa chữa; Giấy chứng nhận QSDĐ', 2),
  ('DV-014', 'Cấp giấy phép tổ chức sự kiện', 2, 3, 5, 100000, 'Đơn đề nghị; Kế hoạch tổ chức; Hợp đồng địa điểm', 3),
  ('DV-015', 'Cấp giấy phép hoạt động giáo dục', 2, 3, 20, 0, 'Đơn đề nghị; Đề án hoạt động; Danh sách giáo viên', 3),
  ('DV-016', 'Cấp giấy phép hành nghề dược', 3, 5, 25, 120000, 'Đơn đề nghị; Bằng cấp chuyên môn; Lý lịch tư pháp', 5),
  ('DV-017', 'Cấp giấy phép kinh doanh vận tải', 4, 2, 10, 250000, 'Đơn đề nghị; Hợp đồng vận tải; Giấy phép lái xe', 2),
  ('DV-018', 'Cấp giấy phép khai thác nước ngầm', 4, 2, 30, 400000, 'Đơn đề nghị; Bản đồ khu vực khai thác; Giấy chứng nhận QSDĐ', 2),
  ('DV-019', 'Cấp giấy phép sử dụng vật liệu nổ', 4, 2, 20, 600000, 'Đơn đề nghị; Hợp đồng cung cấp vật liệu; Giấy phép an toàn', 2),
  ('DV-020', 'Cấp giấy phép nhập khẩu thiết bị y tế', 3, 5, 15, 200000, 'Đơn đề nghị; Hợp đồng nhập khẩu; Giấy chứng nhận chất lượng', 5),
  ('DV-021', 'Cấp giấy phép hoạt động phòng khám', 3, 5, 18, 180000, 'Đơn đề nghị; Bằng cấp chuyên môn; Hợp đồng thuê địa điểm', 5),
  ('DV-022', 'Cấp giấy phép hoạt động trung tâm ngoại ngữ', 2, 3, 12, 0, 'Đơn đề nghị; Đề án hoạt động; Danh sách giáo viên', 3),
  ('DV-023', 'Cấp giấy phép hoạt động tư vấn du học', 2, 3, 10, 0, 'Đơn đề nghị; Đề án hoạt động; Danh sách nhân sự', 3),
  ('DV-024', 'Cấp giấy phép hoạt động dịch vụ việc làm', 2, 3, 14, 0, 'Đơn đề nghị; Đề án hoạt động; Danh sách nhân sự', 3),
  ('DV-025', 'Cấp giấy phép hoạt động dịch vụ bảo vệ', 4, 2, 10, 300000, 'Đơn đề nghị; Hợp đồng dịch vụ; Danh sách nhân sự', 2),
  ('DV-026', 'Cấp giấy phép hoạt động dịch vụ vệ sinh công nghiệp', 4, 2, 8, 100000, 'Đơn đề nghị; Hợp đồng dịch vụ; Danh sách nhân sự', 2),
  ('DV-027', 'Cấp giấy phép hoạt động dịch vụ vận tải hành khách', 4, 2, 12, 200000, 'Đơn đề nghị; Hợp đồng vận tải; Danh sách phương tiện', 2),
  ('DV-028', 'Cấp giấy phép hoạt động dịch vụ vận tải hàng hóa', 4, 2, 12, 200000, 'Đơn đề nghị; Hợp đồng vận tải; Danh sách phương tiện', 2),
  ('DV-029', 'Cấp giấy phép hoạt động dịch vụ chuyển phát nhanh', 4, 2, 10, 150000, 'Đơn đề nghị; Hợp đồng dịch vụ; Danh sách phương tiện', 2),
  ('DV-030', 'Cấp giấy phép hoạt động dịch vụ logistics', 4, 2, 15, 250000, 'Đơn đề nghị; Hợp đồng dịch vụ; Danh sách phương tiện', 2);

