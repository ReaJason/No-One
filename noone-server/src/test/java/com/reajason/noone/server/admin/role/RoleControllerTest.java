package com.reajason.noone.server.admin.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.user.*;
import com.reajason.noone.server.audit.AuditAction;
import com.reajason.noone.server.audit.AuditLogEntity;
import com.reajason.noone.server.audit.AuditLogRepository;
import com.reajason.noone.server.audit.AuditModule;
import com.reajason.noone.server.admin.role.dto.RoleCreateRequest;
import com.reajason.noone.server.admin.role.dto.RoleUpdateRequest;
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
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String accessToken;

    @AfterEach
    void cleanupAuditLogs() {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.executeWithoutResult(status -> auditLogRepository.deleteAll());
    }

    @BeforeEach
    void setUp() {
        Permission createPerm = permissionRepository.save(Permission.builder().code("role:create").name("Create Role").build());
        Permission readPerm = permissionRepository.save(Permission.builder().code("role:read").name("Read Role").build());
        Permission updatePerm = permissionRepository.save(Permission.builder().code("role:update").name("Update Role").build());
        Permission deletePerm = permissionRepository.save(Permission.builder().code("role:delete").name("Delete Role").build());
        Permission listPerm = permissionRepository.save(Permission.builder().code("role:list").name("List Role").build());

        Role role = Role.builder().name("Role Manager").build();
        role.setPermissions(Set.of(createPerm, readPerm, updatePerm, deletePerm, listPerm));
        roleRepository.save(role);

        User user = User.builder()
                .username("testuser")
                .password(passwordEncoder.encode("password"))
                .email("test@example.com")
                .roles(Set.of(role))
                .status(UserStatus.ENABLED)
                .build();
        userRepository.save(user);

        accessToken = createAccessToken(user);
    }

    @Test
    void shouldCreateRoleAndReturn201() throws Exception {
        mockMvc.perform(post("/api/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Operator"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Operator"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(roleRepository.existsByNameAndDeletedFalse("Operator")).isTrue();

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLogEntity log = logs.get(0);
        assertThat(log.getModule()).isEqualTo(AuditModule.ROLE);
        assertThat(log.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(log.getTargetType()).isEqualTo("Role");
        assertThat(log.getTargetId()).isNotBlank();
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldReturn400WhenCreateRequestMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());
    }

    @Test
    void shouldReturn400WhenDuplicateNameOnCreate() throws Exception {
        roleRepository.save(Role.builder().name("Operator").build());

        mockMvc.perform(post("/api/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Operator"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("角色名称已存在：Operator"));
    }

    @Test
    void shouldGetRoleById() throws Exception {
        Role saved = roleRepository.save(Role.builder().name("ReadOnly").build());

        mockMvc.perform(get("/api/roles/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("ReadOnly"));
    }

    @Test
    void shouldReturn404WhenRoleNotFound() throws Exception {
        mockMvc.perform(get("/api/roles/{id}", 99999)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("角色不存在：99999"));
    }

    @Test
    void shouldUpdateRoleAndWriteAuditLog() throws Exception {
        Role saved = roleRepository.save(Role.builder().name("BeforeUpdate").build());

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("AfterUpdate");

        mockMvc.perform(put("/api/roles/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AfterUpdate"));

        Role updated = roleRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("AfterUpdate");

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLogEntity log = logs.get(0);
        assertThat(log.getModule()).isEqualTo(AuditModule.ROLE);
        assertThat(log.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(log.getTargetType()).isEqualTo("Role");
        assertThat(log.getTargetId()).isEqualTo(String.valueOf(saved.getId()));
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldReturn400WhenUpdateDuplicateName() throws Exception {
        roleRepository.save(Role.builder().name("Existing").build());
        Role target = roleRepository.save(Role.builder().name("Target").build());

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("Existing");

        mockMvc.perform(put("/api/roles/{id}", target.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("角色名称已存在：Existing"));
    }

    @Test
    void shouldReturn404WhenUpdateNonExistentRole() throws Exception {
        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("irrelevant");

        mockMvc.perform(put("/api/roles/{id}", 99999)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("角色不存在：99999"));
    }

    @Test
    void shouldDeleteRoleAndReturn204() throws Exception {
        Role saved = roleRepository.save(Role.builder().name("ToDelete").build());

        mockMvc.perform(delete("/api/roles/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        Role deleted = roleRepository.findById(saved.getId()).orElseThrow();
        assertThat(deleted.getDeleted()).isTrue();

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLogEntity log = logs.get(0);
        assertThat(log.getModule()).isEqualTo(AuditModule.ROLE);
        assertThat(log.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(log.getTargetType()).isEqualTo("Role");
        assertThat(log.getTargetId()).isEqualTo(String.valueOf(saved.getId()));
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldReturn404WhenDeleteNonExistentRole() throws Exception {
        mockMvc.perform(delete("/api/roles/{id}", 99999)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("角色不存在：99999"));
    }

    @Test
    void shouldQueryRolesByName() throws Exception {
        roleRepository.save(Role.builder().name("Operator").build());
        roleRepository.save(Role.builder().name("Auditor").build());

        mockMvc.perform(get("/api/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("name", "perat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Operator"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void shouldQueryWithPaginationAndSort() throws Exception {
        for (int i = 1; i <= 5; i++) {
            roleRepository.save(Role.builder().name("Role-" + i).build());
        }

        mockMvc.perform(get("/api/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("name", "Role-")
                        .param("page", "0")
                        .param("pageSize", "2")
                        .param("sortBy", "name")
                        .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Role-1"))
                .andExpect(jsonPath("$.content[1].name").value("Role-2"))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void shouldSyncPermissionsAndReturnUpdatedRole() throws Exception {
        Permission readUser = permissionRepository.save(Permission.builder().code("user:read").name("Read User").build());
        Permission updateUser = permissionRepository.save(Permission.builder().code("user:update").name("Update User").build());
        Role role = roleRepository.save(Role.builder().name("SyncTarget").build());

        mockMvc.perform(put("/api/roles/{id}/permissions", role.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(readUser.getId(), updateUser.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(role.getId()))
                .andExpect(jsonPath("$.permissions.length()").value(2));

        Role updated = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(updated.getPermissions())
                .extracting(Permission::getId)
                .containsExactlyInAnyOrder(readUser.getId(), updateUser.getId());

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLogEntity log = logs.get(0);
        assertThat(log.getModule()).isEqualTo(AuditModule.ROLE);
        assertThat(log.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(log.getTargetType()).isEqualTo("Role");
        assertThat(log.getTargetId()).isEqualTo(String.valueOf(role.getId()));
        assertThat(log.getDescription()).isEqualTo("Sync permissions");
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldSyncPermissionsToEmptyWhenRequestBodyIsEmptyArray() throws Exception {
        Permission existingPerm = permissionRepository.save(Permission.builder().code("user:delete").name("Delete User").build());
        Role role = roleRepository.save(Role.builder().name("SyncEmpty").permissions(Set.of(existingPerm)).build());

        mockMvc.perform(put("/api/roles/{id}/permissions", role.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(0));

        Role updated = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(updated.getPermissions()).isEmpty();
    }

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WhenNoPermissionToCreate() throws Exception {
        User noPermUser = userRepository.save(User.builder()
                .username("noperm")
                .password(passwordEncoder.encode("password"))
                .email("noperm@example.com")
                .roles(Set.of(roleRepository.save(Role.builder().name("No Perm Role").build())))
                .status(UserStatus.ENABLED)
                .build());
        String noPermToken = createAccessToken(noPermUser);

        mockMvc.perform(post("/api/roles")
                        .header("Authorization", "Bearer " + noPermToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("BlockedRole"))))
                .andExpect(status().isForbidden());
    }

    private RoleCreateRequest createRequest(String name) {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setName(name);
        return request;
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
}
