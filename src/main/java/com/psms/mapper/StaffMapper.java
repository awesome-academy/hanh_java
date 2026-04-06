package com.psms.mapper;

import com.psms.dto.response.StaffSummaryResponse;
import com.psms.entity.Staff;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructCentralConfig.class)
public interface StaffMapper {

    @Mapping(target = "staffId",          source = "id")
    @Mapping(target = "userId",           source = "user.id")
    @Mapping(target = "fullName",         source = "user.fullName")
    @Mapping(target = "email",            source = "user.email")
    @Mapping(target = "departmentName",   source = "department.name")
    // activeApplicationCount cần query riêng — không có sẵn trong entity, để 0
    @Mapping(target = "activeApplicationCount", constant = "0L")
    StaffSummaryResponse toSummaryResponse(Staff staff);

    List<StaffSummaryResponse> toSummaryResponses(List<Staff> staffList);
}

