package com.psms.util;

import org.springframework.data.domain.Page;

// PaginationUtils.java
public class PaginationUtils {
    public static PaginationInfo calculate(Page<?> page) {
        int pageStart = Math.max(0, page.getNumber() - 2);
        int pageEnd   = Math.min(page.getTotalPages() - 1, page.getNumber() + 2);

        long displayFrom;
        long displayTo;
        if (page.getNumberOfElements() == 0) {
            displayFrom = 0;
            displayTo = 0;
        } else {
            displayFrom = (long) page.getNumber() * page.getSize() + 1;
            displayTo = Math.min(
                (long) page.getNumber() * page.getSize() + page.getNumberOfElements(),
                page.getTotalElements()
            );
        }
        return new PaginationInfo(pageStart, pageEnd, displayFrom, displayTo);
    }
}

