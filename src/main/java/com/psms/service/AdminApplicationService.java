package com.psms.service;

import com.psms.dto.request.AssignStaffRequest;
import com.psms.dto.request.UpdateStatusRequest;
import com.psms.dto.response.AdminApplicationResponse;
import com.psms.dto.response.DashboardStatsResponse;
import com.psms.entity.Application;
import com.psms.entity.ApplicationStatusHistory;
import com.psms.entity.Staff;
import com.psms.entity.User;
import com.psms.enums.ApplicationStatus;
import com.psms.exception.BusinessException;
import com.psms.exception.InvalidStatusTransitionException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.mapper.ApplicationMapper;
import com.psms.repository.ApplicationRepository;
import com.psms.repository.ApplicationSpecifications;
import com.psms.repository.ApplicationStatusHistoryRepository;
import com.psms.repository.StaffRepository;
import com.psms.util.ApplicationStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service xử lý nghiệp vụ admin: dashboard, quản lý hồ sơ, state machine, phân công.
 *
 * <p>State machine logic đã được tách sang {@link ApplicationStateMachine}.
 * Service này chỉ orchestrate: validate → update entity → ghi history → return response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final StaffRepository staffRepository;
    private final ApplicationMapper applicationMapper;

    /** Số hồ sơ pending tối đa hiển thị trên dashboard. */
    private static final int RECENT_PENDING_LIMIT = 10;

    /** Trạng thái terminal — khi APPROVED hoặc REJECTED, hồ sơ được coi là hoàn thành. */
    private static final List<ApplicationStatus> COMPLETED_STATUSES =
            List.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED);

    /** Trạng thái cần xử lý — dùng cho sidebar badge và dashboard pending count. */
    private static final List<ApplicationStatus> PENDING_STATUSES =
            List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.RECEIVED);

    // ─── Dashboard ─────────────────────────────────────────────────────────

    /**
     * 4 KPI cards cho dashboard admin.
     */
    public DashboardStatsResponse getDashboardStats() {
        long total = applicationRepository.count();

        // Đang xử lý: RECEIVED + PROCESSING + ADDITIONAL_REQUIRED
        long processing = applicationRepository.countByStatusIn(
                List.of(ApplicationStatus.RECEIVED,
                        ApplicationStatus.PROCESSING,
                        ApplicationStatus.ADDITIONAL_REQUIRED));

        // Hoàn thành: APPROVED + REJECTED
        long completed = applicationRepository.countByStatusIn(COMPLETED_STATUSES);

        // Quá hạn: chưa hoàn thành và đã qua processingDeadline
        long overdue = applicationRepository.countOverdue(COMPLETED_STATUSES, LocalDate.now());

        return DashboardStatsResponse.builder()
                .totalApplications(total)
                .processingApplications(processing)
                .completedApplications(completed)
                .overdueApplications(overdue)
                .build();
    }

    /**
     * Hồ sơ SUBMITTED/RECEIVED mới nhất cần xử lý (dashboard table).
     */
    public List<AdminApplicationResponse> getRecentPendingApplications() {
        List<Application> recent = applicationRepository.findRecentPending(
                PENDING_STATUSES, PageRequest.of(0, RECENT_PENDING_LIMIT));
        return recent.stream().map(this::mapToAdminResponse).toList();
    }

    /**
     * Số hồ sơ chờ xử lý (SUBMITTED + RECEIVED) — dùng cho sidebar badge.
     */
    public long countPending() {
        return applicationRepository.countByStatusIn(PENDING_STATUSES);
    }

    // ─── Admin list / detail ──────────────────────────────────────────────

    /**
     * Danh sách hồ sơ với filter đa chiều, phân trang.
     */
    public Page<AdminApplicationResponse> findAll(ApplicationStatus status,
                                                  Long serviceTypeId,
                                                  Long staffId,
                                                  LocalDateTime from,
                                                  LocalDateTime to,
                                                  int page,
                                                  int size) {
        Specification<Application> spec = ApplicationSpecifications
                .withAdminFilters(status, serviceTypeId, staffId, from, to);

        PageRequest pageable = PageRequest.of(page, size, Sort.by("submittedAt").descending());

        return applicationRepository.findAll(spec, pageable)
                .map(this::mapToAdminResponse);
    }

    /**
     * Chi tiết hồ sơ kèm timeline — admin xem được tất cả hồ sơ.
     */
    public AdminApplicationResponse findById(Long id) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ không tồn tại: " + id));

        return buildDetailResponse(app);
    }

    // ─── Update status (state machine) ───────────────────────────────────

    /**
     * Cập nhật trạng thái hồ sơ theo state machine.
     *
     * <p>Business rules:
     * <ul>
     *   <li>Transition không hợp lệ → throw {@link InvalidStatusTransitionException} (400)</li>
     *   <li>REJECTED / ADDITIONAL_REQUIRED bắt buộc có notes</li>
     *   <li>Ghi {@link ApplicationStatusHistory}</li>
     *   <li>Cập nhật receivedAt khi → RECEIVED, completedAt khi → APPROVED/REJECTED</li>
     * </ul>
     */
    @Transactional
    public AdminApplicationResponse updateStatus(Long id, UpdateStatusRequest request, User actingUser) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ không tồn tại: " + id));

        ApplicationStatus current = app.getStatus();
        ApplicationStatus newStatus = request.getNewStatus();

        // Kiểm tra state machine — dùng ApplicationStateMachine tách biệt
        if (!ApplicationStateMachine.isValidTransition(current, newStatus)) {
            throw new InvalidStatusTransitionException(current.getLabel(), newStatus.getLabel());
        }

        // Ghi chú bắt buộc khi REJECTED / ADDITIONAL_REQUIRED
        if ((newStatus == ApplicationStatus.REJECTED || newStatus == ApplicationStatus.ADDITIONAL_REQUIRED)
                && (request.getNotes() == null || request.getNotes().isBlank())) {
            throw new BusinessException("Ghi chú là bắt buộc khi chuyển sang " + newStatus.getLabel());
        }

        ApplicationStatus oldStatus = app.getStatus();
        app.setStatus(newStatus);

        // Cập nhật timestamp tương ứng
        LocalDateTime now = LocalDateTime.now();
        if (newStatus == ApplicationStatus.RECEIVED) {
            app.setReceivedAt(now);
        } else if (COMPLETED_STATUSES.contains(newStatus)) {
            app.setCompletedAt(now);
        }

        if (newStatus == ApplicationStatus.REJECTED) {
            app.setRejectionReason(request.getNotes());
        }

        applicationRepository.save(app);

        // Ghi history
        historyRepository.save(ApplicationStatusHistory.builder()
                .application(app)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(actingUser)
                .notes(request.getNotes())
                .build());

        log.info("Status updated: appId={} {} -> {} by userId={}",
                id, oldStatus, newStatus, actingUser.getId());

        // Map trực tiếp từ entity đã có trong session, tránh extra DB round-trip
        return buildDetailResponse(app);
    }

    // ─── Assign staff ──────────────────────────────────────────────────────

    /**
     * Phân công cán bộ xử lý hồ sơ. Chỉ cán bộ is_available=true mới được chọn.
     */
    @Transactional
    public AdminApplicationResponse assignStaff(Long id, AssignStaffRequest request) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ không tồn tại: " + id));

        Staff staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Cán bộ không tồn tại: " + request.getStaffId()));

        if (!staff.isAvailable()) {
            throw new BusinessException("Cán bộ này hiện không khả dụng (is_available = false)");
        }

        // assignedStaff tham chiếu User, không phải Staff entity
        app.setAssignedStaff(staff.getUser());
        applicationRepository.save(app);

        log.info("Assigned staff: appId={} staffId={}", id, staff.getId());

        // Map trực tiếp từ entity đã có trong session, tránh extra DB round-trip
        return buildDetailResponse(app);
    }

    // ─── ARCH-1: Methods cho AdminViewController ──────────────────────────

    /**
     * Danh sách cán bộ available theo phòng ban — dùng cho dropdown phân công trong UI.
     * Đặt ở service để AdminViewController không phụ thuộc trực tiếp vào StaffRepository.
     */
    public List<Staff> findAvailableStaffByDepartment(Long departmentId) {
        return staffRepository.findAllByDepartmentIdAndIsAvailableTrue(departmentId);
    }

    // ─── Private helpers ──────────────────────────────────────────────────

    /**
     * Map Application → AdminApplicationResponse với history và tính overdue.
     * Tách ra method riêng để dùng chung ở findById, updateStatus, assignStaff.
     */
    private AdminApplicationResponse buildDetailResponse(Application app) {
        AdminApplicationResponse response = mapToAdminResponse(app);
        response.setStatusHistory(
                applicationMapper.toHistoryResponses(
                        historyRepository.findByApplicationIdOrderByChangedAtAsc(app.getId())));
        return response;
    }

    /**
     * Map Application entity → AdminApplicationResponse và tính overdue.
     *
     * <p>overdue được tính ở đây thay vì trong DTO để:
     * <ul>
     *   <li>Tránh {@code LocalDate.now()} trong DTO (khó mock trong test)</li>
     *   <li>Logic "completed" tập trung ở service, không rải rác</li>
     * </ul>
     */
    private AdminApplicationResponse mapToAdminResponse(Application app) {
        AdminApplicationResponse response = applicationMapper.toAdminResponse(app);
        if (app.getStatus() != null && app.getProcessingDeadline() != null) {
            boolean isCompleted = COMPLETED_STATUSES.contains(app.getStatus());
            response.setOverdue(!isCompleted && LocalDate.now().isAfter(app.getProcessingDeadline()));
        }
        return response;
    }
}
