package com.psms.service;

import com.psms.dto.response.NotificationResponse;
import com.psms.entity.Application;
import com.psms.entity.Notification;
import com.psms.entity.User;
import com.psms.enums.ApplicationStatus;
import com.psms.enums.NotificationType;
import com.psms.exception.ResourceNotFoundException;
import com.psms.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service xử lý nghiệp vụ thông báo in-app.
 *
 * <p>Business rules
 * <ul>
 *   <li>Citizen chỉ xem được notification của mình</li>
 *   <li>markAsRead: ownership check chống IDOR (findByIdAndUserId)</li>
 *   <li>countUnread: dùng cho badge trên topbar</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // ── List ─────────────────────────────────────────────────────────────

    /**
     * Danh sách thông báo của user, có filter isRead.
     */
    public Page<NotificationResponse> findByUser(Long userId, Boolean isRead, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return notificationRepository.findByUser(userId, isRead, pageable)
                .map(this::toResponse);
    }

    // ── Count unread ──────────────────────────────────────────────────────

    /** Đếm thông báo chưa đọc — dùng cho badge topbar. */
    public long countUnread(Long userId) {
        return notificationRepository.countUnread(userId);
    }

    // ── Mark as read ──────────────────────────────────────────────────────

    /**
     * Đánh dấu 1 thông báo đã đọc.
     * Kiểm tra ownership (chống IDOR) bằng findByIdAndUserId.
     */
    @Transactional
    public void markAsRead(Long notifId, Long userId) {
        Notification notif = notificationRepository.findByIdAndUserId(notifId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Thông báo không tồn tại"));

        if (!notif.isRead()) {
            notif.setRead(true);
            notif.setReadAt(LocalDateTime.now());
            notificationRepository.save(notif);
        }
    }

    // ── Mark all as read ──────────────────────────────────────────────────

    /** Đánh dấu tất cả thông báo chưa đọc của user thành đã đọc. */
    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = notificationRepository.markAllAsRead(userId);
        log.debug("Marked {} notifications as read for userId={}", updated, userId);
    }

    // ── Create (write methods) ────────────────────────────────────────────

    /**
     * Tạo thông báo khi admin cập nhật trạng thái hồ sơ.
     *
     * <p>Mapping status → NotificationType:
     * <ul>
     *   <li>RECEIVED           → APPLICATION_RECEIVED</li>
     *   <li>ADDITIONAL_REQUIRED → ADDITIONAL_REQUIRED</li>
     *   <li>APPROVED           → APPROVED</li>
     *   <li>REJECTED           → REJECTED</li>
     *   <li>Các status khác   → STATUS_UPDATED</li>
     * </ul>
     */
    @Transactional
    public void notifyStatusChange(Application application, ApplicationStatus newStatus, String notes) {
        NotificationType type = resolveTypeFromStatus(newStatus);
        String title = buildStatusChangeTitle(newStatus);
        String content = buildStatusChangeContent(application, newStatus, notes);
        create(application.getCitizen().getUser(), application, type, title, content);
    }

    /**
     * Tạo thông báo xác nhận khi citizen nộp hồ sơ thành công.
     */
    @Transactional
    public void notifyApplicationSubmitted(Application application) {
        String content = String.format(
                "Hồ sơ %s đã được nộp thành công và đang chờ cán bộ tiếp nhận.",
                application.getApplicationCode());
        create(application.getCitizen().getUser(), application,
                NotificationType.APPLICATION_RECEIVED,
                "Nộp hồ sơ thành công", content);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /** Lưu Notification vào DB — low-level helper dùng chung. */
    private void create(User user, Application application,
                        NotificationType type, String title, String content) {
        notificationRepository.save(Notification.builder()
                .user(user)
                .application(application)
                .type(type)
                .title(title)
                .content(content)
                .build());
        log.debug("Notification created: userId={} appId={} type={}",
                user.getId(), application.getId(), type);
    }

    /** Map ApplicationStatus → NotificationType phù hợp. */
    private static NotificationType resolveTypeFromStatus(ApplicationStatus status) {
        return switch (status) {
            case RECEIVED             -> NotificationType.APPLICATION_RECEIVED;
            case ADDITIONAL_REQUIRED  -> NotificationType.ADDITIONAL_REQUIRED;
            case APPROVED             -> NotificationType.APPROVED;
            case REJECTED             -> NotificationType.REJECTED;
            default                   -> NotificationType.STATUS_UPDATED;
        };
    }

    private static String buildStatusChangeTitle(ApplicationStatus newStatus) {
        return switch (newStatus) {
            case RECEIVED             -> "Hồ sơ đã được tiếp nhận";
            case PROCESSING           -> "Hồ sơ đang được xử lý";
            case ADDITIONAL_REQUIRED  -> "Yêu cầu bổ sung tài liệu";
            case APPROVED             -> "Hồ sơ đã được phê duyệt";
            case REJECTED             -> "Hồ sơ bị từ chối";
            default                   -> "Trạng thái hồ sơ đã thay đổi";
        };
    }

    /** Xây dựng nội dung thông báo dựa trên trạng thái và ghi chú của admin. */
    private static String buildStatusChangeContent(Application app,
                                                   ApplicationStatus newStatus,
                                                   String notes) {
        String code = app.getApplicationCode();
        String fallback = "Vui lòng liên hệ cơ quan để biết thêm chi tiết.";
        return switch (newStatus) {
            case RECEIVED -> String.format(
                    "Hồ sơ %s đã được cán bộ tiếp nhận và đang trong quá trình xử lý.", code);
            case PROCESSING -> String.format(
                    "Hồ sơ %s đang được cán bộ xem xét và xử lý.", code);
            case ADDITIONAL_REQUIRED -> String.format(
                    "Hồ sơ %s cần bổ sung tài liệu. Lý do: %s",
                    code, (notes != null && !notes.isBlank()) ? notes : fallback);
            case APPROVED -> String.format(
                    "Chúc mừng! Hồ sơ %s đã được phê duyệt thành công.", code);
            case REJECTED -> String.format(
                    "Hồ sơ %s đã bị từ chối. Lý do: %s",
                    code, (notes != null && !notes.isBlank()) ? notes : fallback);
            default -> String.format(
                    "Trạng thái hồ sơ %s đã được cập nhật thành: %s",
                    code, newStatus.getLabel());
        };
    }

    // ── Private mapper ────────────────────────────────────────────────────

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .content(n.getContent())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .applicationId(n.getApplication() != null ? n.getApplication().getId() : null)
                .build();
    }
}

