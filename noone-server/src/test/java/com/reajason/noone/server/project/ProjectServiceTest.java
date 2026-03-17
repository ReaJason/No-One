package com.reajason.noone.server.project;

import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.api.ResourceNotFoundException;
import com.reajason.noone.server.config.AuthorizationService;
import com.reajason.noone.server.project.dto.ProjectCreateRequest;
import com.reajason.noone.server.project.dto.ProjectQueryRequest;
import com.reajason.noone.server.project.dto.ProjectResponse;
import com.reajason.noone.server.project.dto.ProjectUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private ProjectService projectService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @Captor
    private ArgumentCaptor<ProjectCreateRequest> createRequestCaptor;

    @Captor
    private ArgumentCaptor<ProjectUpdateRequest> updateRequestCaptor;

    @Test
    void shouldCreateProjectWithMembersAndArchivedAt() {
        ProjectCreateRequest request = createRequest("new-project", "NEW-PROJECT");
        request.setStatus(ProjectStatus.ARCHIVED);
        request.setMemberIds(Set.of(1L, 2L));

        Project entity = buildProject(null, "new-project", "NEW-PROJECT");
        entity.setStatus(ProjectStatus.ARCHIVED);
        Project saved = buildProject(1L, "new-project", "NEW-PROJECT");
        saved.setStatus(ProjectStatus.ARCHIVED);
        Project stored = buildProject(1L, "new-project", "NEW-PROJECT");
        stored.setStatus(ProjectStatus.ARCHIVED);
        stored.setArchivedAt(LocalDateTime.of(2025, 1, 2, 12, 0));
        ProjectResponse expectedResponse = buildResponse(1L, "new-project", "NEW-PROJECT", "ARCHIVED");
        expectedResponse.setArchivedAt(stored.getArchivedAt());

        User alice = buildUser(1L, "alice");
        User bob = buildUser(2L, "bob");

        when(projectRepository.existsByNameAndDeletedFalse("new-project")).thenReturn(false);
        when(projectRepository.existsByCodeAndDeletedFalse("NEW-PROJECT")).thenReturn(false);
        when(projectMapper.toEntity(any(ProjectCreateRequest.class))).thenReturn(entity);
        when(userRepository.getReferenceById(1L)).thenReturn(alice);
        when(userRepository.getReferenceById(2L)).thenReturn(bob);
        when(projectRepository.save(entity)).thenReturn(saved);
        when(projectRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(stored));
        when(projectMapper.toResponse(stored)).thenReturn(expectedResponse);

        ProjectResponse response = projectService.create(request);

        assertThat(response).isEqualTo(expectedResponse);
        verify(projectMapper).toEntity(createRequestCaptor.capture());
        assertThat(createRequestCaptor.getValue()).isSameAs(request);
        assertThat(entity.getMembers()).containsExactlyInAnyOrder(alice, bob);
        assertThat(entity.getArchivedAt()).isNotNull();
        verify(projectRepository).save(entity);
    }

    @Test
    void shouldThrowWhenCreatingDuplicateName() {
        when(projectRepository.existsByNameAndDeletedFalse("duplicate")).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(createRequest("duplicate", "CODE-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project name already exists：duplicate");

        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenCreatingDuplicateCode() {
        when(projectRepository.existsByNameAndDeletedFalse("project-a")).thenReturn(false);
        when(projectRepository.existsByCodeAndDeletedFalse("DUPLICATE")).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(createRequest("project-a", "DUPLICATE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project code already exists：DUPLICATE");

        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldGetProjectById() {
        Project stored = buildProject(10L, "get-test", "GET-TEST");
        ProjectResponse expectedResponse = buildResponse(10L, "get-test", "GET-TEST", "ACTIVE");

        when(projectRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(stored));
        when(projectMapper.toResponse(stored)).thenReturn(expectedResponse);

        ProjectResponse found = projectService.getById(10L);

        assertThat(found).isEqualTo(expectedResponse);
    }

    @Test
    void shouldThrowWhenGettingNonExistentProject() {
        when(projectRepository.findByIdAndDeletedFalse(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(99999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project not found：99999");
    }

    @Test
    void shouldUpdateProjectMembersAndArchivedAt() {
        Project stored = buildProject(20L, "before-update", "BEFORE");
        ProjectResponse expectedResponse = buildResponse(20L, "after-update", "AFTER", "ARCHIVED");

        User alice = buildUser(1L, "alice");
        User bob = buildUser(2L, "bob");

        when(projectRepository.findByIdAndDeletedFalse(20L))
                .thenReturn(Optional.of(stored), Optional.of(stored));
        when(projectRepository.existsByNameAndIdNotAndDeletedFalse("after-update", 20L)).thenReturn(false);
        when(projectRepository.existsByCodeAndIdNotAndDeletedFalse("AFTER", 20L)).thenReturn(false);
        doAnswer(invocation -> {
            Project target = invocation.getArgument(0);
            ProjectUpdateRequest updateRequest = invocation.getArgument(1);
            target.setName(updateRequest.getName());
            target.setCode(updateRequest.getCode());
            target.setStatus(ProjectStatus.valueOf(updateRequest.getStatus()));
            target.setRemark(updateRequest.getRemark());
            return null;
        }).when(projectMapper).updateEntity(eq(stored), any(ProjectUpdateRequest.class));
        when(userRepository.getReferenceById(1L)).thenReturn(alice);
        when(userRepository.getReferenceById(2L)).thenReturn(bob);
        when(projectRepository.save(stored)).thenReturn(stored);
        when(projectMapper.toResponse(stored)).thenReturn(expectedResponse);

        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setName("after-update");
        request.setCode("AFTER");
        request.setStatus("ARCHIVED");
        request.setRemark("archived for audit");
        request.setMemberIds(Set.of(1L, 2L));

        ProjectResponse updated = projectService.update(20L, request);

        assertThat(updated).isEqualTo(expectedResponse);
        verify(projectMapper).updateEntity(eq(stored), updateRequestCaptor.capture());
        assertThat(updateRequestCaptor.getValue()).isSameAs(request);
        assertThat(stored.getName()).isEqualTo("after-update");
        assertThat(stored.getCode()).isEqualTo("AFTER");
        assertThat(stored.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        assertThat(stored.getMembers()).containsExactlyInAnyOrder(alice, bob);
        assertThat(stored.getArchivedAt()).isNotNull();
        verify(projectRepository).save(stored);
    }

    @Test
    void shouldClearArchivedAtWhenStatusBecomesActive() {
        Project stored = buildProject(21L, "archived-project", "ARCHIVED");
        stored.setStatus(ProjectStatus.ARCHIVED);
        stored.setArchivedAt(LocalDateTime.of(2025, 1, 3, 12, 0));
        ProjectResponse expectedResponse = buildResponse(21L, "archived-project", "ARCHIVED", "ACTIVE");

        when(projectRepository.findByIdAndDeletedFalse(21L))
                .thenReturn(Optional.of(stored), Optional.of(stored));
        doAnswer(invocation -> {
            Project target = invocation.getArgument(0);
            ProjectUpdateRequest updateRequest = invocation.getArgument(1);
            target.setStatus(ProjectStatus.valueOf(updateRequest.getStatus()));
            return null;
        }).when(projectMapper).updateEntity(eq(stored), any(ProjectUpdateRequest.class));
        when(projectRepository.save(stored)).thenReturn(stored);
        when(projectMapper.toResponse(stored)).thenReturn(expectedResponse);

        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setStatus("ACTIVE");

        ProjectResponse updated = projectService.update(21L, request);

        assertThat(updated).isEqualTo(expectedResponse);
        assertThat(stored.getArchivedAt()).isNull();
        verify(projectRepository, never()).existsByNameAndIdNotAndDeletedFalse(any(), any());
        verify(projectRepository, never()).existsByCodeAndIdNotAndDeletedFalse(any(), any());
    }

    @Test
    void shouldThrowWhenUpdatingToExistingName() {
        when(projectRepository.findByIdAndDeletedFalse(30L))
                .thenReturn(Optional.of(buildProject(30L, "current-name", "CURRENT")));
        when(projectRepository.existsByNameAndIdNotAndDeletedFalse("existing-name", 30L)).thenReturn(true);

        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setName("existing-name");

        assertThatThrownBy(() -> projectService.update(30L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project name already exists：existing-name");

        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUpdatingToExistingCode() {
        when(projectRepository.findByIdAndDeletedFalse(31L))
                .thenReturn(Optional.of(buildProject(31L, "current-name", "CURRENT")));
        when(projectRepository.existsByCodeAndIdNotAndDeletedFalse("EXISTING", 31L)).thenReturn(true);

        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setCode("EXISTING");

        assertThatThrownBy(() -> projectService.update(31L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project code already exists：EXISTING");

        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentProject() {
        when(projectRepository.findByIdAndDeletedFalse(99999L)).thenReturn(Optional.empty());

        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setName("irrelevant");

        assertThatThrownBy(() -> projectService.update(99999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project not found：99999");
    }

    @Test
    void shouldDeleteProject() {
        Project stored = buildProject(40L, "to-delete", "TO-DELETE");
        when(projectRepository.findByIdAndDeletedFalse(40L)).thenReturn(Optional.of(stored));

        projectService.delete(40L);

        assertThat(stored.getDeleted()).isTrue();
        verify(projectRepository).save(stored);
        verify(projectRepository, never()).deleteById(any());
    }

    @Test
    void shouldThrowWhenDeletingNonExistentProject() {
        when(projectRepository.findByIdAndDeletedFalse(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.delete(99999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project not found：99999");

        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldQueryWithFiltersAndAscSort() {
        Project alpha = buildProject(50L, "alpha", "alpha-code");
        Project beta = buildProject(51L, "beta", "beta-code");
        ProjectResponse alphaResp = buildResponse(50L, "alpha", "alpha-code", "ACTIVE");
        ProjectResponse betaResp = buildResponse(51L, "beta", "beta-code", "ACTIVE");

        Page<Project> repositoryPage = new PageImpl<>(List.of(alpha, beta));
        when(projectRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(repositoryPage);
        when(projectMapper.toResponse(alpha)).thenReturn(alphaResp);
        when(projectMapper.toResponse(beta)).thenReturn(betaResp);
        when(authorizationService.getVisibleProjectIds()).thenReturn(Set.of(50L, 51L));

        ProjectQueryRequest query = new ProjectQueryRequest();
        query.setName("a");
        query.setCode("code");
        query.setStatus("ACTIVE");
        query.setCreatedAfter(LocalDateTime.of(2025, 1, 1, 0, 0));
        query.setCreatedBefore(LocalDateTime.of(2025, 12, 31, 23, 59));
        query.setPage(1);
        query.setPageSize(5);
        query.setSortBy("createdAt");
        query.setSortOrder("asc");

        Page<ProjectResponse> page = projectService.query(query);

        verify(projectRepository).findAll(any(Specification.class), pageableCaptor.capture());
        verify(authorizationService, never()).isAdmin();
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().toList())
                .extracting(order -> order.getProperty() + ":" + order.getDirection().name())
                .containsExactly("createdAt:ASC", "name:ASC");
        assertThat(page.getContent()).containsExactly(alphaResp, betaResp);
    }

    @Test
    void shouldQueryWithDefaultParametersForAdmin() {
        Page<Project> emptyPage = new PageImpl<>(List.of());
        when(projectRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);
        when(authorizationService.getVisibleProjectIds()).thenReturn(Set.of());
        when(authorizationService.isAdmin()).thenReturn(true);

        Page<ProjectResponse> page = projectService.query(new ProjectQueryRequest());

        verify(projectRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().toList())
                .extracting(order -> order.getProperty() + ":" + order.getDirection().name())
                .containsExactly("createdAt:DESC", "name:ASC");
        assertThat(page.getContent()).isEmpty();
    }

    private ProjectCreateRequest createRequest(String name, String code) {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setName(name);
        request.setCode(code);
        request.setStatus(ProjectStatus.ACTIVE);
        request.setBizName("Acme");
        request.setDescription("Project description");
        request.setStartedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        request.setEndedAt(LocalDateTime.of(2025, 12, 31, 0, 0));
        request.setRemark("Initial remark");
        return request;
    }

    private Project buildProject(Long id, String name, String code) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setCode(code);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setBizName("Acme");
        project.setDescription("Project description");
        project.setStartedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        project.setEndedAt(LocalDateTime.of(2025, 12, 31, 0, 0));
        project.setRemark("Initial remark");
        project.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        project.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 12, 0));
        project.setDeleted(Boolean.FALSE);
        return project;
    }

    private ProjectResponse buildResponse(Long id, String name, String code, String status) {
        ProjectResponse response = new ProjectResponse();
        response.setId(id);
        response.setName(name);
        response.setCode(code);
        response.setStatus(status);
        response.setBizName("Acme");
        response.setDescription("Project description");
        response.setStartedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        response.setEndedAt(LocalDateTime.of(2025, 12, 31, 0, 0));
        response.setRemark("Initial remark");
        response.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        response.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 12, 0));
        return response;
    }

    private User buildUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
