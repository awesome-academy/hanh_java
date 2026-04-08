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
@ToString
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
    private String province;
    private LocalDateTime joinedAt;

    public String getInitials() {
        if (fullName == null || fullName.trim().isEmpty()) return "??";
        String[] words = fullName.trim().split("\\s+");
        if (words.length == 1) {
            String w = words[0];
            return w.length() >= 2 ? w.substring(0, 2).toUpperCase() : (w + "?").toUpperCase();
        }
        String first = words[0].substring(0, 1);
        String second = words[1].substring(0, 1);
        return (first + second).toUpperCase();
    }
}

