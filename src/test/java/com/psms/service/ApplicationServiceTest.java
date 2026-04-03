package com.psms.service;

import com.psms.dto.request.SubmitApplicationRequest;
import com.psms.dto.response.ApplicationDetailResponse;
import com.psms.dto.response.ApplicationResponse;
import com.psms.dto.response.ApplicationStatusHistoryResponse;
import com.psms.entity.Application;
import com.psms.entity.ApplicationStatusHistory;
import com.psms.entity.Citizen;
import com.psms.entity.ServiceType;
import com.psms.entity.User;
import com.psms.enums.ApplicationStatus;
import com.psms.exception.ResourceNotFoundException;
import com.psms.mapper.ApplicationMapper;
import com.psms.repository.ApplicationRepository;
import com.psms.repository.ApplicationStatusHistoryRepository;
import com.psms.repository.CitizenRepository;
import com.psms.repository.ServiceTypeRepository;
import com.psms.util.ApplicationCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link ApplicationService}.
 *
 * <p>Dùng Mockito thuần — không load Spring context, không cần DB.
 * Covers: #06-08 (submit + mã HS format), #06-09 (ownership check).
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    // Pattern hợp lệ: HS-YYYYMMDD-NNNNN (ví dụ: HS-20260403-00001)
    private static final Pattern CODE_PATTERN = Pattern.compile("^HS-\\d{8}-\\d{5}$");
    private static final String VALID_CODE    = "HS-20260403-00001";

    @Mock ApplicationRepository              applicationRepository;
    @Mock ApplicationStatusHistoryRepository historyRepository;
    @Mock CitizenRepository                  citizenRepository;
    @Mock ServiceTypeRepository              serviceTypeRepository;
    @Mock ApplicationCodeGenerator           codeGenerator;
    @Mock ApplicationMapper                  applicationMapper;

    @InjectMocks
    ApplicationService applicationService;

    // ─── Fixtures ─────────────────────────────────────────────────────

    private User       user;
    private Citizen    citizen;
    private ServiceType serviceType;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("citizen@test.com");
        user.setFullName("Nguyen Van A");

        citizen = new Citizen();
        citizen.setUser(user);
        citizen.setNationalId("001090012345");

        serviceType = new ServiceType();
        serviceType.setId(10L);
        serviceType.setName("Đăng ký khai sinh");
        serviceType.setProcessingTimeDays((short) 5);
        serviceType.setFee(BigDecimal.ZERO);
        serviceType.setActive(true);
    }

    // ─── #06-08: submit() ─────────────────────────────────────────────

    @Nested
    @DisplayName("#06-08 · submit() — sinh mã HS và ghi history")
    class Submit {

        @Test
        @DisplayName("submit thành công → trả ApplicationResponse với mã HS đúng format")
        void submit_success_returnsResponseWithValidCode() {
            // Given
            SubmitApplicationRequest request = SubmitApplicationRequest.builder()
                    .serviceTypeId(10L)
                    .notes("Ghi chú test")
                    .build();

            given(citizenRepository.findByUserId(1L)).willReturn(Optional.of(citizen));
            given(serviceTypeRepository.findByIdAndIsActiveTrue(10L)).willReturn(Optional.of(serviceType));
            given(codeGenerator.generate()).willReturn(VALID_CODE);

            // Mock save trả lại đúng application đã build
            Application savedApp = Application.builder()
                    .applicationCode(VALID_CODE)
                    .citizen(citizen)
                    .serviceType(serviceType)
                    .status(ApplicationStatus.SUBMITTED)
                    .notes("Ghi chú test")
                    .build();
            given(applicationRepository.save(any(Application.class))).willReturn(savedApp);

            ApplicationResponse expectedResponse = ApplicationResponse.builder()
                    .applicationCode(VALID_CODE)
                    .status(ApplicationStatus.SUBMITTED)
                    .build();
            given(applicationMapper.toResponse(savedApp)).willReturn(expectedResponse);

            // When
            ApplicationResponse result = applicationService.submit(1L, request);

            // Then — mã HS đúng format HS-YYYYMMDD-NNNNN
            assertThat(result.getApplicationCode()).matches(CODE_PATTERN);
            assertThat(result.getApplicationCode()).isEqualTo(VALID_CODE);
            assertThat(result.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        }

        @Test
        @DisplayName("submit → Application được save với status SUBMITTED và đúng dữ liệu")
        void submit_savesApplicationWithCorrectData() {
            // Given
            SubmitApplicationRequest request = SubmitApplicationRequest.builder()
                    .serviceTypeId(10L)
                    .notes("Ghi chú")
                    .build();

            given(citizenRepository.findByUserId(1L)).willReturn(Optional.of(citizen));
            given(serviceTypeRepository.findByIdAndIsActiveTrue(10L)).willReturn(Optional.of(serviceType));
            given(codeGenerator.generate()).willReturn(VALID_CODE);

            Application savedApp = Application.builder()
                    .applicationCode(VALID_CODE)
                    .citizen(citizen)
                    .serviceType(serviceType)
                    .status(ApplicationStatus.SUBMITTED)
                    .build();
            given(applicationRepository.save(any())).willReturn(savedApp);
            given(applicationMapper.toResponse(any())).willReturn(new ApplicationResponse());

            // When
            applicationService.submit(1L, request);

            // Then — capture argument passed to save() và kiểm tra
            ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
            verify(applicationRepository).save(captor.capture());

            Application captured = captor.getValue();
            assertThat(captured.getApplicationCode()).isEqualTo(VALID_CODE);
            assertThat(captured.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
            assertThat(captured.getCitizen()).isEqualTo(citizen);
            assertThat(captured.getServiceType()).isEqualTo(serviceType);
            assertThat(captured.getNotes()).isEqualTo("Ghi chú");
            // Deadline = ngày nộp + processingTimeDays (5 ngày)
            assertThat(captured.getProcessingDeadline())
                    .isEqualTo(captured.getSubmittedAt().toLocalDate().plusDays(5));
        }

        @Test
        @DisplayName("submit → ghi history null→SUBMITTED ngay sau khi save Application")
        void submit_savesStatusHistory_nullToSubmitted() {
            // Given
            SubmitApplicationRequest request = SubmitApplicationRequest.builder()
                    .serviceTypeId(10L)
                    .build();

            given(citizenRepository.findByUserId(1L)).willReturn(Optional.of(citizen));
            given(serviceTypeRepository.findByIdAndIsActiveTrue(10L)).willReturn(Optional.of(serviceType));
            given(codeGenerator.generate()).willReturn(VALID_CODE);

            Application savedApp = Application.builder()
                    .applicationCode(VALID_CODE)
                    .citizen(citizen)
                    .serviceType(serviceType)
                    .status(ApplicationStatus.SUBMITTED)
                    .build();
            given(applicationRepository.save(any())).willReturn(savedApp);
            given(applicationMapper.toResponse(any())).willReturn(new ApplicationResponse());

            // When
            applicationService.submit(1L, request);

            // Then — capture history và kiểm tra null → SUBMITTED
            ArgumentCaptor<ApplicationStatusHistory> captor =
                    ArgumentCaptor.forClass(ApplicationStatusHistory.class);
            verify(historyRepository).save(captor.capture());

            ApplicationStatusHistory history = captor.getValue();
            assertThat(history.getOldStatus()).isNull();
            assertThat(history.getNewStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
            assertThat(history.getApplication()).isEqualTo(savedApp);
        }

        @Test
        @DisplayName("submit → citizen không tồn tại → ResourceNotFoundException")
        void submit_citizenNotFound_throwsException() {
            // Given
            given(citizenRepository.findByUserId(99L)).willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() ->
                    applicationService.submit(99L, SubmitApplicationRequest.builder()
                            .serviceTypeId(10L).build()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("công dân");
        }

        @Test
        @DisplayName("submit → dịch vụ không active → ResourceNotFoundException")
        void submit_serviceNotActive_throwsException() {
            // Given
            given(citizenRepository.findByUserId(1L)).willReturn(Optional.of(citizen));
            given(serviceTypeRepository.findByIdAndIsActiveTrue(99L)).willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() ->
                    applicationService.submit(1L, SubmitApplicationRequest.builder()
                            .serviceTypeId(99L).build()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Dịch vụ");
        }
    }

    // ─── #06-09: Ownership check ──────────────────────────────────────

    @Nested
    @DisplayName("#06-09 · Ownership check — citizen chỉ xem được HS của mình")
    class OwnershipCheck {

        private Application ownedApp;
        private ApplicationDetailResponse detailResponse;

        @BeforeEach
        void setUpOwnership() {
            ownedApp = Application.builder()
                    .applicationCode(VALID_CODE)
                    .citizen(citizen)
                    .serviceType(serviceType)
                    .status(ApplicationStatus.SUBMITTED)
                    .build();

            detailResponse = ApplicationDetailResponse.builder()
                    .applicationCode(VALID_CODE)
                    .status(ApplicationStatus.SUBMITTED)
                    .statusHistory(List.of())
                    .build();
        }

        @Test
        @DisplayName("findMyApplicationById — đúng citizenId → trả detail kèm statusHistory")
        void findById_correctOwner_returnsDetail() {
            // Given
            given(citizenRepository.findByUserId(1L)).willReturn(Optional.of(citizen));
            given(applicationRepository.findByIdAndCitizenId(100L, citizen.getId()))
                    .willReturn(Optional.of(ownedApp));
            given(applicationMapper.toDetailResponse(ownedApp)).willReturn(detailResponse);
            given(historyRepository.findByApplicationIdOrderByChangedAtAsc(100L))
                    .willReturn(List.of());
            given(applicationMapper.toHistoryResponses(List.of())).willReturn(List.of());

            // When
            ApplicationDetailResponse result = applicationService.findMyApplicationById(1L, 100L);

            // Then
            assertThat(result.getApplicationCode()).isEqualTo(VALID_CODE);
            assertThat(result.getStatusHistory()).isEmpty();
        }

        @Test
        @DisplayName("findMyApplicationById — sai citizenId (HS của người khác) → ResourceNotFoundException")
        void findById_wrongOwner_throwsException() {
            // Given — citizen của user 1 tồn tại
            Citizen anotherCitizen = new Citizen();
            anotherCitizen.setUser(user);
            anotherCitizen.setNationalId("001090099999");

            given(citizenRepository.findByUserId(2L)).willReturn(Optional.of(anotherCitizen));
            // findByIdAndCitizenId với citizenId sai → empty
            given(applicationRepository.findByIdAndCitizenId(100L, anotherCitizen.getId()))
                    .willReturn(Optional.empty());

            // When / Then — tránh IDOR: trả 404 thay vì 403
            assertThatThrownBy(() -> applicationService.findMyApplicationById(2L, 100L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("không có quyền xem");
        }

        @Test
        @DisplayName("findMyApplicationById — timeline được gắn theo thứ tự changedAt ASC")
        void findById_timelineAttachedInOrder() {
            // Given
            ApplicationStatusHistoryResponse h1 = ApplicationStatusHistoryResponse.builder()
                    .newStatus(ApplicationStatus.SUBMITTED).build();
            ApplicationStatusHistoryResponse h2 = ApplicationStatusHistoryResponse.builder()
                    .newStatus(ApplicationStatus.RECEIVED).build();

            given(citizenRepository.findByUserId(1L)).willReturn(Optional.of(citizen));
            given(applicationRepository.findByIdAndCitizenId(100L, citizen.getId()))
                    .willReturn(Optional.of(ownedApp));
            given(applicationMapper.toDetailResponse(ownedApp)).willReturn(detailResponse);
            given(historyRepository.findByApplicationIdOrderByChangedAtAsc(100L))
                    .willReturn(List.of(new ApplicationStatusHistory(), new ApplicationStatusHistory()));
            given(applicationMapper.toHistoryResponses(anyList())).willReturn(List.of(h1, h2));

            // When
            ApplicationDetailResponse result = applicationService.findMyApplicationById(1L, 100L);

            // Then — timeline có 2 bước đúng thứ tự
            assertThat(result.getStatusHistory()).hasSize(2);
            assertThat(result.getStatusHistory().get(0).getNewStatus())
                    .isEqualTo(ApplicationStatus.SUBMITTED);
            assertThat(result.getStatusHistory().get(1).getNewStatus())
                    .isEqualTo(ApplicationStatus.RECEIVED);
        }

        @Test
        @DisplayName("findMyApplications — chỉ trả HS của citizen đang đăng nhập")
        void findMyApplications_onlyReturnsCitizenOwnApplications() {
            // Given
            ApplicationResponse appResponse = ApplicationResponse.builder()
                    .applicationCode(VALID_CODE)
                    .status(ApplicationStatus.SUBMITTED)
                    .build();
            Page<Application> page = new PageImpl<>(List.of(ownedApp));
            Page<ApplicationResponse> mappedPage = new PageImpl<>(List.of(appResponse));

            given(citizenRepository.findByUserId(1L)).willReturn(Optional.of(citizen));
            given(applicationRepository.findByCitizenIdWithStatus(
                    eq(citizen.getId()), isNull(), any(Pageable.class)))
                    .willReturn(page);
            given(applicationMapper.toResponse(ownedApp)).willReturn(appResponse);

            // When
            Page<ApplicationResponse> result =
                    applicationService.findMyApplications(1L, null, 0, 10);

            // Then — chỉ thấy HS của citizen 1, không thấy HS người khác
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getApplicationCode()).isEqualTo(VALID_CODE);

            // Verify: query được gọi với đúng citizenId
            verify(applicationRepository)
                    .findByCitizenIdWithStatus(eq(citizen.getId()), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("findMyApplications — filter theo status SUBMITTED")
        void findMyApplications_filterByStatus() {
            // Given
            Page<Application> emptyPage = Page.empty();
            given(citizenRepository.findByUserId(1L)).willReturn(Optional.of(citizen));
            given(applicationRepository.findByCitizenIdWithStatus(
                    eq(citizen.getId()), eq(ApplicationStatus.SUBMITTED), any(Pageable.class)))
                    .willReturn(emptyPage);

            // When
            Page<ApplicationResponse> result =
                    applicationService.findMyApplications(1L, ApplicationStatus.SUBMITTED, 0, 10);

            // Then — query được gọi với đúng status filter
            verify(applicationRepository)
                    .findByCitizenIdWithStatus(
                            eq(citizen.getId()),
                            eq(ApplicationStatus.SUBMITTED),
                            any(Pageable.class));
        }

        @Test
        @DisplayName("findMyApplications — citizen không tồn tại → ResourceNotFoundException")
        void findMyApplications_citizenNotFound_throwsException() {
            // Given
            given(citizenRepository.findByUserId(99L)).willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() ->
                    applicationService.findMyApplications(99L, null, 0, 10))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("công dân");
        }
    }
}

