package com.psms.mapper;

import com.psms.dto.response.ApplicationDetailResponse;
import com.psms.dto.response.ApplicationResponse;
import com.psms.dto.response.ApplicationStatusHistoryResponse;
import com.psms.entity.Application;
import com.psms.entity.ApplicationStatusHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructCentralConfig.class)
public interface ApplicationMapper {

    @Mapping(target = "citizenId", source = "citizen.id")
    @Mapping(target = "serviceTypeId", source = "serviceType.id")
    @Mapping(target = "serviceTypeName", source = "serviceType.name")
    ApplicationResponse toResponse(Application application);

    @Mapping(target = "citizenId", source = "citizen.id")
    @Mapping(target = "citizenFullName", source = "citizen.user.fullName")
    @Mapping(target = "serviceTypeId", source = "serviceType.id")
    @Mapping(target = "serviceTypeName", source = "serviceType.name")
    @Mapping(target = "departmentName", source = "serviceType.department.name")
    @Mapping(target = "fee", source = "serviceType.fee")
    @Mapping(target = "assignedStaffId", source = "assignedStaff.id")
    @Mapping(target = "assignedStaffName", source = "assignedStaff.fullName")
    @Mapping(target = "statusHistory", ignore = true)
    ApplicationDetailResponse toDetailResponse(Application application);

    @Mapping(target = "changedById", source = "changedBy.id")
    @Mapping(target = "changedByName", source = "changedBy.fullName")
    ApplicationStatusHistoryResponse toHistoryResponse(ApplicationStatusHistory history);

    List<ApplicationStatusHistoryResponse> toHistoryResponses(List<ApplicationStatusHistory> historyList);
}

