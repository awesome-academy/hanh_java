package com.psms.service;

import com.psms.config.EmailProperties;
import com.psms.enums.ApplicationStatus;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link EmailService}.
 *
 * <p>Verify 4 hành vi:
 * <ol>
 *   <li>Email được gửi khi {@code emailEnabled = true}</li>
 *   <li>Email KHÔNG gửi khi {@code emailEnabled = false} hoặc {@code toEmail = null}</li>
 *   <li>Lỗi khi gửi KHÔNG throw ra ngoài — tránh rollback business tx</li>
 *   <li>Đúng template được chọn cho từng {@link ApplicationStatus}</li>
 * </ol>
 *
 * <p><b>Lưu ý về @Async:</b>
 * Unit test không load Spring context → @Async không được proxy → method chạy synchronous.
 * Đây là behavior đúng cho unit test (test logic, không test threading).
 * Integration test với @SpringBootTest + @EnableAsync mới verify async threading.
 *
 * <p><b>Tại sao API mới nhận Strings thay vì Entities:</b>
 * Tránh {@code LazyInitializationException} — @Async chạy trên thread khác,
 * ngoài Hibernate transaction boundary. Caller extract values trong transaction trước khi gọi.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService")
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private TemplateEngine templateEngine;
    @Mock private EmailProperties emailProperties;

    @InjectMocks
    private EmailService emailService;

    // Pre-extracted String values (mô phỏng caller đã extract trong transaction)
    private static final String EMAIL    = "citizen@example.com";
    private static final String NAME     = "Nguyễn Văn A";
    private static final String APP_CODE = "HS-20260428-00001";
    private static final String SVC_NAME = "Cấp CCCD lần đầu";
    private static final String DEADLINE = "05/05/2026";
    private static final String SUBMITTED= "09:00 28/04/2026";

    @BeforeEach
    void setUp() {
        // Lenient: chỉ cần trong test "gửi thành công", không cần trong test "skip"
        lenient().when(emailProperties.getFrom()).thenReturn("noreply@psms.gov.vn");
        lenient().when(emailProperties.getFromName()).thenReturn("Cổng DVCQG");
    }

    // ── sendApplicationReceived ───────────────────────────────────────────────

    @Nested
    @DisplayName("sendApplicationReceived")
    class SendApplicationReceived {

        @Test
        @DisplayName("gửi email khi emailEnabled = true")
        void sendsEmail_whenEnabled() throws Exception {
            given(mailSender.createMimeMessage()).willReturn(mock(MimeMessage.class));
            given(templateEngine.process(eq("email/application-received"), any(IContext.class)))
                    .willReturn("<html>receipt</html>");

            emailService.sendApplicationReceived(EMAIL, NAME, true, APP_CODE, SVC_NAME, SUBMITTED, DEADLINE);

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("KHÔNG gửi khi emailEnabled = false")
        void skipsEmail_whenDisabled() {
            emailService.sendApplicationReceived(EMAIL, NAME, false, APP_CODE, SVC_NAME, SUBMITTED, DEADLINE);
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("KHÔNG gửi khi toEmail = null")
        void skipsEmail_whenEmailNull() {
            emailService.sendApplicationReceived(null, NAME, true, APP_CODE, SVC_NAME, SUBMITTED, DEADLINE);
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("KHÔNG throw khi mailSender ném exception — fail silently")
        void doesNotThrow_whenMailSenderFails() {
            given(mailSender.createMimeMessage()).willReturn(mock(MimeMessage.class));
            given(templateEngine.process(anyString(), any(IContext.class))).willReturn("<html></html>");
            doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                    .when(mailSender).send(any(MimeMessage.class));

            assertThatCode(() ->
                    emailService.sendApplicationReceived(EMAIL, NAME, true, APP_CODE, SVC_NAME, SUBMITTED, DEADLINE))
                    .doesNotThrowAnyException();
        }
    }

    // ── sendStatusUpdate ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendStatusUpdate")
    class SendStatusUpdate {

        @Test
        @DisplayName("chọn đúng template cho từng ApplicationStatus")
        void selectsCorrectTemplate_perStatus() {
            given(mailSender.createMimeMessage()).willReturn(mock(MimeMessage.class));
            given(templateEngine.process(anyString(), any(IContext.class))).willReturn("<html></html>");

            emailService.sendStatusUpdate(EMAIL, NAME, true, APP_CODE, SVC_NAME, DEADLINE, ApplicationStatus.APPROVED, "OK");
            verify(templateEngine).process(eq("email/approved"), any());

            emailService.sendStatusUpdate(EMAIL, NAME, true, APP_CODE, SVC_NAME, DEADLINE, ApplicationStatus.REJECTED, "Thiếu tài liệu");
            verify(templateEngine).process(eq("email/rejected"), any());

            emailService.sendStatusUpdate(EMAIL, NAME, true, APP_CODE, SVC_NAME, DEADLINE, ApplicationStatus.ADDITIONAL_REQUIRED, "Bổ sung CCCD");
            verify(templateEngine).process(eq("email/additional-required"), any());

            emailService.sendStatusUpdate(EMAIL, NAME, true, APP_CODE, SVC_NAME, DEADLINE, ApplicationStatus.PROCESSING, null);
            verify(templateEngine).process(eq("email/status-update"), any());
        }

        @Test
        @DisplayName("KHÔNG gửi khi emailEnabled = false")
        void skipsEmail_whenDisabled() {
            emailService.sendStatusUpdate(EMAIL, NAME, false, APP_CODE, SVC_NAME, DEADLINE, ApplicationStatus.APPROVED, null);
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("KHÔNG throw khi TemplateEngine fail")
        void doesNotThrow_whenTemplateEngineFails() {
            given(templateEngine.process(anyString(), any(IContext.class)))
                    .willThrow(new RuntimeException("Template not found"));

            assertThatCode(() ->
                    emailService.sendStatusUpdate(EMAIL, NAME, true, APP_CODE, SVC_NAME, DEADLINE, ApplicationStatus.APPROVED, null))
                    .doesNotThrowAnyException();
        }
    }
}

