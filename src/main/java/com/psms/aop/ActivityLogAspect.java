package com.psms.aop;

import com.psms.annotation.LogActivity;
import com.psms.entity.User;
import com.psms.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * AOP Aspect ghi ActivityLog sau khi method service thực thi thành công.
 *
 * <p><b>Design decisions:</b>
 * <ul>
 *   <li>{@code @Around} thay vì {@code @AfterReturning}: cần truy cập return value để SpEL
 *       tính entityId từ {@code #result}.</li>
 *   <li>Log chỉ ghi khi method thành công (proceed() không throw) — nếu lỗi thì không log,
 *       tránh audit trail bị nhiễu bởi failed attempts.</li>
 *   <li>SpEL evaluation dùng {@code StandardEvaluationContext} với {@code #result} (return value)
 *       và {@code #p0..#pN} (positional args) — an toàn khi bytecode stripping.</li>
 *   <li>ActivityLogService dùng REQUIRES_NEW propagation → log commit độc lập với outer tx.</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ActivityLogAspect {

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private final ActivityLogService activityLogService;

    /**
     * Intercept tất cả method được đánh dấu {@link LogActivity}.
     * Method tiếp tục chạy bình thường; log được ghi sau khi method trả về thành công.
     */
    @Around("@annotation(logActivity)")
    public Object logAround(ProceedingJoinPoint pjp, LogActivity logActivity) throws Throwable {
        // ── Execute method trước ─────────────────────────────────────────────
        Object result = pjp.proceed(); // Nếu throw → exception propagate, log KHÔNG được ghi

        // ── Ghi log sau khi thành công ───────────────────────────────────────
        try {
            User actor       = extractCurrentUser();
            String ipAddress = extractIpAddress();
            String entityId  = evaluateEntityId(logActivity.entityIdSpEL(), pjp, result);
            String desc      = buildDescription(logActivity, entityId, pjp, result);

            activityLogService.log(
                    actor,
                    logActivity.action().name(),
                    logActivity.entityType().isEmpty() ? null : logActivity.entityType(),
                    entityId,
                    desc,
                    ipAddress,
                    extractUserAgent()
            );
        } catch (Exception ex) {
            // Log failure không được làm hỏng business flow
            log.warn("ActivityLogAspect: failed to write log for action={}: {}",
                    logActivity.action(), ex.getMessage());
        }

        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Lấy User đang đăng nhập từ SecurityContextHolder.
     * Trả về null nếu không có authentication (e.g. scheduled job).
     */
    private User extractCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return (principal instanceof User u) ? u : null;
    }

    /**
     * Lấy IP address thực của client — xử lý reverse proxy / load balancer.
     *
     * <p>Thứ tự ưu tiên:
     * <ol>
     *   <li>{@code X-Forwarded-For} — tiêu chuẩn de-facto cho proxy</li>
     *   <li>{@code X-Real-IP} — Nginx convention</li>
     *   <li>{@code RemoteAddr} — kết nối trực tiếp</li>
     * </ol>
     */
    private String extractIpAddress() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;

        HttpServletRequest req = attrs.getRequest();
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For có thể chứa chuỗi IP: "client, proxy1, proxy2"
            return xff.split(",")[0].trim();
        }
        String xri = req.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();

        return req.getRemoteAddr();
    }

    private String extractUserAgent() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        return attrs.getRequest().getHeader("User-Agent");
    }

    /**
     * Evaluate SpEL expression để lấy entityId.
     *
     * <p>Context variables:
     * <ul>
     *   <li>{@code #result} — return value của method annotated</li>
     *   <li>{@code #p0..#pN} — positional method arguments</li>
     * </ul>
     *
     * @return entityId dạng String, hoặc null nếu không có SpEL expression
     */
    private String evaluateEntityId(String spel, ProceedingJoinPoint pjp, Object result) {
        if (spel == null || spel.isBlank()) return null;

        try {
            EvaluationContext context = buildSpelContext(pjp, result);
            Expression expr = SPEL_PARSER.parseExpression(spel);
            Object val = expr.getValue(context);
            return val != null ? val.toString() : null;
        } catch (Exception ex) {
            log.debug("ActivityLogAspect: SpEL eval failed for '{}': {}", spel, ex.getMessage());
            return null;
        }
    }

    /**
     * Xây dựng SpEL EvaluationContext với #result và #p0..#pN.
     */
    private EvaluationContext buildSpelContext(ProceedingJoinPoint pjp, Object result) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("result", result);

        Object[] args = pjp.getArgs();
        for (int i = 0; i < args.length; i++) {
            ctx.setVariable("p" + i, args[i]);
        }

        // Thêm tên param nếu có (bytecode debug info)
        try {
            Method method = ((MethodSignature) pjp.getSignature()).getMethod();
            var discoverer = new org.springframework.core.StandardReflectionParameterNameDiscoverer();
            String[] names = discoverer.getParameterNames(method);
            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    ctx.setVariable(names[i], args[i]);
                }
            }
        } catch (Exception ignored) { /* fallback to #p0..#pN */ }

        return ctx;
    }

    /**
     * Sinh description từ annotation hoặc auto-generate từ action + entityId.
     *
     * <p>Nếu {@code annotation.description()} là một SpEL expression hợp lệ (ví dụ
     * {@code "'Tạo phòng ban ' + #result.name"}), Aspect sẽ evaluate nó với context
     * có {@code #result} và {@code #p0..#pN}. Nếu evaluate thất bại (không phải SpEL
     * hoặc lỗi) thì trả về raw string gốc — backward compatible với các plain-string cũ.
     */
    private String buildDescription(LogActivity annotation, String entityId,
                                    ProceedingJoinPoint pjp, Object result) {
        if (!annotation.description().isEmpty()) {
            try {
                EvaluationContext context = buildSpelContext(pjp, result);
                Expression expr = SPEL_PARSER.parseExpression(annotation.description());
                Object val = expr.getValue(context);
                return val != null ? val.toString() : annotation.description();
            } catch (Exception ex) {
                // Không phải SpEL → trả về raw string gốc (backward compat)
                return annotation.description();
            }
        }
        // Auto-generate khi không có description: "ACTION_NAME [entityType] id=X"
        StringBuilder sb = new StringBuilder(annotation.action().name());
        if (!annotation.entityType().isEmpty()) {
            sb.append(" [").append(annotation.entityType()).append("]");
        }
        if (entityId != null) {
            sb.append(" id=").append(entityId);
        }
        return sb.toString();
    }
}

