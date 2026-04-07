package com.psms.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utility format tiền tệ dùng chung toàn hệ thống.
 *
 * <p>Không dùng ThreadLocal — NumberFormat được tạo mới mỗi lần gọi.
 * Chi phí tạo object là O(1) và không đáng kể so với HTTP overhead.
 *
 * <p>Tách khỏi DTO để: (1) DTO là pure data, không chứa logic presentation;
 * (2) tránh ThreadLocal leak trong thread pool của servlet container.
 */
public final class MoneyUtils {

    private static final Locale VI_VN = Locale.of("vi", "VN");

    private MoneyUtils() {}

    /**
     * Format số tiền sang dạng "1.500.000 VNĐ", "Miễn phí", hoặc "—".
     *
     * @param fee số tiền (nullable)
     * @return chuỗi đã format
     */
    public static String formatFee(BigDecimal fee) {
        if (fee == null) return "—";
        if (fee.compareTo(BigDecimal.ZERO) == 0) return "Miễn phí";

        NumberFormat nf = NumberFormat.getNumberInstance(VI_VN);
        nf.setGroupingUsed(true);
        nf.setMaximumFractionDigits(0);
        return nf.format(fee) + " VNĐ";
    }
}

