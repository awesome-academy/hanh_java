package com.psms.controller.web;

import com.psms.dto.request.LoginRequest;
import com.psms.dto.request.RegisterRequest;
import com.psms.enums.Gender;
import com.psms.exception.BusinessException;
import com.psms.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
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


/**
 * MVC controller xử lý POST request của auth pages — PRG Pattern.
 * <p><b>PRG Pattern:</b> Tất cả POST thành công đều redirect → tránh form resubmit khi F5.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthMvcController {

    private final AuthService authService;

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
     * Xử lý đăng nhập cổng quản trị — delegate hoàn toàn sang {@link AuthService#adminLogin}.
     *
     * <p>Flow (PRG):
     * <ol>
     *   <li>Validation fail → redirect với {@code ?error=true}</li>
     *   <li>Sai credentials / bị khóa → redirect với {@code ?error=true}</li>
     *   <li>Không đủ quyền admin → redirect với flash message lỗi</li>
     *   <li>Thành công → set {@code SecurityContext} vào session → redirect {@code /admin/dashboard}</li>
     * </ol>
     */
    @PostMapping("/admin/login")
    public String adminLogin(@Valid @ModelAttribute LoginRequest request,
                             BindingResult bindingResult,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "redirect:/admin/login?error=true";
        }

        try {
            Authentication auth = authService.adminLogin(request.getEmail(), request.getPassword());

            // Set SecurityContext vào session — Spring Security tự đọc lại ở request sau
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            return "redirect:/admin/dashboard";

        } catch (AccessDeniedException e) {
            // Tài khoản hợp lệ nhưng không đủ quyền admin (CITIZEN cố đăng nhập vào admin)
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/login?error=true";

        } catch (BadCredentialsException | DisabledException | LockedException e) {
            // Sai mật khẩu / tài khoản bị vô hiệu hoá / bị khoá
            return "redirect:/admin/login?error=true";
        }
    }
}

