package com.psms.dto.response;

import com.psms.enums.Gender;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitizenProfileResponse {

    private Long citizenId;
    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private String nationalId;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String permanentAddress;
    private String ward;
    private String district;
    private String province;
    private LocalDateTime joinedAt;
}

