package com.psms.service;

import com.psms.entity.Application;
import com.psms.entity.ApplicationDocument;
import com.psms.entity.ApplicationStatusHistory;
import com.psms.entity.Citizen;
import com.psms.entity.Role;
import com.psms.entity.User;
import com.psms.enums.ApplicationStatus;
import com.psms.enums.RoleName;
import com.psms.enums.ValidationStatus;
import com.psms.exception.BusinessException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.mapper.ApplicationDocumentMapper;
import com.psms.repository.ApplicationDocumentRepository;
import com.psms.repository.ApplicationRepository;
import com.psms.repository.ApplicationStatusHistoryRepository;
import com.psms.repository.CitizenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService")
class DocumentServiceTest {

    @Mock ApplicationDocumentRepository documentRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationStatusHistoryRepository historyRepository;
    @Mock CitizenRepository citizenRepository;
    @Mock ApplicationDocumentMapper documentMapper;
    @Mock FileStorageService fileStorageService;

    @InjectMocks DocumentService documentService;

    private User citizenUser;
    private Citizen citizen;
    private Application app;

    @BeforeEach
    void setUp() {
        citizenUser = new User();
        citizenUser.setId(1L);
        citizenUser.setFullName("Nguyễn Văn A");

        citizen = new Citizen();
        citizen.setId(10L);
        citizen.setUser(citizenUser);

        app = Application.builder()
                .citizen(citizen)
                .status(ApplicationStatus.ADDITIONAL_REQUIRED)
                .build();
        app.setId(100L);
    }

    // ─── Helper: tạo User với role cụ thể ────────────────────────────────

    private User userWithRole(Long id, RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(id);
        user.setRoles(Set.of(role));
        return user;
    }

    // ─── #08-14: Validate file type + size ────────────────────────────

    @Nested
    @DisplayName("#08-14 File validation")
    class FileValidation {

        @Test
        @DisplayName("PDF 5MB → store() được gọi, không throw")
        void validPdf_storesCalled() {
            MockMultipartFile pdf = new MockMultipartFile(
                    "files", "test.pdf", "application/pdf", new byte[5 * 1024 * 1024]);

            given(fileStorageService.store(pdf, 100L)).willReturn("100/uuid_test.pdf");

            assertThatNoException().isThrownBy(() ->
                    documentService.saveDocuments(app, List.of(pdf), citizenUser, false));

            verify(fileStorageService).store(pdf, 100L);
            verify(documentRepository).save(any(ApplicationDocument.class));
        }

