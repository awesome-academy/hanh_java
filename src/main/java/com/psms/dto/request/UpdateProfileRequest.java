package com.psms.dto.request;

import com.psms.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateProfileRequest {

    @NotBlank(message = "{validation.fullName.required}")
    @Size(max = 100, message = "{validation.fullName.size}")
    private String fullName;

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 180, message = "{validation.email.size}")
    private String email;

    @Pattern(regexp = "^(\\+84|0)\\d{9}$", message = "{validation.phone.pattern}")
    @Size(max = 20, message = "{validation.phone.size}")
    private String phone;

    // HTML5 <input type="date"> gửi chuỗi ISO "yyyy-MM-dd" → Spring MVC parse thành LocalDate
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @PastOrPresent(message = "{validation.dateOfBirth.pastOrPresent}")
    private LocalDate dateOfBirth;

    private Gender gender;
    private String permanentAddress;

    @Size(max = 100, message = "{validation.ward.size}")
    private String ward;

    @Size(max = 100, message = "{validation.province.size}")
    private String province;
}
