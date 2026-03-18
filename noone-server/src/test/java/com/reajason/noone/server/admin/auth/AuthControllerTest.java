package com.reajason.noone.server.admin.auth;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.admin.user.UserStatus;
import com.reajason.noone.server.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

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
}
