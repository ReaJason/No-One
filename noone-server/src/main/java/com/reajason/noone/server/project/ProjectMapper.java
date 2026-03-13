package com.reajason.noone.server.project;

import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.project.dto.ProjectCreateRequest;
import com.reajason.noone.server.project.dto.ProjectMemberDTO;
import com.reajason.noone.server.project.dto.ProjectResponse;
import com.reajason.noone.server.project.dto.ProjectUpdateRequest;
import jakarta.annotation.Resource;
import org.mapstruct.*;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class ProjectMapper {

    @Resource
    protected UserRepository userRepository;

    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "status", source = "status", qualifiedByName = "toProjectStatus")
    @Mapping(target = "members", source = "memberIds", qualifiedByName = "toMembers")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    public abstract Project toEntity(ProjectCreateRequest request);

    @Mapping(target = "status", source = "status", qualifiedByName = "toProjectStatus")
    @Mapping(target = "members", source = "memberIds", qualifiedByName = "toMembers")
    @Mapping(target = "startedAt", source = "startedAt", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "endedAt", source = "endedAt", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    public abstract void updateEntity(@MappingTarget Project project, ProjectUpdateRequest request);

    @Mapping(target = "status", expression = "java(project.getStatus().name())")
    @Mapping(target = "members", source = "members")
    public abstract ProjectResponse toResponse(Project project);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "username", source = "username")
    public abstract ProjectMemberDTO toMemberDTO(User user);

    @Named("toProjectStatus")
    protected ProjectStatus toProjectStatus(String status) {
        if (status == null || status.isBlank())
            return null;
        return ProjectStatus.valueOf(status.toUpperCase());
    }

    @Named("toMembers")
    protected Set<User> toMembers(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty())
            return null;
        return memberIds.stream()
                .map(userRepository::getReferenceById)
                .collect(Collectors.toSet());
    }
}
