package com.psms.mapper;

import com.psms.dto.response.ServiceTypeDetailResponse;
import com.psms.dto.response.ServiceTypeResponse;
import com.psms.entity.ServiceType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class)
public interface ServiceTypeMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "active", source = "active")
    ServiceTypeResponse toResponse(ServiceType serviceType);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "active", source = "active")
    ServiceTypeDetailResponse toDetailResponse(ServiceType serviceType);
}

