package com.reajason.noone.server.project;

import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.project.dto.ProjectCreateRequest;
import com.reajason.noone.server.project.dto.ProjectMemberDTO;
import com.reajason.noone.server.project.dto.ProjectResponse;
import com.reajason.noone.server.project.dto.ProjectUpdateRequest;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class ProjectMapper {

    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    public abstract Project toEntity(ProjectCreateRequest request);

    @Mapping(target = "members", ignore = true)
    @Mapping(target = "startedAt", source = "startedAt", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "endedAt", source = "endedAt", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    public abstract void updateEntity(@MappingTarget Project project, ProjectUpdateRequest request);

    @Mapping(target = "status", expression = "java(project.getStatus().name())")
    @Mapping(target = "members", source = "members")
    public abstract ProjectResponse toResponse(Project project);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "username", source = "username")
    public abstract ProjectMemberDTO toMemberDTO(User user);
}
