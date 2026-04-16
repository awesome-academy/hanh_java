package com.psms.service;

import com.psms.annotation.LogActivity;
import com.psms.dto.response.ActivityLogResponse;
import com.psms.entity.ActivityLog;
import com.psms.entity.User;
import com.psms.enums.ActionType;
import com.psms.repository.ActivityLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit test cho ActivityLogService + kiểm tra annotation @LogActivity.
 *
 * Covers:
 * - #12-13: Verify @LogActivity trên updateStatus có đúng action + entityType
 * - ActivityLogService.log(): ghi đúng entity vào repository
 * - ActivityLogService.findLogs(): trả Page kết quả
 * - ActivityLogService.purgeOlderThan(): xóa log cũ và trả số bản ghi
 */
@ExtendWith(MockitoExtension.class)
class ActivityLogServiceTest {

    @Mock ActivityLogRepository activityLogRepository;

    @InjectMocks ActivityLogService activityLogService;

    // ── #12-13: Annotation check ──────────────────────────────────────────────

    @Nested
    @DisplayName("#12-13 @LogActivity annotation correctness")
    class AnnotationCheck {

        /**
         * Kiểm tra updateStatus có @LogActivity với action="UPDATE_STATUS" và entityType="applications".
         * Đây là cách test non-intrusive: reflect trên method signature thay vì start Spring context.
         */
        @Test
        @DisplayName("AdminApplicationService.updateStatus có @LogActivity(action=UPDATE_STATUS, entityType=applications)")
        void updateStatus_hasCorrectLogActivityAnnotation() throws NoSuchMethodException {
            Method updateStatus = AdminApplicationService.class.getMethod(
                    "updateStatus",
                    Long.class,
                    com.psms.dto.request.UpdateStatusRequest.class,
                    User.class);

            LogActivity ann = updateStatus.getAnnotation(LogActivity.class);

            assertThat(ann).as("@LogActivity phải có mặt trên updateStatus").isNotNull();
            assertThat(ann.action()).isEqualTo(ActionType.UPDATE_STATUS);
            assertThat(ann.entityType()).isEqualTo("applications");
            assertThat(ann.entityIdSpEL()).isEqualTo("#p0");
        }

        @Test
        @DisplayName("AdminApplicationService.assignStaff có @LogActivity(action=ASSIGN_STAFF)")
        void assignStaff_hasLogActivityAnnotation() throws NoSuchMethodException {
            Method assignStaff = AdminApplicationService.class.getMethod(
                    "assignStaff",
                    Long.class,
                    com.psms.dto.request.AssignStaffRequest.class);

            LogActivity ann = assignStaff.getAnnotation(LogActivity.class);

            assertThat(ann).isNotNull();
            assertThat(ann.action()).isEqualTo(ActionType.ASSIGN_STAFF);
            assertThat(ann.entityType()).isEqualTo("applications");
        }

        @Test
        @DisplayName("AdminUserService.createUser có @LogActivity(action=CREATE_USER)")
        void createUser_hasLogActivityAnnotation() throws Exception {
            Method createUser = AdminUserService.class.getMethod(
                    "createUser",
                    com.psms.dto.request.CreateUserRequest.class);

            LogActivity ann = createUser.getAnnotation(LogActivity.class);

            assertThat(ann).isNotNull();
            assertThat(ann.action()).isEqualTo(ActionType.CREATE_USER);
            assertThat(ann.entityType()).isEqualTo("users");
        }
    }

    // ── ActivityLogService.log() ──────────────────────────────────────────────

    @Nested
    @DisplayName("log() — ghi activity log vào repository")
    class LogMethod {

