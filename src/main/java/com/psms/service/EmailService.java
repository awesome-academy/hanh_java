package com.psms.service;

import com.psms.config.EmailProperties;
import com.psms.enums.ApplicationStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Gửi email bất đồng bộ (@Async) cho các sự kiện nghiệp vụ.
 *
 * <p><b>Design decisions:</b>
 * <ul>
 *   <li>{@code @Async("mailTaskExecutor")} — chạy trên thread pool riêng, không block request.</li>
 *   <li>Mọi lỗi được catch + log WARNING — không throw để tránh rollback business transaction.</li>
 *   <li>Chỉ gửi khi {@code emailEnabled = true} — tôn trọng cài đặt của người dùng.</li>
 *   <li>Public methods chỉ nhận <b>primitive Strings</b>, không nhận Entity/Proxy — tránh
 *       {@code LazyInitializationException} khi async thread chạy ngoài transaction boundary.</li>
 * </ul>
 *
 * <p><b>Caller responsibility:</b> Caller phải extract tất cả giá trị cần thiết từ entity
 * <em>trong transaction</em> trước khi gọi method này.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailProperties emailProperties;

    // ── Public API — chỉ nhận primitive/String, không nhận Entity ──────────────

    /**
     * Gửi email xác nhận nộp hồ sơ thành công.
     * Template: {@code email/application-received.html}
     *
     * <p>Caller phải truyền các giá trị đã extract trong transaction.
     *
     * @param toEmail         email của citizen
     * @param fullName        họ tên citizen
     * @param emailEnabled    citizen có bật nhận email không
     * @param applicationCode mã hồ sơ HS-YYYYMMDD-NNNNN
     * @param serviceName     tên dịch vụ
     * @param submittedAt     thời gian nộp đã format (HH:mm dd/MM/yyyy)
     * @param deadline        hạn xử lý đã format (dd/MM/yyyy), hoặc "—"
     */
    @Async("mailTaskExecutor")
    public void sendApplicationReceived(String toEmail, String fullName, boolean emailEnabled,
                                        String applicationCode, String serviceName,
                                        String submittedAt, String deadline) {
        if (!emailEnabled || toEmail == null || toEmail.isBlank()) return;

        Context ctx = new Context();
        ctx.setVariable("fullName", fullName);
        ctx.setVariable("applicationCode", applicationCode);
        ctx.setVariable("serviceName", serviceName);
        ctx.setVariable("submittedAt", submittedAt);
        ctx.setVariable("deadline", deadline);

        send(toEmail,
                "Xác nhận nộp hồ sơ — " + applicationCode,
                "email/application-received", ctx);
    }

    /**
     * Gửi email khi trạng thái hồ sơ thay đổi.
     * Template được chọn theo {@code newStatus}:
     * APPROVED → approved.html · REJECTED → rejected.html ·
     * ADDITIONAL_REQUIRED → additional-required.html · khác → status-update.html
     *
     * @param toEmail         email của citizen
     * @param fullName        họ tên citizen
     * @param emailEnabled    citizen có bật nhận email không
     * @param applicationCode mã hồ sơ
     * @param serviceName     tên dịch vụ
     * @param deadline        hạn xử lý đã format, hoặc "—"
     * @param newStatus       trạng thái mới
     * @param notes           ghi chú từ cán bộ (nullable)
     */
    @Async("mailTaskExecutor")
    public void sendStatusUpdate(String toEmail, String fullName, boolean emailEnabled,
                                 String applicationCode, String serviceName, String deadline,
                                 ApplicationStatus newStatus, String notes) {
        if (!emailEnabled || toEmail == null || toEmail.isBlank()) return;

        Context ctx = new Context();
        ctx.setVariable("fullName", fullName);
        ctx.setVariable("applicationCode", applicationCode);
        ctx.setVariable("serviceName", serviceName);
        ctx.setVariable("newStatusLabel", newStatus.getLabel());
        ctx.setVariable("notes", notes);
        ctx.setVariable("hasNotes", notes != null && !notes.isBlank());
        ctx.setVariable("deadline", deadline);

        send(toEmail, resolveSubject(applicationCode, newStatus),
                resolveTemplate(newStatus), ctx);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Core send: render template → build MimeMessage → gửi.
     * Catch mọi exception (checked + RuntimeException) — log WARN, không throw.
     */
    private void send(String to, String subject, String templateName, Context ctx) {
        try {
            String html = templateEngine.process(templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            // false = HTML only, không multipart — dùng MULTIPART_MODE_RELATED khi cần inline images
            MimeMessageHelper helper = new MimeMessageHelper(message, false,
                    StandardCharsets.UTF_8.name());

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.setFrom(new InternetAddress(
                    emailProperties.getFrom(),
                    emailProperties.getFromName(),
                    StandardCharsets.UTF_8.name()));

            mailSender.send(message);
            log.info("Email sent: to={} subject={}", to, subject);

        } catch (MessagingException | UnsupportedEncodingException e) {
            // SLF4J: exception là argument cuối → tự append stack trace vào log
            log.warn("Failed to send email to={} subject={}", to, subject, e);
        } catch (Exception e) {
            // MailSendException (RuntimeException), TemplateProcessingException, etc.
            // Không throw — tránh rollback business transaction
            log.warn("Unexpected error sending email to={} subject={}", to, subject, e);
        }
    }

    private String resolveTemplate(ApplicationStatus status) {
        return switch (status) {
            case APPROVED            -> "email/approved";
            case REJECTED            -> "email/rejected";
            case ADDITIONAL_REQUIRED -> "email/additional-required";
            default                  -> "email/status-update";
        };
    }

    private String resolveSubject(String appCode, ApplicationStatus status) {
        return switch (status) {
            case APPROVED            -> "Hồ sơ " + appCode + " đã được phê duyệt ✓";
            case REJECTED            -> "Hồ sơ " + appCode + " bị từ chối";
            case ADDITIONAL_REQUIRED -> "Hồ sơ " + appCode + " — Cần bổ sung tài liệu";
            default                  -> "Cập nhật trạng thái hồ sơ " + appCode;
        };
    }
}
