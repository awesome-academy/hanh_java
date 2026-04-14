package com.psms.service;

import com.psms.dto.response.ActivityLogResponse;
import com.psms.entity.ActivityLog;
import com.psms.entity.User;
import com.psms.enums.ActionType;
import com.psms.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.JoinType;
import java.time.LocalDateTime;

/**
 * Service ghi và truy vấn nhật ký hoạt động hệ thống.
 *
 * <p>Design choice — tại sao dùng {@link Propagation#REQUIRES_NEW} cho {@code log()}:
 * <ul>
 *   <li>Log phải được commit <em>ngay lập tức</em>, độc lập với transaction của caller.</li>
 *   <li>Nếu caller rollback (vd: exception sau khi log), log vẫn tồn tại → agit sudit trail đầy đủ.</li>
 *   <li>Trade-off: mỗi log call mở thêm 1 connection DB ngắn — chấp nhận được vì
 *       log chỉ được ghi sau khi business logic thành công (Aspect không log khi exception).</li>
 * </ul>
 * <p><b>Lưu ý:</b> Việc ghi log hiện tại được thực hiện qua Aspect cho annotation {@code @LogActivity}:
 * <ul>
 *   <li>Aspect chỉ ghi log khi method annotated thực thi thành công (không exception).</li>
 *   <li>Nếu gọi trực tiếp {@code log()}, log sẽ luôn được commit độc lập với transaction bên ngoài.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityLogService {

    private final ActivityLogRepository repository;

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Ghi một activity log entry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action, String entityType, String entityId,
                    String description, String ipAddress, String userAgent) {
        repository.save(ActivityLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build());
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Danh sách activity logs với filter đa chiều, phân trang.
     * Dùng cho admin log list.
     *
     * @param keyword free-text search trên action hoặc tên người thực hiện (nullable)
     */
    public Page<ActivityLogResponse> findLogs(String keyword, Long userId, String action,
                                              LocalDateTime from, LocalDateTime to,
                                              int page, int size) {
        Specification<ActivityLog> spec = buildSpec(keyword, userId, action, from, to);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    /** Backward-compat overload — REST API và existing tests không cần thay đổi. */
    public Page<ActivityLogResponse> findLogs(Long userId, String action,
                                              LocalDateTime from, LocalDateTime to,
                                              int page, int size) {
        return findLogs(null, userId, action, from, to, page, size);
    }

    // ── Purge ─────────────────────────────────────────────────────────────────

    /**
     * Xóa logs cũ hơn {@code days} ngày — chỉ SUPER_ADMIN.
     * Bulk delete, không load entity vào memory.
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.DELETE_LOG,
        entityType = "logs",
        entityIdSpEL = "#days",
        description = "'Thực hiện xóa log cũ hơn: ' + #days + ' ngày'"
    )
    @Transactional
    public int purgeOlderThan(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        int deleted = repository.deleteOlderThan(cutoff);
        log.info("Purged {} activity logs older than {} days (before {})", deleted, days, cutoff);
        return deleted;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private Specification<ActivityLog> buildSpec(String keyword, Long userId, String action,
                                                   LocalDateTime from, LocalDateTime to) {
        // Build specification động dựa trên filter — AND các điều kiện, mỗi điều kiện có thể null (không filter)
        Specification<ActivityLog> spec = (root, q, cb) -> null;
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("action")), kw),
                    cb.like(cb.lower(root.join("user", JoinType.LEFT).get("fullName")), kw)
            ));
        }
        if (userId != null) {
            spec = spec.and((root, q, cb) ->
                    cb.equal(root.get("user").get("id"), userId));
        }
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, q, cb) ->
                    cb.equal(root.get("action"), action));
        }
        if (from != null) {
            spec = spec.and((root, q, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, q, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return spec;
    }

    private ActivityLogResponse toResponse(ActivityLog log) {
        return ActivityLogResponse.builder()
                .id(log.getId())
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .userFullName(log.getUser() != null ? log.getUser().getFullName() : "System")
                .userEmail(log.getUser() != null ? log.getUser().getEmail() : null)
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .description(log.getDescription())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
