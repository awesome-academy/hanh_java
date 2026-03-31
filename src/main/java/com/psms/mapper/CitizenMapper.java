package com.psms.mapper;

import com.psms.dto.request.RegisterRequest;
import com.psms.dto.response.CitizenProfileResponse;
import com.psms.entity.Citizen;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class)
public interface CitizenMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Citizen toEntity(RegisterRequest request);

    @Mapping(target = "citizenId", source = "id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "joinedAt", source = "user.createdAt")
    CitizenProfileResponse toProfileResponse(Citizen citizen);
}

