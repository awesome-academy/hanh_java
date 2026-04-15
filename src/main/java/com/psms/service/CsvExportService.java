package com.psms.service;

import com.psms.entity.*;
import com.psms.enums.ActionType;
import com.psms.enums.ApplicationStatus;
import com.psms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xuất dữ liệu CSV cho admin.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>UTF-8 BOM (EF BB BF) được thêm vào đầu mỗi file để Excel đọc đúng tiếng Việt.</li>
 *   <li>Mỗi method load toàn bộ dữ liệu vào memory (trước mắt phù hợp với scope vài nghìn records).
 *       Nếu cần scale, chuyển sang streaming với ScrollableResults.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
public class CsvExportService {

    /** UTF-8 BOM — 3 bytes đầu để Excel nhận diện encoding UTF-8 */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final CitizenRepository citizenRepository;
    private final ApplicationRepository applicationRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final DepartmentRepository departmentRepository;
    private final StaffRepository staffRepository;


    /**
     * Export dữ liệu ra CSV theo loại ExportType.
     * @param type loại export (CITIZENS, APPLICATIONS, SERVICES, DEPARTMENTS, STAFF)
     * @return byte[] CSV content
     */
    public byte[] exportByType(com.psms.enums.ExportType type) throws IOException {
        return switch (type) {
            case CITIZENS -> exportCitizens();
            case APPLICATIONS -> exportApplications();
            case SERVICES -> exportServices();
            case DEPARTMENTS -> exportDepartments();
            case STAFF -> exportStaff();
        };
    }

    // ── Export Citizens ────────────────────────────────────────────────────────

