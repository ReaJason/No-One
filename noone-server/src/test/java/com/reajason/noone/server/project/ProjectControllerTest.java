package com.reajason.noone.server.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.user.*;
import com.reajason.noone.server.audit.AuditAction;
import com.reajason.noone.server.audit.AuditLogEntity;
import com.reajason.noone.server.audit.AuditLogRepository;
import com.reajason.noone.server.audit.AuditModule;
import com.reajason.noone.server.project.dto.ProjectCreateRequest;
import com.reajason.noone.server.project.dto.ProjectUpdateRequest;
import com.reajason.noone.server.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPGContainerConfiguration.class)
@ActiveProfiles("test")
@Transactional
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User currentUser;
    private User teammate;
    private String accessToken;

    @AfterEach
    void cleanupAuditLogs() {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.executeWithoutResult(status -> auditLogRepository.deleteAll());
    }

    @BeforeEach
    void setUp() {
        Permission createPerm = permissionRepository.save(Permission.builder().code("project:create").name("Create Project").build());
        Permission updatePerm = permissionRepository.save(Permission.builder().code("project:update").name("Update Project").build());
        Permission deletePerm = permissionRepository.save(Permission.builder().code("project:delete").name("Delete Project").build());
        Permission listPerm = permissionRepository.save(Permission.builder().code("project:list").name("List Projects").build());

        Role role = Role.builder().name("Project Manager").build();
        role.setPermissions(Set.of(createPerm, updatePerm, deletePerm, listPerm));
        roleRepository.save(role);

        currentUser = userRepository.save(buildUser("testuser", Set.of(role)));
        teammate = userRepository.save(buildUser("teammate", Set.of()));
        accessToken = createAccessToken(currentUser);
    }

    @Test
    void shouldCreateProjectAndReturn201() throws Exception {
        ProjectCreateRequest request = createRequest("new-project", "NEW-PROJECT");
        request.setStatus(ProjectStatus.ARCHIVED);
        request.setMemberIds(Set.of(currentUser.getId(), teammate.getId()));

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("new-project"))
                .andExpect(jsonPath("$.code").value("NEW-PROJECT"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        Project saved = findProjectByCode("NEW-PROJECT");
        assertThat(saved.getMembers())
                .extracting(User::getId)
                .containsExactlyInAnyOrder(currentUser.getId(), teammate.getId());
        assertThat(saved.getArchivedAt()).isNotNull();

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLogEntity log = logs.get(0);
        assertThat(log.getModule()).isEqualTo(AuditModule.PROJECT);
        assertThat(log.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(log.getTargetType()).isEqualTo("Project");
        assertThat(log.getTargetId()).isNotBlank();
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldReturn400WhenCreateRequestMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void shouldReturn400WhenDuplicateNameOnCreate() throws Exception {
        projectRepository.save(buildProject("duplicate", "DUP-1", Set.of(currentUser)));

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("duplicate", "DUP-2"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Project name already exists：duplicate"));
    }

    @Test
    void shouldReturn400WhenDuplicateCodeOnCreate() throws Exception {
        projectRepository.save(buildProject("existing", "DUP-CODE", Set.of(currentUser)));

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("fresh-name", "DUP-CODE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Project code already exists：DUP-CODE"));
    }

    @Test
    void shouldCreateProjectWhenDeletedProjectHasSameNameAndCode() throws Exception {
        Project deletedProject = buildProject("reusable", "REUSABLE", Set.of(currentUser));
        deletedProject.setDeleted(Boolean.TRUE);
        projectRepository.save(deletedProject);

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("reusable", "REUSABLE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("reusable"))
                .andExpect(jsonPath("$.code").value("REUSABLE"));
    }

    @Test
    void shouldGetProjectByIdWhenUserIsMember() throws Exception {
        Project saved = projectRepository.save(buildProject("member-project", "MEMBER", Set.of(currentUser)));

        mockMvc.perform(get("/api/projects/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("member-project"))
                .andExpect(jsonPath("$.code").value("MEMBER"));
    }

    @Test
    void shouldReturn403WhenProjectNotAccessible() throws Exception {
        User outsider = userRepository.save(buildUser("outsider", Set.of()));
        Project saved = projectRepository.save(buildProject("hidden-project", "HIDDEN", Set.of(outsider)));

        mockMvc.perform(get("/api/projects/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldUpdateProject() throws Exception {
        Project saved = projectRepository.save(buildProject("before-update", "BEFORE", Set.of(teammate)));

        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setName("after-update");
        request.setCode("AFTER");
        request.setStatus("ARCHIVED");
        request.setMemberIds(Set.of(currentUser.getId(), teammate.getId()));
        request.setRemark("archived");

        mockMvc.perform(put("/api/projects/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("after-update"))
                .andExpect(jsonPath("$.code").value("AFTER"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty())
                .andExpect(jsonPath("$.members.length()").value(2));

        Project updated = projectRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("after-update");
        assertThat(updated.getCode()).isEqualTo("AFTER");
        assertThat(updated.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        assertThat(updated.getRemark()).isEqualTo("archived");
        assertThat(updated.getMembers())
                .extracting(User::getId)
                .containsExactlyInAnyOrder(currentUser.getId(), teammate.getId());
        assertThat(updated.getArchivedAt()).isNotNull();

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLogEntity log = logs.get(0);
        assertThat(log.getModule()).isEqualTo(AuditModule.PROJECT);
        assertThat(log.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(log.getTargetType()).isEqualTo("Project");
        assertThat(log.getTargetId()).isEqualTo(String.valueOf(saved.getId()));
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldReturn400WhenUpdateDuplicateName() throws Exception {
        projectRepository.save(buildProject("existing-name", "EXISTING", Set.of(currentUser)));
        Project target = projectRepository.save(buildProject("to-update", "TARGET", Set.of(currentUser)));

        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setName("existing-name");

        mockMvc.perform(put("/api/projects/{id}", target.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Project name already exists：existing-name"));
    }

    @Test
    void shouldReturn400WhenUpdateDuplicateCode() throws Exception {
        projectRepository.save(buildProject("existing-name", "EXISTING", Set.of(currentUser)));
        Project target = projectRepository.save(buildProject("to-update", "TARGET", Set.of(currentUser)));

        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setCode("EXISTING");

        mockMvc.perform(put("/api/projects/{id}", target.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Project code already exists：EXISTING"));
    }

    @Test
    void shouldReturn404WhenUpdateNonExistentProject() throws Exception {
        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setName("irrelevant");

        mockMvc.perform(put("/api/projects/{id}", 99999)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Project not found：99999"));
    }

    @Test
    void shouldDeleteProjectAndReturn204() throws Exception {
        Project saved = projectRepository.save(buildProject("to-delete", "DELETE-ME", Set.of(currentUser)));

        mockMvc.perform(delete("/api/projects/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        Project deleted = projectRepository.findById(saved.getId()).orElseThrow();
        assertThat(deleted.getDeleted()).isTrue();

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLogEntity log = logs.get(0);
        assertThat(log.getModule()).isEqualTo(AuditModule.PROJECT);
        assertThat(log.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(log.getTargetType()).isEqualTo("Project");
        assertThat(log.getTargetId()).isEqualTo(String.valueOf(saved.getId()));
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldReturn404WhenDeleteNonExistentProject() throws Exception {
        mockMvc.perform(delete("/api/projects/{id}", 99999)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Project not found：99999"));
    }

    @Test
    void shouldQueryProjectsByCode() throws Exception {
        projectRepository.save(buildProject("alpha-project", "ALPHA", Set.of(currentUser)));
        projectRepository.save(buildProject("target-project", "TARGET-CODE", Set.of(currentUser)));

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("code", "TARGET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("target-project"))
                .andExpect(jsonPath("$.content[0].code").value("TARGET-CODE"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void shouldOnlyReturnVisibleProjectsInQuery() throws Exception {
        User outsider = userRepository.save(buildUser("other-member", Set.of()));
        projectRepository.save(buildProject("visible-project", "VISIBLE", Set.of(currentUser)));
        projectRepository.save(buildProject("hidden-project", "HIDDEN", Set.of(outsider)));

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("visible-project"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void shouldQueryWithPaginationAndSort() throws Exception {
        for (int i = 1; i <= 5; i++) {
            projectRepository.save(buildProject("project-" + i, "CODE-" + i, Set.of(currentUser)));
        }

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("page", "0")
                        .param("pageSize", "2")
                        .param("sortBy", "name")
                        .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("project-1"))
                .andExpect(jsonPath("$.content[1].name").value("project-2"))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WhenNoPermissionToCreate() throws Exception {
        User noPermUser = userRepository.save(buildUser("noperm", Set.of(roleRepository.save(Role.builder().name("No Perm Role").build()))));
        String noPermToken = createAccessToken(noPermUser);

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + noPermToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("blocked", "BLOCKED"))))
                .andExpect(status().isForbidden());
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
        request.setMemberIds(Set.of(currentUser.getId()));
        return request;
    }

    private Project buildProject(String name, String code, Set<User> members) {
        Project project = new Project();
        project.setName(name);
        project.setCode(code);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setBizName("Acme");
        project.setDescription("Project description");
        project.setStartedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        project.setEndedAt(LocalDateTime.of(2025, 12, 31, 0, 0));
        project.setRemark("Initial remark");
        project.setDeleted(Boolean.FALSE);
        project.setMembers(new HashSet<>(members));
        return project;
    }

    private User buildUser(String username, Set<Role> roles) {
        return User.builder()
                .username(username)
                .password(passwordEncoder.encode("password"))
                .email(username + "@example.com")
                .roles(roles)
                .status(UserStatus.ENABLED)
                .build();
    }

    private String createAccessToken(User user) {
        String sessionId = jwtUtil.newTokenId();
        userSessionRepository.save(UserSession.builder()
                .user(user)
                .sessionId(sessionId)
                .refreshTokenHash("dummy-hash")
                .accessExpiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(7))
                .build());

        String roleClaim = user.getRoles().stream()
                .findFirst()
                .map(Role::getName)
                .map(name -> "ROLE_" + name.toUpperCase())
                .orElse("ROLE_USER");
        return jwtUtil.generateAccessToken(user.getUsername(), roleClaim, sessionId, jwtUtil.newTokenId());
    }

    private Project findProjectByCode(String code) {
        return projectRepository.findAll().stream()
                .filter(project -> code.equals(project.getCode()) && !Boolean.TRUE.equals(project.getDeleted()))
                .findFirst()
                .orElseThrow();
    }
}
