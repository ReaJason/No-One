package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.user.dto.ResetPasswordRequest;
import com.reajason.noone.server.util.JwtUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

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
class UserControllerTest {

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
    private UserIpWhitelistRepository userIpWhitelistRepository;

    @Autowired
    private LoginLogRepository loginLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private com.reajason.noone.server.shell.oplog.ShellOperationLogService shellOperationLogService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        Permission createPerm = permissionRepository.save(Permission.builder().code("user:create").name("Create User").build());
        Permission updatePerm = permissionRepository.save(Permission.builder().code("user:update").name("Update User").build());
        Permission readWhitelistPerm = permissionRepository.save(Permission.builder().code("user:whitelist:read").name("Read User Whitelist").build());
        Permission manageWhitelistPerm = permissionRepository.save(Permission.builder().code("user:whitelist:manage").name("Manage User Whitelist").build());
        Permission readLoginLogPerm = permissionRepository.save(Permission.builder().code("auth:log:read").name("Read Auth Logs").build());

        Role role = Role.builder().name("User Manager").build();
        role.setPermissions(Set.of(createPerm, updatePerm, readWhitelistPerm, manageWhitelistPerm, readLoginLogPerm));
        roleRepository.save(role);

        User admin = User.builder()
                .username("admin-user")
                .password(passwordEncoder.encode("AdminPass1!"))
                .email("admin@example.com")
                .roles(Set.of(role))
                .status(UserStatus.ENABLED)
                .build();
        userRepository.save(admin);

