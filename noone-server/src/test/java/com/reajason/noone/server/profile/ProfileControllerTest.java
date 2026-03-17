package com.reajason.noone.server.profile;

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
import com.reajason.noone.server.profile.config.*;
import com.reajason.noone.server.profile.dto.ProfileCreateRequest;
import com.reajason.noone.server.profile.dto.ProfileUpdateRequest;
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
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ProfileRepository profileRepository;

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

    private String accessToken;

    @AfterEach
    void cleanupAuditLogs() {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.executeWithoutResult(status -> auditLogRepository.deleteAll());
    }

    @BeforeEach
    void setUp() {
        Permission createPerm = permissionRepository.save(Permission.builder().code("profile:create").name("Create Profile").build());
        Permission readPerm = permissionRepository.save(Permission.builder().code("profile:read").name("Read Profile").build());
        Permission updatePerm = permissionRepository.save(Permission.builder().code("profile:update").name("Update Profile").build());
        Permission deletePerm = permissionRepository.save(Permission.builder().code("profile:delete").name("Delete Profile").build());
        Permission listPerm = permissionRepository.save(Permission.builder().code("profile:list").name("List Profiles").build());

        Role role = Role.builder().name("Profile Manager").build();
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

        String sessionId = jwtUtil.newTokenId();
        userSessionRepository.save(UserSession.builder()
                .user(user)
                .sessionId(sessionId)
                .refreshTokenHash("dummy-hash")
                .accessExpiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(7))
                .build());

        accessToken = jwtUtil.generateAccessToken(
                "testuser", "ROLE_PROFILE MANAGER", sessionId, jwtUtil.newTokenId());
    }

    // --- Create ---

    @Test
    void shouldCreateProfileAndReturn201() throws Exception {
        mockMvc.perform(post("/api/profiles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("new-profile"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("new-profile"))
                .andExpect(jsonPath("$.protocolType").value("HTTP"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(profileRepository.existsByNameAndDeletedFalse("new-profile")).isTrue();

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLogEntity log = logs.get(0);
        assertThat(log.getModule()).isEqualTo(AuditModule.PROFILE);
        assertThat(log.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(log.getTargetType()).isEqualTo("Profile");
        assertThat(log.getTargetId()).isNotBlank();
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldCreateProfileWithFullConfig() throws Exception {
        ProfileCreateRequest request = createRequest("full-config");
        request.setIdentifier(new IdentifierConfig(
                IdentifierLocation.HEADER, IdentifierOperator.EQUALS, "X-Token", "abc123"));
        request.setRequestTransformations(List.of("base64"));
        request.setResponseTransformations(List.of("base64-decode"));

        mockMvc.perform(post("/api/profiles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identifier.location").value("HEADER"))
                .andExpect(jsonPath("$.identifier.operator").value("EQUALS"))
                .andExpect(jsonPath("$.requestTransformations[0]").value("base64"))
                .andExpect(jsonPath("$.responseTransformations[0]").value("base64-decode"));
    }

    @Test
    void shouldReturn400WhenCreateRequestMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/profiles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.password").exists())
                .andExpect(jsonPath("$.protocolType").exists())
                .andExpect(jsonPath("$.protocolConfig").exists());
    }

    @Test
    void shouldReturn400WhenDuplicateNameOnCreate() throws Exception {
        profileRepository.save(buildProfile("duplicate"));

        mockMvc.perform(post("/api/profiles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("duplicate"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Profile name already exists：duplicate"));

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLogEntity log = logs.get(0);
        assertThat(log.getModule()).isEqualTo(AuditModule.PROFILE);
        assertThat(log.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(log.isSuccess()).isFalse();
        assertThat(log.getErrorMessage()).contains("Profile name already exists");
    }

    @Test
    void shouldCreateProfileWhenDeletedProfileHasSameName() throws Exception {
        Profile deletedProfile = buildProfile("reusable-name");
        deletedProfile.setDeleted(Boolean.TRUE);
        profileRepository.save(deletedProfile);

        mockMvc.perform(post("/api/profiles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("reusable-name"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("reusable-name"));

        assertThat(profileRepository.existsByNameAndDeletedFalse("reusable-name")).isTrue();
    }

    // --- GetById ---

    @Test
    void shouldGetProfileById() throws Exception {
        Profile saved = profileRepository.save(buildProfile("get-test"));

        mockMvc.perform(get("/api/profiles/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("get-test"))
                .andExpect(jsonPath("$.protocolType").value("HTTP"));
    }

    @Test
    void shouldReturn404WhenProfileNotFound() throws Exception {
        mockMvc.perform(get("/api/profiles/{id}", 99999)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404WhenGettingDeletedProfile() throws Exception {
        Profile deletedProfile = buildProfile("deleted-get");
        deletedProfile.setDeleted(Boolean.TRUE);
        Profile saved = profileRepository.save(deletedProfile);

        mockMvc.perform(get("/api/profiles/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    // --- Update ---

    @Test
    void shouldUpdateProfile() throws Exception {
        Profile saved = profileRepository.save(buildProfile("before-update"));

        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest();
        updateRequest.setName("after-update");
        updateRequest.setPassword("new-pass");

        mockMvc.perform(put("/api/profiles/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("after-update"));

        Profile updated = profileRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("after-update");
        assertThat(passwordEncoder.matches("new-pass", updated.getPassword())).isTrue();

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLogEntity log = logs.get(0);
        assertThat(log.getModule()).isEqualTo(AuditModule.PROFILE);
        assertThat(log.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(log.getTargetType()).isEqualTo("Profile");
        assertThat(log.getTargetId()).isEqualTo(String.valueOf(saved.getId()));
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldReturn400WhenUpdateDuplicateName() throws Exception {
        profileRepository.save(buildProfile("existing-name"));
        Profile target = profileRepository.save(buildProfile("to-update"));

        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest();
        updateRequest.setName("existing-name");

        mockMvc.perform(put("/api/profiles/{id}", target.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Profile name already exists：existing-name"));
    }

    @Test
    void shouldReturn404WhenUpdateNonExistentProfile() throws Exception {
        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest();
        updateRequest.setName("irrelevant");

        mockMvc.perform(put("/api/profiles/{id}", 99999)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Profile not found：99999"));
    }

    // --- Delete ---

    @Test
    void shouldDeleteProfileAndReturn204() throws Exception {
        Profile saved = profileRepository.save(buildProfile("to-delete"));

        mockMvc.perform(delete("/api/profiles/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        Profile deleted = profileRepository.findById(saved.getId()).orElseThrow();
        assertThat(deleted.getDeleted()).isTrue();

        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLogEntity log = logs.get(0);
        assertThat(log.getModule()).isEqualTo(AuditModule.PROFILE);
        assertThat(log.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(log.getTargetType()).isEqualTo("Profile");
        assertThat(log.getTargetId()).isEqualTo(String.valueOf(saved.getId()));
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldReturn404WhenDeletingDeletedProfile() throws Exception {
        Profile deletedProfile = buildProfile("already-deleted");
        deletedProfile.setDeleted(Boolean.TRUE);
        Profile saved = profileRepository.save(deletedProfile);

        mockMvc.perform(delete("/api/profiles/{id}", saved.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Profile not found：" + saved.getId()));
    }

    @Test
    void shouldReturn404WhenDeleteNonExistentProfile() throws Exception {
        mockMvc.perform(delete("/api/profiles/{id}", 99999)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Profile not found：99999"));
    }

    // --- Query ---

    @Test
    void shouldQueryProfilesWithFilters() throws Exception {
        profileRepository.save(buildProfile("alpha-http"));
        profileRepository.save(buildProfile("beta-http"));
        profileRepository.save(buildProfile("gamma-ws"));
        Profile deletedProfile = buildProfile("deleted-http");
        deletedProfile.setDeleted(Boolean.TRUE);
        profileRepository.save(deletedProfile);

        mockMvc.perform(get("/api/profiles")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("name", "http")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void shouldQueryWithPaginationAndSort() throws Exception {
        for (int i = 1; i <= 5; i++) {
            profileRepository.save(buildProfile("profile-" + i));
        }

        mockMvc.perform(get("/api/profiles")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("page", "0")
                        .param("pageSize", "2")
                        .param("sortBy", "name")
                        .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("profile-1"))
                .andExpect(jsonPath("$.content[1].name").value("profile-2"))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void shouldQueryProfilesByProtocolType() throws Exception {
        profileRepository.save(buildProfile("http-1"));
        profileRepository.save(buildProfile("http-2"));
        Profile wsProfile = buildProfile("ws-1");
        wsProfile.setProtocolType(ProtocolType.WEBSOCKET);
        profileRepository.save(wsProfile);

        mockMvc.perform(get("/api/profiles")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("protocolType", "WEBSOCKET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("ws-1"))
                .andExpect(jsonPath("$.content[0].protocolType").value("WEBSOCKET"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void shouldReturnEmptyPageWhenNoMatch() throws Exception {
        mockMvc.perform(get("/api/profiles")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // --- Security ---

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/profiles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WhenNoPermission() throws Exception {
        Role noPermRole = roleRepository.save(Role.builder().name("No Perm Role").build());
        User noPerm = userRepository.save(User.builder()
                .username("noperm")
                .password(passwordEncoder.encode("password"))
                .email("noperm@example.com")
                .roles(Set.of(noPermRole))
                .status(UserStatus.ENABLED)
                .build());

        String sessionId = jwtUtil.newTokenId();
        userSessionRepository.save(UserSession.builder()
                .user(noPerm)
                .sessionId(sessionId)
                .refreshTokenHash("dummy-hash")
                .accessExpiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(7))
                .build());

        String noPermToken = jwtUtil.generateAccessToken(
                "noperm", "ROLE_NO PERM ROLE", sessionId, jwtUtil.newTokenId());

        mockMvc.perform(get("/api/profiles")
                        .header("Authorization", "Bearer " + noPermToken))
                .andExpect(status().isForbidden());
    }

    // --- Helpers ---

    private ProfileCreateRequest createRequest(String name) {
        ProfileCreateRequest request = new ProfileCreateRequest();
        request.setName(name);
        request.setPassword("test-pass-123");
        request.setProtocolType(ProtocolType.HTTP);
        HttpProtocolConfig config = new HttpProtocolConfig();
        config.setRequestMethod("GET");
        config.setResponseStatusCode(200);
        request.setProtocolConfig(config);
        return request;
    }

    private Profile buildProfile(String name) {
        Profile profile = new Profile();
        profile.setName(name);
        profile.setPassword(passwordEncoder.encode("stored-pass"));
        profile.setProtocolType(ProtocolType.HTTP);
        HttpProtocolConfig config = new HttpProtocolConfig();
        config.setRequestMethod("GET");
        config.setResponseStatusCode(200);
        profile.setProtocolConfig(config);
        profile.setDeleted(Boolean.FALSE);
        return profile;
    }
}
