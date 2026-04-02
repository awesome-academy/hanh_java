package com.psms.controller.web;

import com.psms.dto.request.RegisterRequest;
import com.psms.enums.Gender;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * MVC controller — phục vụ GET request cho các trang auth.
 * Chỉ render template, không xử lý business logic.
 */
@Controller
public class AuthViewController {

    /**
     * GET /auth/login — trang đăng nhập công dân.
     * Nếu đã login rồi → redirect về trang chủ.
     */
    @GetMapping("/auth/login")
    public String loginPage(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        return "auth/login";
    }

    /**
     * GET /auth/register — trang đăng ký công dân.
     * Inject registerRequest rỗng để Thymeleaf th:object binding hoạt động.
     * Inject genders cho dropdown giới tính.
     */
    @GetMapping("/auth/register")
    public String registerPage(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        model.addAttribute("registerRequest", new RegisterRequest());
        model.addAttribute("genders", Gender.values());
        return "auth/register";
    }

    /**
     * GET /admin/login — trang đăng nhập cổng quản trị.
     * Layout riêng (dark theme), chỉ STAFF/MANAGER/SUPER_ADMIN được dùng.
     */
    @GetMapping("/admin/login")
    public String adminLoginPage(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/admin/dashboard";
        }
        return "auth/admin-login";
    }
}
