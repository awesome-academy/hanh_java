package com.psms.mapper;

import com.psms.dto.response.ApplicationDocumentResponse;
import com.psms.entity.ApplicationDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructCentralConfig.class)
public interface ApplicationDocumentMapper {

    @Mapping(target = "uploadedById",   source = "uploadedBy.id")
    @Mapping(target = "uploadedByName", source = "uploadedBy.fullName")
    @Mapping(target = "downloadUrl",    ignore = true) // set bởi controller sau khi map
    ApplicationDocumentResponse toResponse(ApplicationDocument document);

    List<ApplicationDocumentResponse> toResponses(List<ApplicationDocument> documents);
}

