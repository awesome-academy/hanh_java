package com.psms.dto.request;

import com.psms.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "{validation.fullName.required}")
    @Size(max = 100, message = "{validation.fullName.size}")
    private String fullName;

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 180, message = "{validation.email.size}")
    private String email;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 8, max = 255, message = "{validation.password.size}")
    private String password;

    @Pattern(regexp = "^(\\+84|0)\\d{9}$", message = "{validation.phone.pattern}")
    @Size(max = 20, message = "{validation.phone.size}")
    private String phone;

    @NotBlank(message = "{validation.nationalId.required}")
    @Pattern(regexp = "^\\d{9}(\\d{3})?$", message = "{validation.nationalId.pattern}")
    @Size(max = 20, message = "{validation.nationalId.size}")
    private String nationalId;

    @PastOrPresent(message = "{validation.dateOfBirth.pastOrPresent}")
    private LocalDate dateOfBirth;

    private Gender gender;

    private String permanentAddress;

    @Size(max = 100, message = "{validation.ward.size}")
    private String ward;


    @Size(max = 100, message = "{validation.province.size}")
    private String province;
}
