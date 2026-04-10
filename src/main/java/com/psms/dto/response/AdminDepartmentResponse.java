package com.psms.dto.response;

import lombok.*;

/**
 * Response DTO cho admin quản lý phòng ban.
 * Bao gồm thông tin phòng ban + số cán bộ + số dịch vụ + thông tin trưởng phòng.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDepartmentResponse {

    private Long id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private String email;
    private boolean active;

    /** User ID của trưởng phòng */
    private Long leaderId;
    /** Họ tên trưởng phòng */
    private String leaderName;
    /** Email trưởng phòng */
    private String leaderEmail;

    /** Số cán bộ đang thuộc phòng ban này */
    private long staffCount;

    /** Số dịch vụ công đang thuộc phòng ban này */
    private long serviceCount;
}

