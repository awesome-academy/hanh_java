package com.psms.util;

import org.springframework.data.domain.Page;

/**
 * Tính toán thông tin phân trang cho Thymeleaf template.
 * Dùng chung cho cả client và admin layout.
 */
public final class PaginationUtils {

    private PaginationUtils() {}

    public static PaginationInfo calculate(Page<?> page) {
        if (page.isEmpty()) {
            return new PaginationInfo(0, 0, 0, 0);
        }

        int cur = page.getNumber();
        long offset = (long) cur * page.getSize();

        return new PaginationInfo(
                Math.max(0, cur - 2),
                Math.min(page.getTotalPages() - 1, cur + 2),
                offset + 1,
                offset + page.getNumberOfElements()
        );
    }
}