    /**
     * Export danh sách công dân ra CSV.
     * Columns: Họ tên, Email, CCCD, Ngày sinh, Giới tính, Địa chỉ, Số HS đã nộp
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.EXPORT,
        entityType = "citizens",
        description = "'Export danh sách công dân ra CSV'"
    )
    public byte[] exportCitizens() throws IOException {
        List<Citizen> citizens = citizenRepository.findAllWithUser();

        // Batch-count total applications per citizen để tránh N+1
        Map<Long, Long> appCountMap = applicationRepository.countAllGroupByCitizenId()
                .stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        String[] headers = {"Họ tên", "Email", "CCCD/CMND", "Ngày sinh", "Giới tính",
                            "Địa chỉ thường trú", "Số hồ sơ đã nộp"};

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, buildFormat(headers))) {
            for (Citizen c : citizens) {
                User u = c.getUser();
                printer.printRecord(
                        u.getFullName(),
                        u.getEmail(),
                        c.getNationalId(),
                        c.getDateOfBirth() != null ? c.getDateOfBirth().toString() : "",
                        c.getGender() != null ? c.getGender().getLabel() : "",
                        c.getPermanentAddress() != null ? c.getPermanentAddress() : "",
                        appCountMap.getOrDefault(c.getId(), 0L)
                );
            }
        }
        log.info("Exported {} citizens to CSV", citizens.size());
        return withBom(sw.toString());
    }

    // ── Export Applications ────────────────────────────────────────────────────

    /**
     * Export danh sách hồ sơ ra CSV.
     * Columns: Mã HS, Tên DV, Công dân, Ngày nộp, Ngày hoàn thành, Trạng thái, Cán bộ XL, Thời gian XL (ngày)
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.EXPORT,
        entityType = "applications",
        description = "'Export danh sách hồ sơ ra CSV'"
    )
    public byte[] exportApplications() throws IOException {
        List<Application> apps = applicationRepository.findAllForExport();

        String[] headers = {"Mã hồ sơ", "Tên dịch vụ", "Công dân", "Ngày nộp",
                            "Ngày hoàn thành", "Trạng thái", "Cán bộ xử lý", "Thời gian XL (ngày)"};

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, buildFormat(headers))) {
            for (Application a : apps) {
                Long processingDays = null;
                if (a.getSubmittedAt() != null && a.getCompletedAt() != null) {
                    processingDays = ChronoUnit.DAYS.between(
                            a.getSubmittedAt().toLocalDate(), a.getCompletedAt().toLocalDate());
                }
                printer.printRecord(
                        a.getApplicationCode(),
                        a.getServiceType().getName(),
                        a.getCitizen().getUser().getFullName(),
                        a.getSubmittedAt() != null ? a.getSubmittedAt().toLocalDate().toString() : "",
                        a.getCompletedAt() != null ? a.getCompletedAt().toLocalDate().toString() : "",
                        a.getStatus() != null ? a.getStatus().getLabel() : "",
                        a.getAssignedStaff() != null ? a.getAssignedStaff().getFullName() : "",
                        processingDays != null ? processingDays : ""
                );
            }
        }
        log.info("Exported {} applications to CSV", apps.size());
        return withBom(sw.toString());
    }

    // ── Export Services ────────────────────────────────────────────────────────

    /**
     * Export danh sách dịch vụ công ra CSV.
     * Columns: Mã DV, Tên, Lĩnh vực, Cơ quan, Thời hạn XL (ngày), Lệ phí, Trạng thái
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.EXPORT,
        entityType = "service_types",
        description = "'Export danh sách dịch vụ công ra CSV'"
    )
    public byte[] exportServices() throws IOException {
        List<ServiceType> services = serviceTypeRepository.findAllForExport();

        String[] headers = {"Mã dịch vụ", "Tên dịch vụ", "Lĩnh vực", "Cơ quan tiếp nhận",
                            "Thời hạn xử lý (ngày)", "Lệ phí (VNĐ)", "Trạng thái"};

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, buildFormat(headers))) {
            for (ServiceType s : services) {
                printer.printRecord(
                        s.getCode(),
                        s.getName(),
                        s.getCategory() != null ? s.getCategory().getName() : "",
                        s.getDepartment() != null ? s.getDepartment().getName() : "",
                        s.getProcessingTimeDays(),
                        s.getFee() != null ? s.getFee().toPlainString() : "0",
                        s.isActive() ? "Đang hoạt động" : "Tạm dừng"
                );
            }
        }
        log.info("Exported {} service types to CSV", services.size());
        return withBom(sw.toString());
    }

    // ── Export Departments ─────────────────────────────────────────────────────

    /**
     * Export danh sách phòng ban ra CSV.
     * Columns: Mã PB, Tên, Địa chỉ, SĐT, Trưởng phòng, Số cán bộ
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.EXPORT,
        entityType = "departments",
        description = "'Export danh sách phòng ban ra CSV'"
    )
    public byte[] exportDepartments() throws IOException {
        List<Department> departments = departmentRepository.findAllWithLeader();

        // Batch-count staff per department — tránh N+1
        List<Long> deptIds = departments.stream().map(Department::getId).toList();
        Map<Long, Long> staffCountMap = deptIds.isEmpty() ? Map.of() :
                staffRepository.countByDepartmentIdIn(deptIds).stream()
                        .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        String[] headers = {"Mã phòng ban", "Tên phòng ban", "Địa chỉ", "Số điện thoại",
                            "Trưởng phòng", "Số cán bộ"};

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, buildFormat(headers))) {
            for (Department d : departments) {
                printer.printRecord(
                        d.getCode(),
                        d.getName(),
                        d.getAddress() != null ? d.getAddress() : "",
                        d.getPhone() != null ? d.getPhone() : "",
                        d.getLeader() != null ? d.getLeader().getFullName() : "",
                        staffCountMap.getOrDefault(d.getId(), 0L)
                );
            }
        }
        log.info("Exported {} departments to CSV", departments.size());
        return withBom(sw.toString());
    }

    // ── Export Staff ───────────────────────────────────────────────────────────

    /**
     * Export danh sách cán bộ ra CSV.
     * Columns: Mã CB, Họ tên, Email, Phòng ban, Chức vụ, Số HS đang XL
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.EXPORT,
        entityType = "staff",
        description = "'Export danh sách cán bộ ra CSV'"
    )
    public byte[] exportStaff() throws IOException {
        List<Staff> staffList = staffRepository.findAllForExport();

        // Batch-count active applications per staff
        List<Long> userIds = staffList.stream().map(s -> s.getUser().getId()).toList();
        List<ApplicationStatus> activeStatuses = List.of(
                ApplicationStatus.SUBMITTED, ApplicationStatus.RECEIVED,
                ApplicationStatus.PROCESSING, ApplicationStatus.ADDITIONAL_REQUIRED);
        Map<Long, Long> activeCountMap = userIds.isEmpty() ? Map.of() :
                applicationRepository.countActiveByAssignedStaffIdIn(userIds, activeStatuses)
                        .stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        String[] headers = {"Mã cán bộ", "Họ tên", "Email", "Phòng ban", "Chức vụ", "Số HS đang XL"};

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, buildFormat(headers))) {
            for (Staff s : staffList) {
                printer.printRecord(
                        s.getStaffCode(),
                        s.getUser().getFullName(),
                        s.getUser().getEmail(),
                        s.getDepartment().getName(),
                        s.getPosition() != null ? s.getPosition() : "",
                        activeCountMap.getOrDefault(s.getUser().getId(), 0L)
                );
            }
        }
        log.info("Exported {} staff to CSV", staffList.size());
        return withBom(sw.toString());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Build CSVFormat chuẩn với header row.
     * Dùng CSVFormat.DEFAULT (RFC 4180) — Excel đọc được.
     */
    private CSVFormat buildFormat(String[] headers) {
        return CSVFormat.DEFAULT.builder()
                .setHeader(headers)
                .build();
    }

    /**
     * Prepend UTF-8 BOM vào content để Excel nhận diện encoding UTF-8.
     */
    private byte[] withBom(String content) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + contentBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(contentBytes, 0, result, UTF8_BOM.length, contentBytes.length);
        return result;
    }
}

