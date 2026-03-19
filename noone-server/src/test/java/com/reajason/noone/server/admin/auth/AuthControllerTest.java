package com.reajason.noone.server.admin.auth;

import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.permission.PermissionRepository;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.user.*;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPGContainerConfiguration.class)
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private TwoFactorAuthService twoFactorAuthService;

    private User mfaUser;

    @BeforeEach
    void setUp() {
        mfaUser = userRepository.save(User.builder()
                .username("mfa-user")
                .password(passwordEncoder.encode("password"))
                .email("mfa-user@example.com")
                .status(UserStatus.ENABLED)
                .mfaEnabled(true)
                .mfaSecret("test-mfa-secret")
                .mustChangePassword(false)
                .deleted(false)
                .build());
    }

    @Test
    void loginShouldReturnRequire2faWithChallengeToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "mfa-user",
                                "password", "password"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("REQUIRE_2FA"))
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.actionToken").isNotEmpty());
    }

    @Test
    void verify2faShouldIssueSessionWhenChallengeAndCodeAreValid() throws Exception {
        String actionToken = issueChallengeToken();
        when(twoFactorAuthService.isCodeValid("test-mfa-secret", "123456")).thenReturn(true);

        mockMvc.perform(post("/api/auth/verify-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actionToken", actionToken,
                                "twoFactorCode", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("mfa-user"));
    }

    @Test
    void verify2faShouldReturnInvalidCodeWhenCodeIsWrong() throws Exception {
        String actionToken = issueChallengeToken();
        when(twoFactorAuthService.isCodeValid("test-mfa-secret", "000000")).thenReturn(false);

        mockMvc.perform(post("/api/auth/verify-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actionToken", actionToken,
                                "twoFactorCode", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_2FA_CODE"));
    }

    @Test
    void verify2faShouldRejectForgedChallengeToken() throws Exception {
        mockMvc.perform(post("/api/auth/verify-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actionToken", "forged-token",
                                "twoFactorCode", "123456"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_2FA_CHALLENGE"));
    }

    @Test
    void verify2faShouldRejectExpiredChallengeToken() throws Exception {
        String expiredToken = jwtUtil.generateActionToken(
                mfaUser.getUsername(),
                jwtUtil.newTokenId(),
                "login_2fa",
                "LOGIN_2FA_VERIFY",
                null,
                null,
                Duration.ofSeconds(-1));

        mockMvc.perform(post("/api/auth/verify-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actionToken", expiredToken,
                                "twoFactorCode", "123456"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_2FA_CHALLENGE"));
    }

    @Test
    void verify2faShouldRejectWrongTokenType() throws Exception {
        String wrongTypeToken = jwtUtil.generatePasswordChangeToken(mfaUser.getUsername());

        mockMvc.perform(post("/api/auth/verify-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actionToken", wrongTypeToken,
                                "twoFactorCode", "123456"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_2FA_CHALLENGE"));
    }

//    @Test
//    void refreshShouldRejectRefreshReuseAndRevokeSession() throws Exception {
//        UserSession session = userSessionRepository.save(UserSession.builder()
//                .user(mfaUser)
//                .sessionId("refresh-session")
//                .refreshTokenHash(hash("expected-refresh-id"))
//                .accessExpiresAt(LocalDateTime.now().plusMinutes(30))
//                .refreshExpiresAt(LocalDateTime.now().plusDays(1))
//                .build());
//        String refreshToken = jwtUtil.generateRefreshToken(mfaUser.getUsername(), session.getSessionId(), "expected-refresh-id");
//
//        mockMvc.perform(post("/api/auth/refresh")
//                        .header("Authorization", "Bearer " + refreshToken))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
//
//        mockMvc.perform(post("/api/auth/refresh")
//                        .header("Authorization", "Bearer " + refreshToken))
//                .andExpect(status().isUnauthorized());
//
//        TestTransaction.flagForCommit();
//        TestTransaction.end();
//
//        try {
//            UserSession committedSession = inNewTransaction(() ->
//                    userSessionRepository.findById(session.getId()).orElse(null));
//            assertThat(committedSession).isNotNull();
//            assertThat(committedSession.isRevoked()).isTrue();
//            assertThat(committedSession.getRevokeReason()).isEqualTo("REFRESH_REUSE_DETECTED");
//        } finally {
//            inNewTransaction(() -> {
//                userSessionRepository.findById(session.getId()).ifPresent(userSessionRepository::delete);
//                userRepository.findById(mfaUser.getId()).ifPresent(userRepository::delete);
//                return null;
//            });
//            TestTransaction.start();
//            TestTransaction.flagForRollback();
//        }
//    }

    @Test
    void shouldReturn404WhenRevokingForeignUserSession() throws Exception {
        String adminAccessToken = issueAdminSessionToken();
        User owner = createUser("session-owner");
        User foreignUser = createUser("foreign-owner");
        UserSession foreignSession = userSessionRepository.save(UserSession.builder()
                .user(foreignUser)
                .sessionId("foreign-session")
                .refreshTokenHash("foreign-refresh-hash")
                .accessExpiresAt(LocalDateTime.now().plusMinutes(30))
                .refreshExpiresAt(LocalDateTime.now().plusDays(1))
                .build());

        mockMvc.perform(delete("/api/users/{id}/sessions/{sessionId}", owner.getId(), foreignSession.getSessionId())
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isNotFound());

        UserSession updated = userSessionRepository.findById(foreignSession.getId()).orElseThrow();
        assertThat(updated.isRevoked()).isFalse();
    }

    @Test
    void shouldListUserSessionsUsingPagedContract() throws Exception {
        String adminAccessToken = issueAdminSessionToken();
        User owner = createUser("paged-owner");
        userSessionRepository.save(UserSession.builder()
                .user(owner)
                .sessionId("revoked-session")
                .refreshTokenHash("revoked-refresh-hash")
                .accessExpiresAt(LocalDateTime.now().plusMinutes(30))
                .refreshExpiresAt(LocalDateTime.now().plusDays(1))
                .revoked(true)
                .revokedAt(LocalDateTime.now())
                .revokeReason("MANUAL")
                .build());
        userSessionRepository.save(UserSession.builder()
                .user(owner)
                .sessionId("active-session")
                .refreshTokenHash("active-refresh-hash")
                .accessExpiresAt(LocalDateTime.now().plusMinutes(30))
                .refreshExpiresAt(LocalDateTime.now().plusDays(1))
                .build());

        mockMvc.perform(get("/api/users/{id}/sessions", owner.getId())
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .param("revoked", "false")
                        .param("page", "0")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sessionId").value("active-session"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void shouldFilterUserSessionsByCreatedAfter() throws Exception {
        String adminAccessToken = issueAdminSessionToken();
        User owner = createUser("created-after-owner");
        UserSession olderSession = userSessionRepository.save(UserSession.builder()
                .user(owner)
                .sessionId("older-session")
                .refreshTokenHash("older-refresh-hash")
                .accessExpiresAt(LocalDateTime.now().plusMinutes(30))
                .refreshExpiresAt(LocalDateTime.now().plusDays(1))
                .build());
        UserSession newerSession = userSessionRepository.save(UserSession.builder()
                .user(owner)
                .sessionId("newer-session")
                .refreshTokenHash("newer-refresh-hash")
                .accessExpiresAt(LocalDateTime.now().plusMinutes(30))
                .refreshExpiresAt(LocalDateTime.now().plusDays(1))
                .build());
        LocalDateTime cutoff = LocalDateTime.now().withNano(0);
        updateSessionCreatedAt(olderSession.getId(), cutoff.minusMinutes(1));
        updateSessionCreatedAt(newerSession.getId(), cutoff.plusMinutes(1));
        entityManager.clear();

        mockMvc.perform(get("/api/users/{id}/sessions", owner.getId())
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .param("createdAfter", cutoff.toString())
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sessionId").value("newer-session"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void shouldReturn400WhenSessionQueryDateIsMalformed() throws Exception {
        String adminAccessToken = issueAdminSessionToken();
        User owner = createUser("malformed-query-owner");

        mockMvc.perform(get("/api/users/{id}/sessions", owner.getId())
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .param("createdAfter", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    private String issueChallengeToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "mfa-user",
                                "password", "password"))))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String actionToken = response.path("actionToken").asText();
        assertThat(actionToken).isNotBlank();
        return actionToken;
    }

    private String issueAdminSessionToken() {
        Permission manageSessions = permissionRepository.save(Permission.builder()
                .code("auth:session:manage")
                .name("Manage Sessions")
                .build());
        Role adminRole = Role.builder().name("Session Admin").build();
        adminRole.setPermissions(Set.of(manageSessions));
        roleRepository.save(adminRole);

        User admin = userRepository.save(User.builder()
                .username("session-admin")
                .password(passwordEncoder.encode("password"))
                .email("session-admin@example.com")
                .roles(Set.of(adminRole))
                .status(UserStatus.ENABLED)
                .mustChangePassword(false)
                .deleted(false)
                .build());
        String sessionId = jwtUtil.newTokenId();
        userSessionRepository.save(UserSession.builder()
                .user(admin)
                .sessionId(sessionId)
                .refreshTokenHash("admin-refresh-hash")
                .accessExpiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(7))
                .build());
        return jwtUtil.generateAccessToken(admin.getUsername(), "ROLE_SESSION_ADMIN", sessionId, jwtUtil.newTokenId());
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode("password"))
                .email(username + "@example.com")
                .status(UserStatus.ENABLED)
                .mustChangePassword(false)
                .deleted(false)
                .build());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T inNewTransaction(Supplier<T> callback) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(status -> callback.get());
    }

    private void updateSessionCreatedAt(Long sessionId, LocalDateTime createdAt) {
        entityManager.createNativeQuery("update user_sessions set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", sessionId)
                .executeUpdate();
        entityManager.flush();
    }
}
