package com.psms.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChangePasswordRequest {

    @NotBlank(message = "{validation.oldPassword.required}")
    private String oldPassword;

    @NotBlank(message = "{validation.password.new.required}")
    @Size(min = 8, max = 255, message = "{validation.password.new.size}")
    private String newPassword;

    @NotBlank(message = "{validation.password.confirm.required}")
    private String confirmPassword;
}
