package com.psms.controller.client;

import com.psms.dto.request.ChangePasswordRequest;
import com.psms.dto.request.UpdateProfileRequest;
import com.psms.dto.response.ApiResponse;
import com.psms.dto.response.CitizenProfileResponse;
import com.psms.entity.User;
import com.psms.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller cho hồ sơ cá nhân công dân.
 *
 * <p>Tất cả endpoint yêu cầu role CITIZEN (enforce bởi SecurityConfig).
 */
@RestController
@RequestMapping("/api/client/profile")
@RequiredArgsConstructor
@Tag(name = "Client - Profile", description = "Hồ sơ cá nhân công dân")
public class ClientProfileController {

    private final ProfileService profileService;

    @Operation(
        summary = "Xem hồ sơ cá nhân",
        description = "Trả về thông tin User + Citizen của người đang đăng nhập"
    )
    @GetMapping
    public ResponseEntity<ApiResponse<CitizenProfileResponse>> getProfile(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                ApiResponse.success(profileService.getProfile(user.getId())));
    }

    @Operation(
        summary = "Cập nhật hồ sơ cá nhân",
        description = "Cập nhật fullName, email, phone, dateOfBirth, gender, address. Riêng nationalId KHÔNG được sửa"
    )
    @PutMapping
    public ResponseEntity<ApiResponse<CitizenProfileResponse>> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(profileService.updateProfile(user.getId(), request)));
    }

    @Operation(
        summary = "Đổi mật khẩu",
        description = "Validate: mật khẩu cũ đúng, mật khẩu mới ≥ 8 ký tự, xác nhận khớp"
    )
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(
        summary = "Cập nhật cài đặt email thông báo",
        description = "Bật/tắt nhận email khi có thay đổi trạng thái hồ sơ"
    )
    @PutMapping("/email-notifications")
    public ResponseEntity<ApiResponse<Void>> updateEmailNotif(
            @AuthenticationPrincipal User user,
            @RequestParam boolean enabled) {
        profileService.updateEmailNotifSetting(user.getId(), enabled);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

