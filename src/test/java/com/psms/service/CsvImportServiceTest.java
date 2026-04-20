package com.psms.service;

import com.psms.dto.response.ImportResult;
import com.psms.entity.*;
import com.psms.enums.RoleName;
import com.psms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit test cho CsvImportService.
 *
 * <p>Covers task #14-08:
 * - Import 100 rows, 3 rows lỗi → { total:100, success:97, failed:3, errors:[...] }
 * - Validation: required fields, uniqueness, FK existence
 * - Partial failure: lỗi 1 row không dừng toàn bộ import
 */
@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock CitizenRepository citizenRepository;
    @Mock ServiceTypeRepository serviceTypeRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock StaffRepository staffRepository;
    @Mock UserRepository userRepository;
    @Mock ServiceCategoryRepository categoryRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks CsvImportService importService;

    private Role citizenRole;
    private Role staffRole;

    @BeforeEach
    void setUp() {
        citizenRole = new Role();
        citizenRole.setName(RoleName.CITIZEN);

        staffRole = new Role();
        staffRole.setName(RoleName.STAFF);
    }

    // ── Import Citizens ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("importCitizens()")
    class ImportCitizens {

        @Test
        @DisplayName("3 rows hợp lệ → success=3, failed=0")
        void validRows_allSuccess() throws IOException {
            String csv = "hoTen,email,matKhau,soCCCD,ngaySinh,gioiTinh,diaChiThuongTru\n" +
                         "Nguyen Van A,a@test.com,Pass@123,001234567890,1990-01-15,MALE,Ha Noi\n" +
                         "Tran Thi B,b@test.com,Pass@123,001234567891,1992-05-20,FEMALE,HCM\n" +
                         "Le Van C,c@test.com,Pass@123,001234567892,1988-03-10,OTHER,Da Nang\n";

            given(roleRepository.findByName(RoleName.CITIZEN)).willReturn(Optional.of(citizenRole));
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(citizenRepository.existsByNationalId(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("hashed");
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            ImportResult result = importService.importCitizens(toFile(csv, "citizens.csv"));

            assertThat(result.getTotal()).isEqualTo(3);
            assertThat(result.getSuccess()).isEqualTo(3);
            assertThat(result.getFailed()).isEqualTo(0);
            assertThat(result.getErrors()).isEmpty();
            verify(citizenRepository, times(3)).save(any(Citizen.class));
        }

        @Test
        @DisplayName("Email trùng → row bị skip, không dừng import")
        void duplicateEmail_partialFailure() throws IOException {
            String csv = "hoTen,email,matKhau,soCCCD\n" +
                         "Nguyen Van A,dup@test.com,Pass@123,001234567890\n" +
                         "Tran Thi B,ok@test.com,Pass@123,001234567891\n";

            given(roleRepository.findByName(RoleName.CITIZEN)).willReturn(Optional.of(citizenRole));
            // Row 1: email đã tồn tại
            given(userRepository.existsByEmail("dup@test.com")).willReturn(true);
            // Row 2: OK
            given(userRepository.existsByEmail("ok@test.com")).willReturn(false);
            given(citizenRepository.existsByNationalId("001234567891")).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("hashed");
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            ImportResult result = importService.importCitizens(toFile(csv, "citizens.csv"));

            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getSuccess()).isEqualTo(1);
            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getRow()).isEqualTo(1);
            assertThat(result.getErrors().get(0).getField()).isEqualTo("email");
        }

        @Test
        @DisplayName("CCCD trùng → row bị skip với lỗi rõ ràng")
        void duplicateNationalId_skipRow() throws IOException {
            String csv = "hoTen,email,matKhau,soCCCD\n" +
                         "Nguyen Van A,unique@test.com,Pass@123,DUPLICATE_ID\n";

            given(roleRepository.findByName(RoleName.CITIZEN)).willReturn(Optional.of(citizenRole));
            given(userRepository.existsByEmail("unique@test.com")).willReturn(false);
            given(citizenRepository.existsByNationalId("DUPLICATE_ID")).willReturn(true);

            ImportResult result = importService.importCitizens(toFile(csv, "citizens.csv"));

            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getErrors().get(0).getField()).isEqualTo("soCCCD");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("hoTen trống → row bị skip với lỗi field rõ ràng")
        void emptyRequiredField_skipRow() throws IOException {
            String csv = "hoTen,email,matKhau,soCCCD\n" +
                         ",empty@test.com,Pass@123,001234567890\n";

            given(roleRepository.findByName(RoleName.CITIZEN)).willReturn(Optional.of(citizenRole));

            ImportResult result = importService.importCitizens(toFile(csv, "citizens.csv"));

            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getErrors().get(0).getField()).isEqualTo("hoTen");
        }
    }

    // ── Import partial failure 100 rows ───────────────────────────────────────

    @Nested
    @DisplayName("Partial failure — 100 rows, 3 errors")
    class PartialFailure {

        @Test
        @DisplayName("Import 100 rows, 3 rows email trùng → total=100, success=97, failed=3")
        void hundredRows_threeErrors() throws IOException {
            given(roleRepository.findByName(RoleName.CITIZEN)).willReturn(Optional.of(citizenRole));
            given(passwordEncoder.encode(anyString())).willReturn("hashed");
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // Default: email và CCCD đều chưa tồn tại
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(citizenRepository.existsByNationalId(anyString())).willReturn(false);
            // 3 rows lỗi: row 1, 50, 100 — email trùng
            given(userRepository.existsByEmail("dup1@test.com")).willReturn(true);
            given(userRepository.existsByEmail("dup50@test.com")).willReturn(true);
            given(userRepository.existsByEmail("dup100@test.com")).willReturn(true);

            StringBuilder sb = new StringBuilder("hoTen,email,matKhau,soCCCD\n");
            for (int i = 1; i <= 100; i++) {
                boolean isDup = (i == 1 || i == 50 || i == 100);
                String email = isDup ? "dup" + i + "@test.com" : "ok" + i + "@test.com";
                sb.append("User ").append(i).append(",").append(email)
                  .append(",Pass@123,").append(String.format("%012d", i)).append("\n");
            }

            ImportResult result = importService.importCitizens(toFile(sb.toString(), "bulk.csv"));

            assertThat(result.getTotal()).isEqualTo(100);
            assertThat(result.getSuccess()).isEqualTo(97);
            assertThat(result.getFailed()).isEqualTo(3);
            assertThat(result.getErrors()).hasSize(3);
        }
    }

    // ── Import Departments ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("importDepartments()")
    class ImportDepartments {

        @Test
        @DisplayName("2 rows hợp lệ → success=2")
        void validRows_allSuccess() throws IOException {
            String csv = "maPB,tenPB,diaChi,soDienThoai,email\n" +
                         "PB01,Phong Ban A,Ha Noi,0123456789,a@test.com\n" +
                         "PB02,Phong Ban B,HCM,0987654321,b@test.com\n";

            given(departmentRepository.existsByCode("PB01")).willReturn(false);
            given(departmentRepository.existsByCode("PB02")).willReturn(false);

            ImportResult result = importService.importDepartments(toFile(csv, "dept.csv"));

            assertThat(result.getSuccess()).isEqualTo(2);
            assertThat(result.getFailed()).isEqualTo(0);
            verify(departmentRepository, times(2)).save(any(Department.class));
        }

        @Test
        @DisplayName("Mã PB trùng → row bị skip")
        void duplicateCode_skipRow() throws IOException {
            String csv = "maPB,tenPB\n" +
                         "DUP,Phong Ban Trung\n";
            given(departmentRepository.existsByCode("DUP")).willReturn(true);

            ImportResult result = importService.importDepartments(toFile(csv, "dept.csv"));

            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getErrors().get(0).getField()).isEqualTo("maPB");
            verify(departmentRepository, never()).save(any());
        }
    }

    // ── Import Services ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("importServices()")
    class ImportServices {

        @Test
        @DisplayName("Lĩnh vực không tồn tại → row bị skip với lỗi maLinhVuc")
        void categoryNotFound_skipRow() throws IOException {
            String csv = "maDV,tenDV,maLinhVuc,maPhongBan,thoiHanXL\n" +
                         "DV001,Ten DV,INVALID_CAT,PB001,5\n";

            given(serviceTypeRepository.existsByCode("DV001")).willReturn(false);
            given(categoryRepository.findByCode("INVALID_CAT")).willReturn(Optional.empty());

            ImportResult result = importService.importServices(toFile(csv, "svc.csv"));

            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getErrors().get(0).getField()).isEqualTo("maLinhVuc");
            verify(serviceTypeRepository, never()).save(any());
        }
    }

    // ── Import Staff ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("importStaff()")
    class ImportStaff {

        @Test
        @DisplayName("User không tồn tại → row bị skip")
        void userNotFound_skipRow() throws IOException {
            String csv = "email,maCB,maPhongBan,chucVu\n" +
                         "notfound@test.com,CB001,PB001,Chuyên viên\n";

            given(userRepository.findWithRolesByEmail("notfound@test.com")).willReturn(Optional.empty());

            ImportResult result = importService.importStaff(toFile(csv, "staff.csv"));

            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getErrors().get(0).getField()).isEqualTo("email");
        }

        @Test
        @DisplayName("User không có role STAFF/MANAGER → row bị skip")
        void userWithCitizenRole_skipRow() throws IOException {
            String csv = "email,maCB,maPhongBan,chucVu\n" +
                         "citizen@test.com,CB001,PB001,Chuyên viên\n";

            User user = new User();
            user.setEmail("citizen@test.com");
            user.setRoles(Set.of(citizenRole));
            given(userRepository.findWithRolesByEmail("citizen@test.com")).willReturn(Optional.of(user));

            ImportResult result = importService.importStaff(toFile(csv, "staff.csv"));

            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getErrors().get(0).getField()).isEqualTo("email");
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private MockMultipartFile toFile(String content, String filename) {
        return new MockMultipartFile("file", filename, "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }
}