        String sessionId = jwtUtil.newTokenId();
        userSessionRepository.save(UserSession.builder()
                .user(admin)
                .sessionId(sessionId)
                .refreshTokenHash("admin-refresh-hash")
                .accessExpiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(7))
                .build());

        accessToken = createAccessToken(admin);
    }

    @Test
    void shouldResetPasswordAndHonorForceChangeFlag() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("target-user")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("target@example.com")
                .status(UserStatus.ENABLED)
                .mustChangePassword(true)
                .build());
        UserSession session = userSessionRepository.save(UserSession.builder()
                .user(targetUser)
                .sessionId("target-session")
                .refreshTokenHash("target-refresh-hash")
                .accessExpiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(7))
                .build());

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("OldPass1!");
        request.setNewPassword("NewPass1!");
        request.setForceChangeOnNextLogin(false);

        mockMvc.perform(put("/api/users/{id}/reset-password", targetUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetUser.getId()))
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        User updated = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass1!", updated.getPassword())).isTrue();
        assertThat(updated.isMustChangePassword()).isFalse();

        UserSession revokedSession = userSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(revokedSession.isRevoked()).isTrue();
        assertThat(revokedSession.getRevokeReason()).isEqualTo("ADMIN_PASSWORD_RESET");
    }

    @Test
    void shouldResetPasswordWithoutOldPasswordForAdminFlow() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("target-without-old-password")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("target-without-old@example.com")
                .status(UserStatus.ENABLED)
                .mustChangePassword(true)
                .build());
        UserSession session = userSessionRepository.save(UserSession.builder()
                .user(targetUser)
                .sessionId("target-without-old-session")
                .refreshTokenHash("target-without-old-refresh-hash")
                .accessExpiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(7))
                .build());

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("NewPass1!");
        request.setForceChangeOnNextLogin(true);

        mockMvc.perform(put("/api/users/{id}/reset-password", targetUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetUser.getId()))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        User updated = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass1!", updated.getPassword())).isTrue();
        assertThat(updated.isMustChangePassword()).isTrue();

        UserSession revokedSession = userSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(revokedSession.isRevoked()).isTrue();
        assertThat(revokedSession.getRevokeReason()).isEqualTo("ADMIN_PASSWORD_RESET");
    }

    @Test
    void shouldReturn400WhenCreateRequestContainsStatusProperty() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "create-with-status",
                                  "password": "Create1!",
                                  "email": "create-with-status@example.com",
                                  "status": "ENABLED"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.existsByUsernameAndDeletedFalse("create-with-status")).isFalse();
    }

    @Test
    void shouldReturn400WhenOldPasswordIsIncorrect() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("target-with-wrong-old")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("wrong-old@example.com")
                .status(UserStatus.ENABLED)
                .build());

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("WrongPass1!");
        request.setNewPassword("NewPass1!");
        request.setForceChangeOnNextLogin(true);

        mockMvc.perform(put("/api/users/{id}/reset-password", targetUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("旧密码不正确"));
    }

    @Test
    void shouldReturn400WhenNewPasswordViolatesPasswordPolicy() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("target-with-weak-password")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("weak-password@example.com")
                .status(UserStatus.ENABLED)
                .build());

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("OldPass1!");
        request.setNewPassword("weakpass");

        mockMvc.perform(put("/api/users/{id}/reset-password", targetUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.newPassword").value("密码复杂度不符合要求"));
    }

    @Test
    void shouldListWhitelistEntries() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("whitelist-target-list")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("whitelist-target-list@example.com")
                .status(UserStatus.ENABLED)
                .build());

        userIpWhitelistRepository.save(UserIpWhitelist.builder()
                .user(targetUser)
                .ipAddress("10.0.0.1")
                .build());

        mockMvc.perform(get("/api/users/{id}/ip-whitelist", targetUser.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ipAddress").value("10.0.0.1"))
                .andExpect(jsonPath("$[0].userId").value(targetUser.getId()));
    }

    @Test
    void shouldAddWhitelistEntry() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("whitelist-target-add")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("whitelist-target-add@example.com")
                .status(UserStatus.ENABLED)
                .build());

        mockMvc.perform(post("/api/users/{id}/ip-whitelist", targetUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ipAddress":"10.0.0.2"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value(targetUser.getId()))
                .andExpect(jsonPath("$.ipAddress").value("10.0.0.2"));

        assertThat(userIpWhitelistRepository.existsByUserIdAndIpAddress(targetUser.getId(), "10.0.0.2")).isTrue();
    }

    @Test
    void shouldReplaceWhitelistEntriesAndDeduplicate() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("whitelist-target-replace")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("whitelist-target-replace@example.com")
                .status(UserStatus.ENABLED)
                .build());

        userIpWhitelistRepository.save(UserIpWhitelist.builder()
                .user(targetUser)
                .ipAddress("10.0.0.9")
                .build());

        mockMvc.perform(put("/api/users/{id}/ip-whitelist", targetUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of("10.0.0.1", "10.0.0.1", "10.0.0.2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ipAddress").value("10.0.0.1"))
                .andExpect(jsonPath("$[1].ipAddress").value("10.0.0.2"));

        assertThat(userIpWhitelistRepository.findByUserIdOrderByCreatedAtAsc(targetUser.getId()))
                .extracting(UserIpWhitelist::getIpAddress)
                .containsExactly("10.0.0.1", "10.0.0.2");
    }

    @Test
    void shouldDeleteWhitelistEntry() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("whitelist-target-delete")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("whitelist-target-delete@example.com")
                .status(UserStatus.ENABLED)
                .build());

        UserIpWhitelist entry = userIpWhitelistRepository.save(UserIpWhitelist.builder()
                .user(targetUser)
                .ipAddress("10.0.0.3")
                .build());

        mockMvc.perform(delete("/api/users/{id}/ip-whitelist/{entryId}", targetUser.getId(), entry.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(userIpWhitelistRepository.findById(entry.getId())).isEmpty();
    }

    @Test
    void shouldReturn400WhenWhitelistIpIsInvalid() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("whitelist-target-invalid")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("whitelist-target-invalid@example.com")
                .status(UserStatus.ENABLED)
                .build());

        mockMvc.perform(post("/api/users/{id}/ip-whitelist", targetUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ipAddress":"10.0.0.*"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("IP地址不合法：10.0.0.*"));
    }

    @Test
    void shouldReturn404WhenWhitelistUserNotFound() throws Exception {
        mockMvc.perform(get("/api/users/{id}/ip-whitelist", 99999L)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("用户不存在：99999"));
    }

    @Test
    void shouldPageAndFilterLoginLogs() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("login-log-target")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("login-log-target@example.com")
                .status(UserStatus.ENABLED)
                .build());

        LocalDateTime now = LocalDateTime.now();
        saveLoginLog(targetUser, "session-old", "10.0.0.1", LoginLog.LoginStatus.SUCCESS, now.minusDays(1));
        saveLoginLog(targetUser, "session-match", "10.0.0.2", LoginLog.LoginStatus.SUCCESS, now);
        saveLoginLog(targetUser, "session-other", "10.0.0.3", LoginLog.LoginStatus.DISABLED, now.minusHours(2));

        mockMvc.perform(get("/api/users/{id}/login-logs", targetUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .param("status", "SUCCESS")
                        .param("ipAddress", "10.0.0.2")
                        .param("sessionId", "session-match")
                        .param("loginTimeAfter", now.minusMinutes(10).toString())
                        .param("loginTimeBefore", now.plusMinutes(10).toString())
                        .param("page", "0")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sessionId").value("session-match"))
                .andExpect(jsonPath("$.content[0].ipAddress").value("10.0.0.2"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].deviceInfo").doesNotExist());
    }

    @Test
    void shouldReturn404WhenLoginLogUserNotFound() throws Exception {
        mockMvc.perform(get("/api/users/{id}/login-logs", 99999L)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("用户不存在：99999"));
    }

    @Test
    void shouldReturn400WhenLoginLogStatusIsInvalid() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("login-log-invalid-status")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("login-log-invalid-status@example.com")
                .status(UserStatus.ENABLED)
                .build());

        mockMvc.perform(get("/api/users/{id}/login-logs", targetUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenLoginLogTimeFilterIsMalformed() throws Exception {
        User targetUser = userRepository.save(User.builder()
                .username("login-log-invalid-time")
                .password(passwordEncoder.encode("OldPass1!"))
                .email("login-log-invalid-time@example.com")
                .status(UserStatus.ENABLED)
                .build());

        mockMvc.perform(get("/api/users/{id}/login-logs", targetUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .param("loginTimeAfter", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn403WhenMissingWhitelistReadPermission() throws Exception {
        User noPermUser = userRepository.save(User.builder()
                .username("no-whitelist-read")
                .password(passwordEncoder.encode("password"))
                .email("no-whitelist-read@example.com")
                .roles(Set.of(roleRepository.save(Role.builder().name("No Whitelist Read").build())))
                .status(UserStatus.ENABLED)
                .build());
        String noPermToken = createAccessToken(noPermUser);

        mockMvc.perform(get("/api/users/{id}/ip-whitelist", noPermUser.getId())
                        .header("Authorization", "Bearer " + noPermToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn403WhenMissingWhitelistManagePermission() throws Exception {
        User noPermUser = userRepository.save(User.builder()
                .username("no-whitelist-manage")
                .password(passwordEncoder.encode("password"))
                .email("no-whitelist-manage@example.com")
                .roles(Set.of(roleRepository.save(Role.builder().name("No Whitelist Manage").build())))
                .status(UserStatus.ENABLED)
                .build());
        String noPermToken = createAccessToken(noPermUser);

        mockMvc.perform(post("/api/users/{id}/ip-whitelist", noPermUser.getId())
                        .header("Authorization", "Bearer " + noPermToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ipAddress":"10.0.0.4"}
                                """))
                .andExpect(status().isForbidden());
    }

    private String createAccessToken(User user) {
        String sessionId = jwtUtil.newTokenId();
        userSessionRepository.save(UserSession.builder()
                .user(user)
                .sessionId(sessionId)
                .refreshTokenHash("admin-refresh-hash")
                .accessExpiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(7))
                .build());

        String roleClaim = user.getRoles().stream()
                .findFirst()
                .map(Role::getName)
                .map(name -> "ROLE_" + name.toUpperCase().replace(' ', '_'))
                .orElse("ROLE_USER");
        return jwtUtil.generateAccessToken(user.getUsername(), roleClaim, sessionId, jwtUtil.newTokenId());
    }

    private LoginLog saveLoginLog(User user, String sessionId, String ipAddress, LoginLog.LoginStatus status, LocalDateTime loginTime) {
        LoginLog log = loginLogRepository.save(LoginLog.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .sessionId(sessionId)
                .ipAddress(ipAddress)
                .userAgent("TestAgent/1.0")
                .browser("Chrome")
                .os("Linux")
                .status(status)
                .failReason(null)
                .build());
        entityManager.createNativeQuery("update login_logs set login_time = :loginTime where id = :id")
                .setParameter("loginTime", loginTime)
                .setParameter("id", log.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        return log;
    }
}
