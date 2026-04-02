package com.psms.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceCategoryResponse {

    private Integer id;
    private String code;
    private String name;
    private String description;
    private String icon;
    private short sortOrder;
    private long serviceCount; // số DV active trong lĩnh vực — dùng cho category grid
}

