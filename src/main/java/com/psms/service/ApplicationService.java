package com.psms.service;

import com.psms.dto.request.SubmitApplicationRequest;
import com.psms.dto.response.ApplicationDetailResponse;
import com.psms.dto.response.ApplicationResponse;
import com.psms.entity.Application;
import com.psms.entity.ApplicationStatusHistory;
import com.psms.entity.Citizen;
import com.psms.entity.ServiceType;
import com.psms.enums.ActionType;
import com.psms.enums.ApplicationStatus;
import com.psms.exception.ResourceNotFoundException;
import com.psms.mapper.ApplicationMapper;
import com.psms.repository.ApplicationRepository;
import com.psms.repository.ApplicationStatusHistoryRepository;
import com.psms.repository.CitizenRepository;
import com.psms.repository.ServiceTypeRepository;
import com.psms.util.ApplicationCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service xử lý logic nghiệp vụ cho hồ sơ (Application).
 *
 * <p>submit() có @Transactional write vì tạo 2 record: Application + StatusHistory.
 * Các query read dùng @Transactional(readOnly = true).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final CitizenRepository citizenRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ApplicationCodeGenerator codeGenerator;
    private final ApplicationMapper applicationMapper;
    private final DocumentService documentService;
    private final NotificationService notificationService;

    // ─── Submit ───────────────────────────────────────────────────────

    /**
     * Nộp hồ sơ mới: sinh mã HS → tạo Application (SUBMITTED) → ghi history → lưu file.
     *
     * @param userId  ID của User đang đăng nhập (lấy từ principal)
     * @param request DTO chứa serviceTypeId + notes
     * @param files   danh sách file đính kèm (nullable)
     * @return ApplicationResponse với applicationCode đã sinh
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.SUBMIT_APP,
        entityType = "applications",
        entityIdSpEL = "#result.id",
        description = "'Nộp hồ sơ mới ' + #result.applicationCode + ' — ' + #result.serviceTypeName"
    )
    @Transactional
    public ApplicationResponse submit(Long userId, SubmitApplicationRequest request,
                                      List<MultipartFile> files) {
        // 1. Kiểm tra citizen tồn tại (user phải có profile citizen)
        Citizen citizen = citizenRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin công dân"));

        // 2. Kiểm tra dịch vụ active
        ServiceType serviceType = serviceTypeRepository.findByIdAndIsActiveTrue(request.getServiceTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Dịch vụ không tồn tại hoặc đã ngừng hoạt động"));

        // 3. Sinh mã hồ sơ thread-safe (HS-YYYYMMDD-NNNNN)
        String code = codeGenerator.generate();

        // 4. Tính hạn xử lý = ngày nộp + processingTimeDays
        LocalDateTime now = LocalDateTime.now();
        LocalDate deadline = now.toLocalDate().plusDays(serviceType.getProcessingTimeDays());

        // 5. Tạo Application + ApplicationStatusHistory trong transaction → rollback nếu lỗi
        Application application = Application.builder()
                .applicationCode(code)
                .citizen(citizen)
                .serviceType(serviceType)
                .status(ApplicationStatus.SUBMITTED)
                .submittedAt(now)
                .processingDeadline(deadline)
                .notes(request.getNotes())
                .build();

        application = applicationRepository.save(application);

        historyRepository.save(ApplicationStatusHistory.builder()
                .application(application)
                .oldStatus(null)
                .newStatus(ApplicationStatus.SUBMITTED)
                .changedBy(citizen.getUser())
                .notes("Công dân nộp hồ sơ")
                .build());

        //6. Lưu file đính kèm - nếu throw, transaction ở bước 5 sẽ rollback → không có Application nửa vời nếu file lỗi.
        documentService.saveDocuments(application, files, citizen.getUser(), false);

        // 7. Tạo notification xác nhận nộp hồ sơ thành công
        notificationService.notifyApplicationSubmitted(application);

        return applicationMapper.toResponse(application);
    }

    // ─── Query — danh sách HS của citizen ─────────────────────────────

    /**
     * Lấy danh sách hồ sơ của citizen, filter status tuỳ chọn, phân trang.
     * Ownership được đảm bảo bằng cách tra citizenId từ userId.
     */
    public Page<ApplicationResponse> findMyApplications(
            Long userId, ApplicationStatus status, int page, int size) {

        Citizen citizen = citizenRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin công dân"));

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return applicationRepository.findByCitizenIdWithStatus(citizen.getId(), status, pageable)
                .map(applicationMapper::toResponse);
    }

    // ─── Query — chi tiết HS của citizen ──────────────────────────────

    /**
     * Chi tiết hồ sơ kèm timeline.
     * Ownership check: tìm bằng id + citizenId → 404 nếu sai citizen (tránh IDOR).
     */
    public ApplicationDetailResponse findMyApplicationById(Long userId, Long applicationId) {
        Citizen citizen = citizenRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin công dân"));

        // findByIdAndCitizenId đảm bảo citizen chỉ thấy HS của mình
        Application application = applicationRepository.findByIdAndCitizenId(applicationId, citizen.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ không tồn tại hoặc bạn không có quyền xem"));

        ApplicationDetailResponse response = applicationMapper.toDetailResponse(application);

        log.debug("Mapped ApplicationDetailResponse for applicationId={}, code={}",
            application.getId(),
            application.getApplicationCode());

        // Gắn timeline theo thứ tự thời gian tăng dần
        List<ApplicationStatusHistory> histories =
                historyRepository.findByApplicationIdOrderByChangedAtAsc(applicationId);
        response.setStatusHistory(applicationMapper.toHistoryResponses(histories));

        // Gắn tài liệu: tách citizen docs vs staff response docs
        response.setCitizenDocuments(documentService.findCitizenDocuments(applicationId));
        response.setStaffDocuments(documentService.findStaffDocuments(applicationId));

        return response;
    }
}

