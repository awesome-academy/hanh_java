package com.psms.controller.web;

import com.psms.dto.request.ChangePasswordRequest;
import com.psms.dto.request.UpdateProfileRequest;
import com.psms.dto.response.CitizenProfileResponse;
import com.psms.entity.User;
import com.psms.exception.BusinessException;
import com.psms.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/**
 * MVC controller — render Thymeleaf pages cho Profile.
 *
 * <p>Tất cả route yêu cầu role CITIZEN.
 * PRG pattern: POST xử lý xong → redirect GET + flash message.
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('CITIZEN')")
public class ProfileViewController {

    private final ProfileService profileService;

    // ─── GET /profile ──────────────────────────────────────────────────────

    @GetMapping("/profile")
    public String showProfile(@AuthenticationPrincipal User user, Model model) {
        CitizenProfileResponse profile = profileService.getProfile(user.getId());
        model.addAttribute("profile", profile);
        model.addAttribute("updateReq", toUpdateRequest(profile));
        model.addAttribute("changePwReq", new ChangePasswordRequest());

        boolean emailNotifEnabled = profileService.getEmailNotifEnabled(user.getId());
        model.addAttribute("emailNotifEnabled", emailNotifEnabled);
        model.addAttribute("activeNav", "profile");
        return "client/profile";
    }

    // ─── POST /profile ─────────────────────────────────────────────────────

    @PostMapping("/profile")
    public String updateProfile(
        @AuthenticationPrincipal User user,
        @Valid @ModelAttribute("updateReq") UpdateProfileRequest request,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            CitizenProfileResponse profile = profileService.getProfile(user.getId());
            model.addAttribute("profile", profile);
            model.addAttribute("changePwReq", new ChangePasswordRequest());
            boolean emailNotifEnabled = profileService.getEmailNotifEnabled(user.getId());
            model.addAttribute("emailNotifEnabled", emailNotifEnabled);
            model.addAttribute("activeNav", "profile");
            return "client/profile";
        }
        try {
            profileService.updateProfile(user.getId(), request);
            ra.addFlashAttribute("success", "Cập nhật hồ sơ thành công!");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile";
    }

    // ─── POST /profile/change-password ─────────────────────────────────────

    @PostMapping("/profile/change-password")
    public String changePassword(
        @AuthenticationPrincipal User user,
        @Valid @ModelAttribute("changePwReq") ChangePasswordRequest request,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            CitizenProfileResponse profile = profileService.getProfile(user.getId());
            model.addAttribute("profile", profile);
            model.addAttribute("updateReq", toUpdateRequest(profile));
            boolean emailNotifEnabled = profileService.getEmailNotifEnabled(user.getId());
            model.addAttribute("emailNotifEnabled", emailNotifEnabled);
            model.addAttribute("activeNav", "profile");
            return "client/profile";
        }
        try {
            profileService.changePassword(user.getId(), request);
            ra.addFlashAttribute("success", "Đổi mật khẩu thành công!");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile";
    }

    // ─── POST /profile/email-notifications setting ──────────────────────────────────

    @PostMapping("/profile/email-notifications")
    public String updateEmailNotif(
        @AuthenticationPrincipal User user,
        @RequestParam boolean enabled,
        RedirectAttributes ra) {
        profileService.updateEmailNotifSetting(user.getId(), enabled);
        ra.addFlashAttribute("success",
            enabled ? "Đã bật nhận email thông báo." : "Đã tắt nhận email thông báo.");
        return "redirect:/profile";
    }

    // ─── Helper ───────────────────────────────────────────────────────────

    private UpdateProfileRequest toUpdateRequest(CitizenProfileResponse p) {
        return UpdateProfileRequest.builder()
            .fullName(p.getFullName())
            .email(p.getEmail())
            .phone(p.getPhone())
            .dateOfBirth(p.getDateOfBirth())
            .gender(p.getGender())
            .permanentAddress(p.getPermanentAddress())
            .ward(p.getWard())
            .province(p.getProvince())
            .build();
    }
}