        @Test
        @DisplayName("File .exe → FileStorageService throw BusinessException")
        void invalidType_throwsBusinessException() {
            MockMultipartFile exe = new MockMultipartFile(
                    "files", "virus.exe", "application/octet-stream", new byte[1024]);

            given(fileStorageService.store(exe, 100L))
                    .willThrow(new BusinessException("Định dạng file không được hỗ trợ: .exe"));

            assertThatThrownBy(() ->
                    documentService.saveDocuments(app, List.of(exe), citizenUser, false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Định dạng file không được hỗ trợ");
        }

        @Test
        @DisplayName("File > 10MB → FileStorageService throw BusinessException")
        void oversizeFile_throwsBusinessException() {
            MockMultipartFile bigFile = new MockMultipartFile(
                    "files", "big.pdf", "application/pdf", new byte[11 * 1024 * 1024]);

            given(fileStorageService.store(bigFile, 100L))
                    .willThrow(new BusinessException("File vượt quá dung lượng cho phép"));

            assertThatThrownBy(() ->
                    documentService.saveDocuments(app, List.of(bigFile), citizenUser, false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("vượt quá dung lượng");
        }

        @Test
        @DisplayName("Danh sách file null/empty → không gọi store(), không throw")
        void emptyFiles_noop() {
            assertThatNoException().isThrownBy(() ->
                    documentService.saveDocuments(app, null, citizenUser, false));
            assertThatNoException().isThrownBy(() ->
                    documentService.saveDocuments(app, List.of(), citizenUser, false));
            verify(fileStorageService, never()).store(any(), any());
        }
    }

    // ─── #08-15: Upload khi status sai ────────────────────────────────

    @Nested
    @DisplayName("#08-15 Upload bổ sung — kiểm tra status")
    class UploadSupplemental {

        @Test
        @DisplayName("Status ADDITIONAL_REQUIRED → upload thành công, auto-transition SUBMITTED")
        void additionalRequired_uploadSucceeds_andTransitionsToSubmitted() {
            MockMultipartFile pdf = new MockMultipartFile(
                    "files", "supp.pdf", "application/pdf", new byte[1024]);

            given(citizenRepository.findByUserId(1L)).willReturn(Optional.of(citizen));
            given(applicationRepository.findByIdAndCitizenId(100L, 10L)).willReturn(Optional.of(app));
            given(fileStorageService.store(pdf, 100L)).willReturn("100/supp.pdf");

            documentService.uploadSupplementalDocuments(100L, 1L, List.of(pdf));

            assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);

            ArgumentCaptor<ApplicationStatusHistory> historyCaptor =
                    ArgumentCaptor.forClass(ApplicationStatusHistory.class);
            verify(historyRepository).save(historyCaptor.capture());
            assertThat(historyCaptor.getValue().getOldStatus()).isEqualTo(ApplicationStatus.ADDITIONAL_REQUIRED);
            assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        }

        @Test
        @DisplayName("Status PROCESSING → throw BusinessException (không cho upload bổ sung)")
        void wrongStatus_throwsBusinessException() {
            Application processingApp = Application.builder()
                    .citizen(citizen)
                    .status(ApplicationStatus.PROCESSING)
                    .build();
            processingApp.setId(100L);

            MockMultipartFile pdf = new MockMultipartFile(
                    "files", "supp.pdf", "application/pdf", new byte[1024]);

            given(citizenRepository.findByUserId(1L)).willReturn(Optional.of(citizen));
            given(applicationRepository.findByIdAndCitizenId(100L, 10L)).willReturn(Optional.of(processingApp));

            assertThatThrownBy(() ->
                    documentService.uploadSupplementalDocuments(100L, 1L, List.of(pdf)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Yêu cầu bổ sung");
        }

        @Test
        @DisplayName("Hồ sơ của người khác → ResourceNotFoundException (IDOR)")
        void otherCitizenApp_throwsNotFound() {
            User anotherUser = new User();
            anotherUser.setId(2L);
            Citizen anotherCitizen = new Citizen();
            anotherCitizen.setId(99L);
            anotherCitizen.setUser(anotherUser);

            given(citizenRepository.findByUserId(2L)).willReturn(Optional.of(anotherCitizen));
            given(applicationRepository.findByIdAndCitizenId(100L, 99L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    documentService.uploadSupplementalDocuments(100L, 2L, List.of()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── #08-16: Document saved với đúng isResponse flag ──────────────

    @Test
    @DisplayName("#08-16 saveDocuments với isResponse=true → doc được lưu đúng flag")
    void saveDocuments_isResponseTrue_savedCorrectly() {
        MockMultipartFile pdf = new MockMultipartFile(
                "files", "response.pdf", "application/pdf", new byte[1024]);

        given(fileStorageService.store(pdf, 100L)).willReturn("100/uuid_response.pdf");

        documentService.saveDocuments(app, List.of(pdf), citizenUser, true);

        ArgumentCaptor<ApplicationDocument> docCaptor =
                ArgumentCaptor.forClass(ApplicationDocument.class);
        verify(documentRepository).save(docCaptor.capture());

        ApplicationDocument saved = docCaptor.getValue();
        assertThat(saved.isResponse()).isTrue();
        assertThat(saved.getValidationStatus()).isEqualTo(ValidationStatus.PENDING);
        assertThat(saved.getFilePath()).isEqualTo("100/uuid_response.pdf");
    }

    // ─── #08-23: deleteDocument() — permission matrix ─────────────────

    @Nested
    @DisplayName("#08-23 deleteDocument — kiểm tra phân quyền")
    class DeleteDocument {

        private Application submittedApp;
        private ApplicationDocument citizenDoc;
        private ApplicationDocument responseDoc;

        @BeforeEach
        void setUpDocs() {
            submittedApp = Application.builder()
                    .citizen(citizen)
                    .status(ApplicationStatus.SUBMITTED)
                    .build();
            submittedApp.setId(100L);

            // Tài liệu citizen nộp (is_response=false)
            citizenDoc = ApplicationDocument.builder()
                    .application(submittedApp)
                    .fileName("citizen.pdf")
                    .filePath("100/citizen.pdf")
                    .fileType("pdf")
                    .fileSize(1024L)
                    .uploadedBy(citizenUser)
                    .isResponse(false)
                    .build();
            citizenDoc.setId(1L);

            // Tài liệu phản hồi cán bộ (is_response=true)
            responseDoc = ApplicationDocument.builder()
                    .application(submittedApp)
                    .fileName("response.pdf")
                    .filePath("100/response.pdf")
                    .fileType("pdf")
                    .fileSize(1024L)
                    .uploadedBy(citizenUser)
                    .isResponse(true)
                    .build();
            responseDoc.setId(2L);
        }

        @Test
        @DisplayName("CITIZEN xóa doc của mình khi SUBMITTED → thành công, is_deleted=true")
        void citizen_deletesOwnDoc_whenSubmitted_succeeds() {
            User citizen_ = userWithRole(1L, RoleName.CITIZEN);
            given(documentRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(citizenDoc));
            given(citizenRepository.findByUserId(1L)).willReturn(Optional.of(citizen));

            documentService.deleteDocument(100L, 1L, citizen_);

            assertThat(citizenDoc.isDeleted()).isTrue();
            verify(documentRepository).save(citizenDoc);
        }

        @Test
        @DisplayName("CITIZEN xóa khi status=RECEIVED → BusinessException")
        void citizen_deleteWhenReceived_throwsBusinessException() {
            Application receivedApp = Application.builder()
                    .citizen(citizen)
                    .status(ApplicationStatus.RECEIVED)
                    .build();
            receivedApp.setId(100L);

            ApplicationDocument doc = ApplicationDocument.builder()
                    .application(receivedApp)
                    .isResponse(false)
                    .build();
            doc.setId(1L);

            User citizen_ = userWithRole(1L, RoleName.CITIZEN);
            given(documentRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(doc));
            // citizenRepository không được stub vì exception được throw từ status check trước

            assertThatThrownBy(() -> documentService.deleteDocument(100L, 1L, citizen_))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Đã nộp");
        }

        @Test
        @DisplayName("CITIZEN xóa response doc (is_response=true) → BusinessException")
        void citizen_deleteResponseDoc_throwsBusinessException() {
            User citizen_ = userWithRole(1L, RoleName.CITIZEN);
            given(documentRepository.findByIdAndIsDeletedFalse(2L)).willReturn(Optional.of(responseDoc));

            assertThatThrownBy(() -> documentService.deleteDocument(100L, 2L, citizen_))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("quyền");
        }

        @Test
        @DisplayName("STAFF xóa response doc do chính mình upload → thành công")
        void staff_deletesOwnResponseDoc_succeeds() {
            User staff = userWithRole(5L, RoleName.STAFF);

            // responseDoc phải do chính staff (id=5L) upload mới được xóa
            ApplicationDocument ownResponseDoc = ApplicationDocument.builder()
                    .application(submittedApp)
                    .fileName("response.pdf")
                    .filePath("100/response.pdf")
                    .fileType("pdf")
                    .fileSize(1024L)
                    .uploadedBy(staff)          // staff là người upload
                    .isResponse(true)
                    .build();
            ownResponseDoc.setId(2L);

            given(documentRepository.findByIdAndIsDeletedFalse(2L)).willReturn(Optional.of(ownResponseDoc));

            documentService.deleteDocument(100L, 2L, staff);

            assertThat(ownResponseDoc.isDeleted()).isTrue();
            verify(documentRepository).save(ownResponseDoc);
        }

        @Test
        @DisplayName("STAFF xóa response doc do người khác upload → BusinessException")
        void staff_deletesOtherStaffResponseDoc_throwsBusinessException() {
            User staff = userWithRole(5L, RoleName.STAFF);
            // responseDoc.uploadedBy = citizenUser (id=1L) ≠ staff (id=5L)
            given(documentRepository.findByIdAndIsDeletedFalse(2L)).willReturn(Optional.of(responseDoc));

            assertThatThrownBy(() -> documentService.deleteDocument(100L, 2L, staff))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("chính mình upload");
        }

        @Test
        @DisplayName("STAFF xóa citizen doc (is_response=false) → BusinessException")
        void staff_deletesCitizenDoc_throwsBusinessException() {
            User staff = userWithRole(5L, RoleName.STAFF);
            given(documentRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(citizenDoc));

            assertThatThrownBy(() -> documentService.deleteDocument(100L, 1L, staff))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("phản hồi");
        }

        @Test
        @DisplayName("MANAGER xóa citizen doc → thành công (full access)")
        void manager_deletesCitizenDoc_succeeds() {
            User manager = userWithRole(6L, RoleName.MANAGER);
            given(documentRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(citizenDoc));

            documentService.deleteDocument(100L, 1L, manager);

            assertThat(citizenDoc.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("SUPER_ADMIN xóa bất kỳ doc → thành công")
        void superAdmin_deletesAnyDoc_succeeds() {
            User superAdmin = userWithRole(7L, RoleName.SUPER_ADMIN);
            given(documentRepository.findByIdAndIsDeletedFalse(2L)).willReturn(Optional.of(responseDoc));

            documentService.deleteDocument(100L, 2L, superAdmin);

            assertThat(responseDoc.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("Doc không tồn tại / đã xóa → ResourceNotFoundException")
        void docNotFound_throwsResourceNotFoundException() {
            User superAdmin = userWithRole(7L, RoleName.SUPER_ADMIN);
            given(documentRepository.findByIdAndIsDeletedFalse(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.deleteDocument(100L, 999L, superAdmin))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Doc thuộc hồ sơ khác → BusinessException (IDOR protection)")
        void docBelongsToDifferentApp_throwsBusinessException() {
            // Doc thuộc appId=999, nhưng request đang truy cập với appId=100
            Application otherApp = Application.builder()
                    .citizen(citizen)
                    .status(ApplicationStatus.SUBMITTED)
                    .build();
            otherApp.setId(999L);

            ApplicationDocument otherDoc = ApplicationDocument.builder()
                    .application(otherApp)
                    .isResponse(false)
                    .build();
            otherDoc.setId(3L);

            User superAdmin = userWithRole(7L, RoleName.SUPER_ADMIN);
            given(documentRepository.findByIdAndIsDeletedFalse(3L)).willReturn(Optional.of(otherDoc));

            assertThatThrownBy(() -> documentService.deleteDocument(100L, 3L, superAdmin))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("không thuộc hồ sơ");
        }
    }
}
