package com.psms.service;

import com.psms.dto.request.UpdateStatusRequest;
import com.psms.dto.response.AdminApplicationResponse;
import com.psms.entity.*;
import com.psms.enums.ApplicationStatus;
import com.psms.exception.BusinessException;
import com.psms.exception.InvalidStatusTransitionException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.mapper.ApplicationMapper;
import com.psms.util.ApplicationStateMachine;
import com.psms.repository.ApplicationRepository;
import com.psms.repository.ApplicationStatusHistoryRepository;
import com.psms.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit test cho AdminApplicationService.
 *
 * Covers:
 * - #07-10: All valid state machine transitions
 * - #07-11: All invalid transitions throw InvalidStatusTransitionException (400)
 * - State machine: REJECTED/ADDITIONAL_REQUIRED requires notes
 * - Dashboard stats: countPending
 */
@ExtendWith(MockitoExtension.class)
class AdminApplicationServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationStatusHistoryRepository historyRepository;
    @Mock StaffRepository staffRepository;
    @Mock ApplicationMapper applicationMapper;
    @Mock DocumentService documentService;

    @InjectMocks AdminApplicationService adminApplicationService;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private Application makeApp(ApplicationStatus status) {
        User user = new User();
        user.setId(1L);

        Citizen citizen = new Citizen();
        citizen.setId(1L);
        citizen.setUser(user);

        Application app = new Application();
        app.setId(10L);
        app.setApplicationCode("HS-20260101-00001");
        app.setStatus(status);
        app.setCitizen(citizen);
        return app;
    }

    private User actingUser() {
        User u = new User();
        u.setId(99L);
        u.setEmail("admin@test.com");
        return u;
    }

    // ── #07-10: Valid transitions ─────────────────────────────────────────────

    @Nested
    @DisplayName("#07-10 Valid state machine transitions")
    class ValidTransitions {

        @Test
        @DisplayName("SUBMITTED -> RECEIVED: OK, sets receivedAt")
        void submittedToReceived() {
            Application app = makeApp(ApplicationStatus.SUBMITTED);
            given(applicationRepository.findById(10L)).willReturn(Optional.of(app));
            given(applicationMapper.toAdminResponse(any())).willReturn(new AdminApplicationResponse());
            given(historyRepository.findByApplicationIdOrderByChangedAtAsc(10L)).willReturn(List.of());

            UpdateStatusRequest req = UpdateStatusRequest.builder()
                    .newStatus(ApplicationStatus.RECEIVED).build();

            adminApplicationService.updateStatus(10L, req, actingUser());

            assertThat(app.getStatus()).isEqualTo(ApplicationStatus.RECEIVED);
            assertThat(app.getReceivedAt()).isNotNull();
            verify(historyRepository).save(any(ApplicationStatusHistory.class));
        }

        @Test
        @DisplayName("RECEIVED -> PROCESSING: OK")
        void receivedToProcessing() {
            Application app = makeApp(ApplicationStatus.RECEIVED);
            given(applicationRepository.findById(10L)).willReturn(Optional.of(app));
            given(applicationMapper.toAdminResponse(any())).willReturn(new AdminApplicationResponse());
            given(historyRepository.findByApplicationIdOrderByChangedAtAsc(10L)).willReturn(List.of());

            UpdateStatusRequest req = UpdateStatusRequest.builder()
                    .newStatus(ApplicationStatus.PROCESSING).build();

            adminApplicationService.updateStatus(10L, req, actingUser());

            assertThat(app.getStatus()).isEqualTo(ApplicationStatus.PROCESSING);
        }

        @Test
        @DisplayName("PROCESSING -> APPROVED: OK, sets completedAt")
        void processingToApproved() {
            Application app = makeApp(ApplicationStatus.PROCESSING);
            given(applicationRepository.findById(10L)).willReturn(Optional.of(app));
            given(applicationMapper.toAdminResponse(any())).willReturn(new AdminApplicationResponse());
            given(historyRepository.findByApplicationIdOrderByChangedAtAsc(10L)).willReturn(List.of());

            UpdateStatusRequest req = UpdateStatusRequest.builder()
                    .newStatus(ApplicationStatus.APPROVED).build();

            adminApplicationService.updateStatus(10L, req, actingUser());

            assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
            assertThat(app.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("PROCESSING -> REJECTED with notes: OK, sets rejectionReason + completedAt")
        void processingToRejectedWithNotes() {
            Application app = makeApp(ApplicationStatus.PROCESSING);
            given(applicationRepository.findById(10L)).willReturn(Optional.of(app));
            given(applicationMapper.toAdminResponse(any())).willReturn(new AdminApplicationResponse());
            given(historyRepository.findByApplicationIdOrderByChangedAtAsc(10L)).willReturn(List.of());

            UpdateStatusRequest req = UpdateStatusRequest.builder()
                    .newStatus(ApplicationStatus.REJECTED)
                    .notes("Ho so thieu giay to")
                    .build();

            adminApplicationService.updateStatus(10L, req, actingUser());

            assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
            assertThat(app.getRejectionReason()).isEqualTo("Ho so thieu giay to");
            assertThat(app.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("PROCESSING -> ADDITIONAL_REQUIRED with notes: OK")
        void processingToAdditionalRequired() {
            Application app = makeApp(ApplicationStatus.PROCESSING);
            given(applicationRepository.findById(10L)).willReturn(Optional.of(app));
            given(applicationMapper.toAdminResponse(any())).willReturn(new AdminApplicationResponse());
            given(historyRepository.findByApplicationIdOrderByChangedAtAsc(10L)).willReturn(List.of());

            UpdateStatusRequest req = UpdateStatusRequest.builder()
                    .newStatus(ApplicationStatus.ADDITIONAL_REQUIRED)
                    .notes("Can bo sung CCCD mat sau")
                    .build();

            adminApplicationService.updateStatus(10L, req, actingUser());

            assertThat(app.getStatus()).isEqualTo(ApplicationStatus.ADDITIONAL_REQUIRED);
        }
    }

    // ── #07-11: Invalid transitions ───────────────────────────────────────────

    @Nested
    @DisplayName("#07-11 Invalid transitions throw exception and do not change DB")
    class InvalidTransitions {

        /**
         * Tat ca cap transition khong hop le → throw InvalidStatusTransitionException.
         * DB khong thay doi (verify applicationRepository.save() khong duoc goi).
         */
        @ParameterizedTest(name = "{0} -> {1}: invalid")
        @CsvSource({
                "SUBMITTED, PROCESSING",
                "SUBMITTED, APPROVED",
                "SUBMITTED, REJECTED",
                "RECEIVED, APPROVED",
                "RECEIVED, REJECTED",
                "RECEIVED, ADDITIONAL_REQUIRED",
                "APPROVED, RECEIVED",
                "APPROVED, PROCESSING",
                "REJECTED, PROCESSING",
                "REJECTED, RECEIVED",
                "DRAFT, RECEIVED"
        })
        void invalidTransitionThrows(String fromStr, String toStr) {
            ApplicationStatus from = ApplicationStatus.valueOf(fromStr);
            ApplicationStatus to   = ApplicationStatus.valueOf(toStr);

            Application app = makeApp(from);
            given(applicationRepository.findById(10L)).willReturn(Optional.of(app));

            UpdateStatusRequest req = UpdateStatusRequest.builder()
                    .newStatus(to)
                    .notes("some notes")
                    .build();

            assertThatThrownBy(() -> adminApplicationService.updateStatus(10L, req, actingUser()))
                    .isInstanceOf(InvalidStatusTransitionException.class);

            // DB khong duoc thay doi
            verify(applicationRepository, never()).save(any());
            verify(historyRepository, never()).save(any());
            // Trang thai giu nguyen
            assertThat(app.getStatus()).isEqualTo(from);
        }

        @Test
        @DisplayName("REJECTED requires notes — throws BusinessException when notes blank")
        void rejectedRequiresNotes() {
            Application app = makeApp(ApplicationStatus.PROCESSING);
            given(applicationRepository.findById(10L)).willReturn(Optional.of(app));

            UpdateStatusRequest req = UpdateStatusRequest.builder()
                    .newStatus(ApplicationStatus.REJECTED)
                    .notes("")   // blank notes
                    .build();

            assertThatThrownBy(() -> adminApplicationService.updateStatus(10L, req, actingUser()))
                    .isInstanceOf(BusinessException.class);

            verify(applicationRepository, never()).save(any());
        }

        @Test
        @DisplayName("ADDITIONAL_REQUIRED requires notes — throws BusinessException when notes null")
        void additionalRequiredNeedsNotes() {
            Application app = makeApp(ApplicationStatus.PROCESSING);
            given(applicationRepository.findById(10L)).willReturn(Optional.of(app));

            UpdateStatusRequest req = UpdateStatusRequest.builder()
                    .newStatus(ApplicationStatus.ADDITIONAL_REQUIRED)
                    .notes(null)
                    .build();

            assertThatThrownBy(() -> adminApplicationService.updateStatus(10L, req, actingUser()))
                    .isInstanceOf(BusinessException.class);

            verify(applicationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Application not found → ResourceNotFoundException")
        void appNotFound() {
            given(applicationRepository.findById(999L)).willReturn(Optional.empty());

            UpdateStatusRequest req = UpdateStatusRequest.builder()
                    .newStatus(ApplicationStatus.RECEIVED).build();

            assertThatThrownBy(() -> adminApplicationService.updateStatus(999L, req, actingUser()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Static helper: ApplicationStateMachine.getAllowedTransitions ──────────

    @Nested
    @DisplayName("ApplicationStateMachine.getAllowedTransitions — dropdown options for UI")
    class AllowedTransitionsHelper {

        @Test
        void submittedAllowsOnlyReceived() {
            Set<ApplicationStatus> allowed = ApplicationStateMachine.getAllowedTransitions(ApplicationStatus.SUBMITTED);
            assertThat(allowed).containsExactly(ApplicationStatus.RECEIVED);
        }

        @Test
        void processingAllowsThreeOptions() {
            Set<ApplicationStatus> allowed = ApplicationStateMachine.getAllowedTransitions(ApplicationStatus.PROCESSING);
            assertThat(allowed).containsExactlyInAnyOrder(
                    ApplicationStatus.APPROVED,
                    ApplicationStatus.REJECTED,
                    ApplicationStatus.ADDITIONAL_REQUIRED
            );
        }

        @Test
        void approvedHasNoAllowedTransitions() {
            Set<ApplicationStatus> allowed = ApplicationStateMachine.getAllowedTransitions(ApplicationStatus.APPROVED);
            assertThat(allowed).isEmpty();
        }

        @Test
        void rejectedHasNoAllowedTransitions() {
            Set<ApplicationStatus> allowed = ApplicationStateMachine.getAllowedTransitions(ApplicationStatus.REJECTED);
            assertThat(allowed).isEmpty();
        }
    }

    // ── countPending ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("countPending returns sum of SUBMITTED + RECEIVED")
    void countPending() {
        given(applicationRepository.countByStatusIn(
                List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.RECEIVED)))
                .willReturn(7L);

        long count = adminApplicationService.countPending();

        assertThat(count).isEqualTo(7L);
    }
}