        @Test
        @DisplayName("log() lưu đúng entity với đầy đủ các trường")
        void log_savesCorrectEntity() {
            User user = new User();
            user.setId(10L);
            user.setEmail("admin@test.com");
            user.setFullName("Admin Test");

            activityLogService.log(user, "UPDATE_STATUS", "applications", "42",
                    "UPDATE_STATUS [applications] id=42", "127.0.0.1", "Mozilla/5.0");

            ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
            verify(activityLogRepository).save(captor.capture());

            ActivityLog saved = captor.getValue();
            assertThat(saved.getUser()).isSameAs(user);
            assertThat(saved.getAction()).isEqualTo("UPDATE_STATUS");
            assertThat(saved.getEntityType()).isEqualTo("applications");
            assertThat(saved.getEntityId()).isEqualTo("42");
            assertThat(saved.getDescription()).isEqualTo("UPDATE_STATUS [applications] id=42");
            assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("log() chấp nhận user=null (system action)")
        void log_acceptsNullUser() {
            activityLogService.log(null, "LOGIN", null, null, "System LOGIN", null, null);

            ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
            verify(activityLogRepository).save(captor.capture());

            assertThat(captor.getValue().getUser()).isNull();
        }
    }

    // ── ActivityLogService.findLogs() ─────────────────────────────────────────

    @Nested
    @DisplayName("findLogs() — truy vấn có phân trang")
    class FindLogs {

        @Test
        @DisplayName("findLogs() trả về Page được map sang DTO")
        void findLogs_returnsMappedPage() {
            User user = new User();
            user.setId(5L);
            user.setFullName("Staff A");
            user.setEmail("staff@test.com");

            ActivityLog logEntry = new ActivityLog();
            logEntry.setId(1L);
            logEntry.setUser(user);
            logEntry.setAction("UPDATE_STATUS");
            logEntry.setEntityType("applications");
            logEntry.setEntityId("10");
            logEntry.setDescription("UPDATE_STATUS [applications] id=10");
            logEntry.setIpAddress("192.168.1.1");
            logEntry.setCreatedAt(LocalDateTime.now());

            Page<ActivityLog> page = new PageImpl<>(List.of(logEntry));
            given(activityLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(page);

            Page<ActivityLogResponse> result = activityLogService.findLogs(null, null, null, null, 0, 10);

            assertThat(result.getTotalElements()).isEqualTo(1);
            ActivityLogResponse dto = result.getContent().get(0);
            assertThat(dto.getAction()).isEqualTo("UPDATE_STATUS");
            assertThat(dto.getEntityType()).isEqualTo("applications");
            assertThat(dto.getEntityId()).isEqualTo("10");
            assertThat(dto.getUserFullName()).isEqualTo("Staff A");
            assertThat(dto.getUserEmail()).isEqualTo("staff@test.com");
        }

        @Test
        @DisplayName("findLogs() với user=null → userFullName='System'")
        void findLogs_nullUserMapsToSystem() {
            ActivityLog logEntry = new ActivityLog();
            logEntry.setId(2L);
            logEntry.setUser(null);
            logEntry.setAction("CLEANUP");
            logEntry.setDescription("Scheduled cleanup");
            logEntry.setCreatedAt(LocalDateTime.now());

            Page<ActivityLog> page = new PageImpl<>(List.of(logEntry));
            given(activityLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .willReturn(page);

            Page<ActivityLogResponse> result = activityLogService.findLogs(null, null, null, null, 0, 10);

            ActivityLogResponse dto = result.getContent().get(0);
            assertThat(dto.getUserFullName()).isEqualTo("System");
            assertThat(dto.getUserId()).isNull();
        }
    }

    // ── ActivityLogService.purgeOlderThan() ──────────────────────────────────

    @Test
    @DisplayName("purgeOlderThan(30) gọi deleteOlderThan với cutoff = now - 30 days và trả đúng count")
    void purgeOlderThan_callsDeleteWithCorrectCutoffAndReturnsCount() {
        given(activityLogRepository.deleteOlderThan(any(LocalDateTime.class))).willReturn(15);

        int deleted = activityLogService.purgeOlderThan(30);

        assertThat(deleted).isEqualTo(15);
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(activityLogRepository).deleteOlderThan(cutoffCaptor.capture());

        // Cutoff phải là khoảng 30 ngày trước now (±1 phút để tránh flaky)
        LocalDateTime expected = LocalDateTime.now().minusDays(30);
        assertThat(cutoffCaptor.getValue()).isBetween(expected.minusMinutes(1), expected.plusMinutes(1));
    }
}

