package com.psms.util;

import com.psms.enums.ApplicationStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * State machine cho vòng đời hồ sơ (Application).
 *
 * <p>Valid transitions:
 * <pre>
 *   SUBMITTED           → RECEIVED
 *   RECEIVED            → PROCESSING
 *   PROCESSING          → APPROVED | REJECTED | ADDITIONAL_REQUIRED
 *   ADDITIONAL_REQUIRED → SUBMITTED   (admin reset sau khi citizen bổ sung)
 * </pre>
 *
 * <p>Transition không hợp lệ không được phép — caller nên throw {@code InvalidStatusTransitionException}.
 */
public final class ApplicationStateMachine {

    private ApplicationStateMachine() {}

    /** Map: trạng thái hiện tại → tập trạng thái được phép chuyển sang */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> VALID_TRANSITIONS =
            new EnumMap<>(ApplicationStatus.class);

    static {
        VALID_TRANSITIONS.put(ApplicationStatus.SUBMITTED,
                Set.of(ApplicationStatus.RECEIVED));

        VALID_TRANSITIONS.put(ApplicationStatus.RECEIVED,
                Set.of(ApplicationStatus.PROCESSING));

        VALID_TRANSITIONS.put(ApplicationStatus.PROCESSING,
                Set.of(ApplicationStatus.APPROVED,
                        ApplicationStatus.REJECTED,
                        ApplicationStatus.ADDITIONAL_REQUIRED));

        // Admin reset về SUBMITTED sau khi citizen đã bổ sung tài liệu
        VALID_TRANSITIONS.put(ApplicationStatus.ADDITIONAL_REQUIRED,
                Set.of(ApplicationStatus.SUBMITTED));
    }

    /**
     * Kiểm tra một transition có hợp lệ không.
     *
     * @param from trạng thái hiện tại
     * @param to   trạng thái muốn chuyển sang
     * @return {@code true} nếu transition được phép
     */
    public static boolean isValidTransition(ApplicationStatus from, ApplicationStatus to) {
        Set<ApplicationStatus> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Lấy tập trạng thái có thể chuyển sang từ trạng thái hiện tại.
     * Dùng để render dropdown trong UI.
     *
     * @param current trạng thái hiện tại
     * @return tập trạng thái được phép (rỗng nếu là terminal state)
     */
    public static Set<ApplicationStatus> getAllowedTransitions(ApplicationStatus current) {
        return VALID_TRANSITIONS.getOrDefault(current, Set.of());
    }
}

