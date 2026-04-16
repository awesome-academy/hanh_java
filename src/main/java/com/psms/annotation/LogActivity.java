package com.psms.annotation;

import java.lang.annotation.*;
import com.psms.enums.ActionType;

/**
 * Đánh dấu một method Service cần ghi ActivityLog sau khi thực thi thành công.
 *
 * <p><b>Design choice — ghi log ở Aspect thay vì inline trong service:</b>
 * <ul>
 *   <li>Separation of concerns: service không cần biết về logging cơ sở hạ tầng.</li>
 *   <li>Không cần inject ActivityLogService vào hàng chục service khác nhau.</li>
 *   <li>Log chỉ ghi khi method thành công (không throw exception).</li>
 * </ul>
 *
 * <p><b>SpEL expressions:</b>
 * <ul>
 *   <li>{@code #result.id} — truy cập field {@code id} của return value (dùng cho create)</li>
 *   <li>{@code #p0} — tham số đầu tiên của method (positional, an toàn với bytecode stripping)</li>
 *   <li>{@code #p0.toString()} — chuyển tham số thứ nhất thành String</li>
 * </ul>
 *
 * <p><b>Ví dụ sử dụng:</b>
 * <pre>{@code
 * @LogActivity(action = ActionType.CREATE_USER, entityType = "users", entityIdSpEL = "#result.id")
 * public AdminUserResponse createUser(CreateUserRequest request) { ... }
 *
 * @LogActivity(action = ActionType.UPDATE_STATUS, entityType = "applications", entityIdSpEL = "#p0")
 * public AdminApplicationResponse updateStatus(Long id, UpdateStatusRequest req, User user) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogActivity {

    /**
     * Loại hành động — phải là một trong các giá trị định nghĩa trong ActionType enum.
     * Ví dụ: ActionType.LOGIN, ActionType.CREATE_USER, ...
     */
    ActionType action();

    /**
     * Loại entity bị tác động.
     * Ví dụ: "applications", "users", "service_types", "departments", "staff".
     * Để trống nếu action không liên quan đến entity cụ thể (vd: LOGIN).
     */
    String entityType() default "";

    /**
     * SpEL expression để tính entityId từ return value hoặc tham số.
     * Các biến có sẵn:
     * <ul>
     *   <li>{@code #result} — return value của method</li>
     *   <li>{@code #p0}, {@code #p1}, ... — tham số theo thứ tự</li>
     * </ul>
     * Để trống nếu không cần entityId.
     */
    String entityIdSpEL() default "";

    /**
     * Mô tả hành động — hỗ trợ SpEL expression.
     * Nếu để trống, Aspect sẽ tự sinh description từ action + entityType + entityId.
     * Ví dụ: {@code "Cập nhật trạng thái hồ sơ #" + #p0}
     */
    String description() default "";
}

