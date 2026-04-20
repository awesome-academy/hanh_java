package com.psms.service;

import com.psms.dto.response.ImportResult;
import com.psms.entity.*;
import com.psms.enums.ActionType;
import com.psms.enums.Gender;
import com.psms.enums.RoleName;
import com.psms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.psms.enums.ImportType;

/**
 * Service import dữ liệu từ file CSV.
 *
 * <p>Design decisions:
 * <ul>
 *   <li><b>Partial failure</b>: không fail toàn bộ nếu 1 row lỗi — xử lý từng row độc lập.
 *       Response trả về: {@code { total, success, failed, errors:[{row, field, message}] }}</li>
 *   <li><b>Row savepoint</b>: mỗi row được save trong try-catch riêng. Nếu lỗi → add vào errors,
 *       tiếp tục row tiếp theo (không rollback toàn bộ transaction).</li>
 *   <li><b>Validation order</b>: required fields → format → uniqueness → FK exists.
 *       Dừng lại ở lỗi đầu tiên của mỗi row (không stack nhiều lỗi trên 1 row).</li>
 *   <li>Max 1000 rows.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
public class CsvImportService {

    private static final int MAX_ROWS = 1000;

    private final CitizenRepository citizenRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final DepartmentRepository departmentRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

      /**
       * Import dữ liệu từ CSV theo loại ImportType.
       * @param type loại import (CITIZENS, SERVICES, DEPARTMENTS, STAFF)
       * @param file file CSV upload
       * @return ImportResult
       */
      public ImportResult importByType(ImportType type, MultipartFile file) throws IOException {
          return switch (type) {
              case CITIZENS -> importCitizens(file);
              case SERVICES -> importServices(file);
              case DEPARTMENTS -> importDepartments(file);
              case STAFF -> importStaff(file);
          };
      }
    // ── Import Citizens ────────────────────────────────────────────────────────

    /**
     * Import công dân từ CSV.
     *
     * <p>CSV columns (header required):
     * {@code hoTen, email, matKhau, soCCCD, ngaySinh (yyyy-MM-dd), gioiTinh (MALE/FEMALE/OTHER), diaChiThuongTru}
     *
     * <p>Validation: email unique, soCCCD unique, required fields non-empty.
     * Mỗi row tạo 1 User + 1 Citizen, gán role CITIZEN.
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.IMPORT,
        entityType = "citizens",
        description = "'Import công dân từ CSV — kết quả: ' + #result.success + '/' + #result.total + ' thành công'"
    )
    @Transactional
    public ImportResult importCitizens(MultipartFile file) throws IOException {
        List<CSVRecord> records = parseCsv(file);
        List<ImportResult.RowError> errors = new ArrayList<>();
        int success = 0;

        Role citizenRole = roleRepository.findByName(RoleName.CITIZEN)
                .orElseThrow(() -> new IllegalStateException("Role CITIZEN không tồn tại"));

        for (int i = 0; i < records.size(); i++) {
            int rowNum = i + 1;
            CSVRecord rec = records.get(i);
            try {
                String fullName = get(rec, "hoTen");
                String email    = get(rec, "email");
                String password = get(rec, "matKhau");
                String cccd     = get(rec, "soCCCD");

                if (fullName.isBlank()) { errors.add(rowErr(rowNum, "hoTen", "Họ tên không được trống")); continue; }
                if (email.isBlank())    { errors.add(rowErr(rowNum, "email", "Email không được trống")); continue; }
                if (cccd.isBlank())     { errors.add(rowErr(rowNum, "soCCCD", "Số CCCD không được trống")); continue; }
                if (password.isBlank()) { errors.add(rowErr(rowNum, "matKhau", "Mật khẩu không được trống")); continue; }

                if (userRepository.existsByEmail(email)) {
                    errors.add(rowErr(rowNum, "email", "Email đã tồn tại: " + email)); continue;
                }
                if (citizenRepository.existsByNationalId(cccd)) {
                    errors.add(rowErr(rowNum, "soCCCD", "Số CCCD đã tồn tại: " + cccd)); continue;
                }

                User user = new User();
                user.setEmail(email.trim());
                user.setFullName(fullName.trim());
                user.setPassword(passwordEncoder.encode(password));
                user.setPhone(getOpt(rec, "soDienThoai"));
                user.setRoles(Set.of(citizenRole));
                userRepository.save(user);

                Citizen citizen = new Citizen();
                citizen.setUser(user);
                citizen.setNationalId(cccd.trim());
                citizen.setDateOfBirth(parseDate(getOpt(rec, "ngaySinh")));
                citizen.setGender(parseGender(getOpt(rec, "gioiTinh")));
                citizen.setPermanentAddress(getOpt(rec, "diaChiThuongTru"));
                citizenRepository.save(citizen);

                success++;
            } catch (Exception ex) {
                errors.add(rowErr(rowNum, "general", "Lỗi xử lý row: " + ex.getMessage()));
            }
        }

        log.info("Import citizens: total={}, success={}, failed={}", records.size(), success, errors.size());
        return ImportResult.builder()
                .total(records.size()).success(success).failed(errors.size()).errors(errors).build();
    }

    // ── Import Services ────────────────────────────────────────────────────────

    /**
     * Import dịch vụ công từ CSV.
     *
     * <p>CSV columns: {@code maDV, tenDV, maLinhVuc, maPhongBan, thoiHanXL (số nguyên), lePhi, moTa}
     *
     * <p>Validation: maDV unique, maLinhVuc phải tồn tại, maPhongBan phải tồn tại.
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.IMPORT,
        entityType = "service_types",
        description = "'Import dịch vụ từ CSV — kết quả: ' + #result.success + '/' + #result.total + ' thành công'"
    )
    @Transactional
    public ImportResult importServices(MultipartFile file) throws IOException {
        List<CSVRecord> records = parseCsv(file);
        List<ImportResult.RowError> errors = new ArrayList<>();
        int success = 0;

        for (int i = 0; i < records.size(); i++) {
            int rowNum = i + 1;
            CSVRecord rec = records.get(i);
            try {
                String code      = get(rec, "maDV");
                String name      = get(rec, "tenDV");
                String catCode   = get(rec, "maLinhVuc");
                String deptCode  = get(rec, "maPhongBan");
                String daysStr   = get(rec, "thoiHanXL");

                if (code.isBlank())     { errors.add(rowErr(rowNum, "maDV", "Mã dịch vụ không được trống")); continue; }
                if (name.isBlank())     { errors.add(rowErr(rowNum, "tenDV", "Tên dịch vụ không được trống")); continue; }
                if (catCode.isBlank())  { errors.add(rowErr(rowNum, "maLinhVuc", "Mã lĩnh vực không được trống")); continue; }
                if (deptCode.isBlank()) { errors.add(rowErr(rowNum, "maPhongBan", "Mã phòng ban không được trống")); continue; }
                if (daysStr.isBlank())  { errors.add(rowErr(rowNum, "thoiHanXL", "Thời hạn xử lý không được trống")); continue; }

                if (serviceTypeRepository.existsByCode(code.toUpperCase())) {
                    errors.add(rowErr(rowNum, "maDV", "Mã dịch vụ đã tồn tại: " + code)); continue;
                }

                var category = categoryRepository.findByCode(catCode.toUpperCase())
                        .orElse(null);
                if (category == null) {
                    errors.add(rowErr(rowNum, "maLinhVuc", "Lĩnh vực không tồn tại: " + catCode)); continue;
                }

                var department = departmentRepository.findByCode(deptCode.toUpperCase())
                        .orElse(null);
                if (department == null) {
                    errors.add(rowErr(rowNum, "maPhongBan", "Phòng ban không tồn tại: " + deptCode)); continue;
                }

                int days;
                try { days = Integer.parseInt(daysStr.trim()); }
                catch (NumberFormatException ex) {
                    errors.add(rowErr(rowNum, "thoiHanXL", "Thời hạn xử lý phải là số nguyên")); continue;
                }
                if (days <= 0) {
                    errors.add(rowErr(rowNum, "thoiHanXL", "Thời hạn xử lý phải lớn hơn 0")); continue;
                }

                ServiceType svc = new ServiceType();
                svc.setCode(code.trim().toUpperCase());
                svc.setName(name.trim());
                svc.setCategory(category);
                svc.setDepartment(department);
                svc.setProcessingTimeDays((short) days);
                svc.setDescription(getOpt(rec, "moTa"));
                svc.setRequirements(getOpt(rec, "yeuCau"));
                String feeStr = getOpt(rec, "lePhi");
                svc.setFee(feeStr != null && !feeStr.isBlank()
                        ? new BigDecimal(feeStr.trim()) : BigDecimal.ZERO);
                svc.setActive(true);
                serviceTypeRepository.save(svc);

                success++;
            } catch (Exception ex) {
                errors.add(rowErr(rowNum, "general", "Lỗi xử lý row: " + ex.getMessage()));
            }
        }

        log.info("Import services: total={}, success={}, failed={}", records.size(), success, errors.size());
        return ImportResult.builder()
                .total(records.size()).success(success).failed(errors.size()).errors(errors).build();
    }

    // ── Import Departments ─────────────────────────────────────────────────────

    /**
     * Import phòng ban từ CSV.
     *
     * <p>CSV columns: {@code maPB, tenPB, diaChi, soDienThoai, email}
     *
     * <p>Validation: maPB unique.
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.IMPORT,
        entityType = "departments",
        description = "'Import phòng ban từ CSV — kết quả: ' + #result.success + '/' + #result.total + ' thành công'"
    )
    @Transactional
    public ImportResult importDepartments(MultipartFile file) throws IOException {
        List<CSVRecord> records = parseCsv(file);
        List<ImportResult.RowError> errors = new ArrayList<>();
        int success = 0;

        for (int i = 0; i < records.size(); i++) {
            int rowNum = i + 1;
            CSVRecord rec = records.get(i);
            try {
                String code = get(rec, "maPB");
                String name = get(rec, "tenPB");

                if (code.isBlank()) { errors.add(rowErr(rowNum, "maPB", "Mã phòng ban không được trống")); continue; }
                if (name.isBlank()) { errors.add(rowErr(rowNum, "tenPB", "Tên phòng ban không được trống")); continue; }

                if (departmentRepository.existsByCode(code.toUpperCase())) {
                    errors.add(rowErr(rowNum, "maPB", "Mã phòng ban đã tồn tại: " + code)); continue;
                }

                Department dept = new Department();
                dept.setCode(code.trim().toUpperCase());
                dept.setName(name.trim());
                dept.setAddress(getOpt(rec, "diaChi"));
                dept.setPhone(getOpt(rec, "soDienThoai"));
                dept.setEmail(getOpt(rec, "email"));
                dept.setActive(true);
                departmentRepository.save(dept);

                success++;
            } catch (Exception ex) {
                errors.add(rowErr(rowNum, "general", "Lỗi xử lý row: " + ex.getMessage()));
            }
        }

        log.info("Import departments: total={}, success={}, failed={}", records.size(), success, errors.size());
        return ImportResult.builder()
                .total(records.size()).success(success).failed(errors.size()).errors(errors).build();
    }

    // ── Import Staff ───────────────────────────────────────────────────────────

    /**
     * Import cán bộ từ CSV.
     *
     * <p>CSV columns: {@code email, maCB, maPhongBan, chucVu}
     *
     * <p>Validation: user với email phải tồn tại và là STAFF/MANAGER,
     * department với mã phải tồn tại, maCB phải unique.
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.IMPORT,
        entityType = "staff",
        description = "'Import cán bộ từ CSV — kết quả: ' + #result.success + '/' + #result.total + ' thành công'"
    )
    @Transactional
    public ImportResult importStaff(MultipartFile file) throws IOException {
        List<CSVRecord> records = parseCsv(file);
        List<ImportResult.RowError> errors = new ArrayList<>();
        int success = 0;

        for (int i = 0; i < records.size(); i++) {
            int rowNum = i + 1;
            CSVRecord rec = records.get(i);
            try {
                String email    = get(rec, "email");
                String staffCode = get(rec, "maCB");
                String deptCode = get(rec, "maPhongBan");

                if (email.isBlank())     { errors.add(rowErr(rowNum, "email", "Email không được trống")); continue; }
                if (staffCode.isBlank()) { errors.add(rowErr(rowNum, "maCB", "Mã cán bộ không được trống")); continue; }
                if (deptCode.isBlank())  { errors.add(rowErr(rowNum, "maPhongBan", "Mã phòng ban không được trống")); continue; }

                var user = userRepository.findWithRolesByEmail(email).orElse(null);
                if (user == null) {
                    errors.add(rowErr(rowNum, "email", "Không tìm thấy user với email: " + email)); continue;
                }

                boolean hasStaffRole = user.getRoles().stream()
                        .anyMatch(r -> r.getName() == RoleName.STAFF || r.getName() == RoleName.MANAGER);
                if (!hasStaffRole) {
                    errors.add(rowErr(rowNum, "email", "User không có role STAFF/MANAGER: " + email)); continue;
                }

                if (staffRepository.existsByStaffCode(staffCode)) {
                    errors.add(rowErr(rowNum, "maCB", "Mã cán bộ đã tồn tại: " + staffCode)); continue;
                }

                if (staffRepository.findByUserId(user.getId()).isPresent()) {
                    errors.add(rowErr(rowNum, "email", "User đã có hồ sơ cán bộ: " + email)); continue;
                }

                var department = departmentRepository.findByCode(deptCode.toUpperCase()).orElse(null);
                if (department == null) {
                    errors.add(rowErr(rowNum, "maPhongBan", "Phòng ban không tồn tại: " + deptCode)); continue;
                }

                Staff staff = new Staff();
                staff.setUser(user);
                staff.setStaffCode(staffCode.trim());
                staff.setDepartment(department);
                staff.setPosition(getOpt(rec, "chucVu"));
                staff.setAvailable(true);
                staffRepository.save(staff);

                success++;
            } catch (Exception ex) {
                errors.add(rowErr(rowNum, "general", "Lỗi xử lý row: " + ex.getMessage()));
            }
        }

        log.info("Import staff: total={}, success={}, failed={}", records.size(), success, errors.size());
        return ImportResult.builder()
                .total(records.size()).success(success).failed(errors.size()).errors(errors).build();
    }

    // ── CSV Template (headers only) ────────────────────────────────────────────

    /**
     * Tạo file CSV mẫu (chỉ có header row) để người dùng download và điền dữ liệu.
     */

    public byte[] getTemplate(ImportType importType) throws IOException {
        String[] headers = switch (importType) {
            case CITIZENS -> new String[]{"hoTen", "email", "matKhau", "soCCCD", "ngaySinh",
                                         "gioiTinh", "diaChiThuongTru", "soDienThoai"};
            case SERVICES -> new String[]{"maDV", "tenDV", "maLinhVuc", "maPhongBan",
                                         "thoiHanXL", "lePhi", "moTa", "yeuCau"};
            case DEPARTMENTS -> new String[]{"maPB", "tenPB", "diaChi", "soDienThoai", "email"};
            case STAFF -> new String[]{"email", "maCB", "maPhongBan", "chucVu"};
        };

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw,
                CSVFormat.DEFAULT.builder().setHeader(headers).build())) {
            // Chỉ in header, không có data rows
        }

        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = sw.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(content, 0, result, bom.length, content.length);
        return result;
    }

    /**
     * Legacy: for backward compatibility, still accept String type
     */
    public byte[] getTemplate(String type) throws IOException {
        ImportType importType = ImportType.fromCode(type);
        return getTemplate(importType);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Parse CSV file — bỏ qua BOM nếu có, accept header row, trim whitespace.
     * Giới hạn MAX_ROWS = 1000 rows.
     */
    private List<CSVRecord> parseCsv(MultipartFile file) throws IOException {
        var format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build();

        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             var parser = new CSVParser(reader, format)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.size() > MAX_ROWS) {
                throw new IllegalArgumentException(
                        "File CSV vượt quá giới hạn " + MAX_ROWS + " rows. File của bạn có "
                        + records.size() + " rows.");
            }
            return records;
        }
    }

    /** Lấy giá trị field từ CSV record, trả về empty string nếu không có */
    private String get(CSVRecord rec, String field) {
        try { return rec.get(field) != null ? rec.get(field).trim() : ""; }
        catch (IllegalArgumentException ex) { return ""; }
    }

    /** Lấy giá trị optional field — trả về null nếu trống */
    private String getOpt(CSVRecord rec, String field) {
        String val = get(rec, field);
        return val.isBlank() ? null : val;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); }
        catch (DateTimeParseException ex) { return null; }
    }

    private Gender parseGender(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Gender.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private ImportResult.RowError rowErr(int row, String field, String message) {
        return ImportResult.RowError.builder().row(row).field(field).message(message).build();
    }
}

