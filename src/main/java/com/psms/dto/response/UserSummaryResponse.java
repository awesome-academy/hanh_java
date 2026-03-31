package com.psms.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
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

