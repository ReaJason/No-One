package com.reajason.noone.server.admin.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.user.*;
import com.reajason.noone.server.admin.permission.dto.PermissionCreateRequest;
import com.reajason.noone.server.admin.permission.dto.PermissionUpdateRequest;
import com.reajason.noone.server.util.JwtUtil;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

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

    private String accessToken;

    @BeforeEach
    void setUp() {
        Permission createPerm = permissionRepository.save(Permission.builder().code("permission:create").name("Create Permission").build());
        Permission readPerm = permissionRepository.save(Permission.builder().code("permission:read").name("Read Permission").build());
        Permission updatePerm = permissionRepository.save(Permission.builder().code("permission:update").name("Update Permission").build());
        Permission deletePerm = permissionRepository.save(Permission.builder().code("permission:delete").name("Delete Permission").build());
        Permission listPerm = permissionRepository.save(Permission.builder().code("permission:list").name("List Permission").build());

        Role role = Role.builder().name("Permission Manager").build();
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
    void shouldCreatePermissionAndReturn201() throws Exception {
        mockMvc.perform(post("/api/permissions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("user:create", "Create User"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("user:create"))
                .andExpect(jsonPath("$.name").value("Create User"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(permissionRepository.existsByCodeAndDeletedFalse("user:create")).isTrue();
    }

    @Test
    void shouldReturn400WhenCreateRequestMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/permissions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.name").exists());
    }

    @Test
    void shouldReturn400WhenCreateRequestCodeFormatInvalid() throws Exception {
        mockMvc.perform(post("/api/permissions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("invalid-format", "Invalid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void shouldReturn400WhenDuplicateCodeOnCreate() throws Exception {
        permissionRepository.save(Permission.builder().code("user:create").name("Existing").build());

        mockMvc.perform(post("/api/permissions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("user:create", "Create User"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("权限代码已存在：user:create"));
    }

    @Test
    void shouldGetPermissionById() throws Exception {
        Permission saved = permissionRepository.save(Permission.builder().code("user:read").name("Read User").build());

        mockMvc.perform(get("/api/permissions/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.code").value("user:read"))
                .andExpect(jsonPath("$.name").value("Read User"));
    }

    @Test
    void shouldReturn404WhenPermissionNotFound() throws Exception {
        mockMvc.perform(get("/api/permissions/{id}", 99999)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("权限不存在：99999"));
    }

    @Test
    void shouldUpdatePermission() throws Exception {
        Permission saved = permissionRepository.save(Permission.builder().code("user:read").name("Read User").build());

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setCode("user:update");
        request.setName("Update User");

        mockMvc.perform(put("/api/permissions/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("user:update"))
                .andExpect(jsonPath("$.name").value("Update User"));

        Permission updated = permissionRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getCode()).isEqualTo("user:update");
        assertThat(updated.getName()).isEqualTo("Update User");
    }

    @Test
    void shouldReturn400WhenUpdateDuplicateCode() throws Exception {
        permissionRepository.save(Permission.builder().code("user:existing").name("Existing").build());
        Permission target = permissionRepository.save(Permission.builder().code("user:target").name("Target").build());

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setCode("user:existing");

        mockMvc.perform(put("/api/permissions/{id}", target.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("权限代码已存在：user:existing"));
    }

    @Test
    void shouldReturn404WhenUpdateNonExistentPermission() throws Exception {
        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setName("irrelevant");

        mockMvc.perform(put("/api/permissions/{id}", 99999)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("权限不存在：99999"));
    }

    @Test
    void shouldDeletePermissionAndReturn204() throws Exception {
        Permission saved = permissionRepository.save(Permission.builder().code("user:delete").name("Delete User").build());

        mockMvc.perform(delete("/api/permissions/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        Permission deleted = permissionRepository.findById(saved.getId()).orElseThrow();
        assertThat(deleted.getDeleted()).isTrue();
    }

    @Test
    void shouldReturn404WhenDeleteNonExistentPermission() throws Exception {
        mockMvc.perform(delete("/api/permissions/{id}", 99999)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("权限不存在：99999"));
    }

    @Test
    void shouldQueryPermissionsByCode() throws Exception {
        permissionRepository.save(Permission.builder().code("user:read").name("Read User").build());
        permissionRepository.save(Permission.builder().code("project:create").name("Create Project").build());

        mockMvc.perform(get("/api/permissions")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("code", "user:"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("user:read"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void shouldQueryWithPaginationAndSort() throws Exception {
        for (int i = 1; i <= 5; i++) {
            permissionRepository.save(Permission.builder()
                    .code("module" + i + ":action" + i)
                    .name("Permission " + i)
                    .build());
        }

        mockMvc.perform(get("/api/permissions")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("code", "module")
                        .param("page", "0")
                        .param("pageSize", "2")
                        .param("sortBy", "name")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Permission 1"))
                .andExpect(jsonPath("$.content[1].name").value("Permission 2"))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/permissions"))
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

        mockMvc.perform(post("/api/permissions")
                        .header("Authorization", "Bearer " + noPermToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("user:list", "List User"))))
                .andExpect(status().isForbidden());
    }

    private PermissionCreateRequest createRequest(String code, String name) {
        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setCode(code);
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
