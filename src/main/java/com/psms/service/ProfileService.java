package com.psms.service;

import com.psms.dto.request.ChangePasswordRequest;
import com.psms.dto.request.UpdateProfileRequest;
import com.psms.dto.response.CitizenProfileResponse;
import com.psms.entity.Citizen;
import com.psms.entity.User;
import com.psms.enums.ActionType;
import com.psms.exception.BusinessException;
import com.psms.exception.ResourceNotFoundException;
import com.psms.repository.CitizenRepository;
import com.psms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý nghiệp vụ hồ sơ cá nhân công dân.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final CitizenRepository citizenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ── GET profile ───────────────────────────────────────────────────────

    /**
     * Lấy thông tin hồ sơ cá nhân của citizen.
     *
     * @param userId ID của User đang đăng nhập
     * @return CitizenProfileResponse gồm thông tin User + Citizen
     */
    public CitizenProfileResponse getProfile(Long userId) {
        Citizen citizen = citizenRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin công dân"));
        User user = citizen.getUser();

        return CitizenProfileResponse.builder()
                .citizenId(citizen.getId())
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .nationalId(citizen.getNationalId())
                .dateOfBirth(citizen.getDateOfBirth())
                .gender(citizen.getGender())
                .permanentAddress(citizen.getPermanentAddress())
                .ward(citizen.getWard())
                .province(citizen.getProvince())
                .joinedAt(user.getCreatedAt())
                .build();
    }

    // ── UPDATE profile ────────────────────────────────────────────────────

    /**
     * Cập nhật thông tin hồ sơ cá nhân.
     * <p>Business rules:
     * <ul>
     *   <li>Cập nhật User: fullName, email, phone</li>
     *   <li>Cập nhật Citizen: dateOfBirth, gender, permanentAddress, ward, province</li>
     *   <li>Không cho phép cập nhật nationalId</li>
     *  <li>Email phải unique nếu có thay đổi</li>
     * </ul>
     * @param userId  ID của User đang đăng nhập
     * @param request DTO chứa thông tin cập nhật
     * @return CitizenProfileResponse sau khi cập nhật
     * @throws BusinessException nếu email đã tồn tại hoặc có lỗi validate khác
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.UPDATE_USER,
        entityType = "users",
        entityIdSpEL = "#result.id",
        description = "'Cập nhật thông tin tài khoản cá nhân: ' + #result.fullName + ' (' + #result.email + ')'")
    @Transactional
    public CitizenProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        Citizen citizen = citizenRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin công dân"));
        User user = citizen.getUser();

        // Cập nhật User fields
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());

        // Email unique check nếu đổi
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("Email đã được sử dụng bởi tài khoản khác");
            }
            user.setEmail(request.getEmail());
        }

        // Cập nhật Citizen fields (nationalId KHÔNG cập nhật)
        citizen.setDateOfBirth(request.getDateOfBirth());
        citizen.setGender(request.getGender());
        citizen.setPermanentAddress(request.getPermanentAddress());
        citizen.setWard(request.getWard());
        citizen.setProvince(request.getProvince());

        userRepository.save(user);
        citizenRepository.save(citizen);

        log.info("Profile updated: userId={}", userId);
        return getProfile(userId);
    }

    // ── CHANGE PASSWORD ───────────────────────────────────────────────────

    /**
     * Đổi mật khẩu.
     *
     * <p>Business rules:
     * <ul>
     *   <li>Mật khẩu cũ phải khớp với DB (BCrypt verify)</li>
     *   <li>Mật khẩu mới và xác nhận phải giống nhau</li>
     *   <li>Mật khẩu mới phải ≥ 8 ký tự </li>
     * </ul>
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.UPDATE_USER,
        entityType = "users",
        entityIdSpEL = "#userId",
        description = "Cập nhật mật khẩu tài khoản cá nhân"
    )
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("Mật khẩu cũ không đúng");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Mật khẩu xác nhận không khớp");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed: userId={}", userId);
    }

    // ── EMAIL NOTIFICATION SETTING ─────────────────────────────────────────

    /**
     * Bật/tắt nhận email thông báo.
     */
    @com.psms.annotation.LogActivity(
        action = ActionType.UPDATE_USER,
        entityType = "users",
        entityIdSpEL = "#userId",
        description = "'Cập nhật trạng thái nhận email thông báo: ' + (#enabled ? 'BẬT' : 'TẮT')"
    )
    @Transactional
    public void updateEmailNotifSetting(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
        user.setEmailNotifEnabled(enabled);
        userRepository.save(user);
        log.info("Email notif setting: userId={} enabled={}", userId, enabled);
    }

    /**
     * Lấy trạng thái bật/tắt nhận email thông báo.
     */
    public boolean getEmailNotifEnabled(Long userId) {
        return userRepository.findById(userId)
            .map(User::isEmailNotifEnabled)
            .orElse(false);
    }
}

