package com.psms.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryResponse {

    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private boolean active;
    private boolean locked;
    private LocalDateTime createdAt;
    private Set<String> roles;
}

