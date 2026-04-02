package com.psms.controller.web;

import com.psms.dto.request.LoginRequest;
import com.psms.dto.request.RegisterRequest;
import com.psms.enums.Gender;
import com.psms.exception.BusinessException;
import com.psms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * MVC controller xử lý POST request của auth pages — PRG Pattern.
 *
 * <p><b>Lưu ý phân chia:</b>
 * <ul>
 *   <li>{@code POST /auth/login} — Spring Security xử lý tự động qua
 *       {@code formLogin().loginProcessingUrl()}. Controller KHÔNG cần handle.</li>
 *   <li>{@code POST /auth/logout} — Spring Security xử lý tự động qua
 *       {@code logout().logoutUrl()}. Controller KHÔNG cần handle.</li>
 *   <li>{@code POST /auth/register} — Controller xử lý (Spring Security không biết register).</li>
 *   <li>{@code POST /admin/login} — Controller xử lý thêm role-check (STAFF+).</li>
 * </ul>
 *
 * <p><b>PRG Pattern:</b> Tất cả POST thành công đều redirect → tránh form resubmit khi F5.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthMvcController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;

    // ----------------------------------------------------------------
    // POST /auth/register — #03-17
    // ----------------------------------------------------------------

    /**
     * Xử lý đăng ký tài khoản công dân.
     *
     * <p>Flow (PRG):
     * <ol>
     *   <li>Validation fail → return form (không redirect, giữ lại lỗi + dữ liệu)</li>
     *   <li>Email/CCCD trùng → return form với error message</li>
     *   <li>Thành công → redirect /auth/login + flash "Đăng ký thành công"</li>
     * </ol>
     */
    @PostMapping("/auth/register")
    public String register(@Valid @ModelAttribute("registerRequest") RegisterRequest request,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        // Trả lại form nếu có lỗi validation field-level
        if (bindingResult.hasErrors()) {
            model.addAttribute("genders", Gender.values());
            return "auth/register";
        }

        try {
            authService.register(request);
            // PRG: redirect với flash message
            redirectAttributes.addFlashAttribute("success",
                    "Đăng ký thành công! Vui lòng đăng nhập.");
            return "redirect:/auth/login";

        } catch (BusinessException e) {
            // Duplicate email/CCCD → hiển thị lỗi global trên form (không redirect)
            model.addAttribute("error", e.getMessage());
            model.addAttribute("genders", Gender.values());
            return "auth/register";
        }
    }

    // ----------------------------------------------------------------
    // POST /admin/login
    // ----------------------------------------------------------------

    /**
     * Xử lý đăng nhập cổng quản trị.
     *
     * <p>Điểm khác biệt so với {@code /auth/login} (Spring Security formLogin):
     * <ul>
     *   <li>Kiểm tra thêm role — chỉ STAFF/MANAGER/SUPER_ADMIN được qua</li>
     *   <li>Citizen cố đăng nhập → redirect với lỗi, không set SecurityContext</li>
     *   <li>Thành công → set SecurityContext vào session → redirect /admin/dashboard</li>
     * </ul>
     */
    @PostMapping("/admin/login")
    public String adminLogin(@Valid @ModelAttribute LoginRequest request,
                             BindingResult bindingResult,
                             HttpServletRequest httpRequest,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "redirect:/admin/login?error=true";
        }

        try {
            // Xác thực qua Spring Security AuthenticationManager
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()));

            // Kiểm tra role — chỉ cho STAFF/MANAGER/SUPER_ADMIN
            List<String> allowedRoles = List.of("ROLE_STAFF", "ROLE_MANAGER", "ROLE_SUPER_ADMIN");
            boolean hasAdminRole = auth.getAuthorities().stream()
                    .anyMatch(a -> allowedRoles.contains(a.getAuthority()));

            if (!hasAdminRole) {
                redirectAttributes.addFlashAttribute("error",
                        "Bạn không có quyền truy cập trang Admin.");
                return "redirect:/admin/login?error=true";
            }

            // Set SecurityContext vào session — Spring Security sẽ tự đọc lại ở request sau
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            log.info("Admin login: email={}, roles={}", request.getEmail(), auth.getAuthorities());
            return "redirect:/admin/dashboard";

        } catch (BadCredentialsException | DisabledException | LockedException e) {
            return "redirect:/admin/login?error=true";
        }
    }
}

