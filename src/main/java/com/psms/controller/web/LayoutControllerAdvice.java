package com.psms.controller.web;

import com.psms.service.AdminApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Inject pendingCount vao Model cho moi request /admin/**
 * de sidebar badge hien thi so ho so cho xu ly.
 *
 * @ControllerAdvice chi xu ly MVC controller (web/), khong anh huong REST controller.
 * basePackageClasses gioi han scope chi trong controller.web.
 */
@ControllerAdvice(basePackageClasses = LayoutControllerAdvice.class)
@RequiredArgsConstructor
public class LayoutControllerAdvice {

    private final AdminApplicationService adminApplicationService;

    /**
     * Them pendingCount (SUBMITTED + RECEIVED) vao model.
     * Chi chay khi request path bat dau /admin/ de tranh query khong can thiet.
     */
    @ModelAttribute("pendingCount")
    public long pendingCount(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/admin/")) {
            return adminApplicationService.countPending();
        }
        return 0L;
    }
}

