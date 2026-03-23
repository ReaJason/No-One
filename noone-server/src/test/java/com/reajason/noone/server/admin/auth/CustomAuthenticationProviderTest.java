package com.reajason.noone.server.admin.auth;

import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.admin.user.UserStatus;
import com.reajason.noone.server.config.LoginIpPolicyService;
import com.reajason.noone.server.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomAuthenticationProviderTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TwoFactorAuthService twoFactorAuthService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private LoginIpPolicyService loginIpPolicyService;

    @InjectMocks
    private CustomAuthenticationProvider provider;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("encodedpassword")
                .status(UserStatus.ENABLED)
                .failedAttempts(0)
                .build();

        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        lenient().when(loginIpPolicyService.isAllowed("127.0.0.1")).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testSuccessfulLogin() {
        when(userRepository.findByUsernameAndDeletedFalse("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "encodedpassword")).thenReturn(true);

        Authentication auth = new TwoFactorAuthenticationToken("testuser", "password", null);
        Authentication result = provider.authenticate(auth);

        assertNotNull(result);
        assertEquals("testuser", result.getName());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testFailedLoginIncrementsAttempts() {
        when(userRepository.findByUsernameAndDeletedFalse("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", "encodedpassword")).thenReturn(false);

        Authentication auth = new TwoFactorAuthenticationToken("testuser", "wrongpassword", null);

        assertThrows(BadCredentialsException.class, () -> provider.authenticate(auth));
        assertEquals(1, testUser.getFailedAttempts());
        verify(userRepository).save(testUser);
    }

    @Test
    void testLockoutAfterMaxAttempts() {
        testUser.setFailedAttempts(4);
        when(userRepository.findByUsernameAndDeletedFalse("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", "encodedpassword")).thenReturn(false);

        Authentication auth = new TwoFactorAuthenticationToken("testuser", "wrongpassword", null);

        assertThrows(BadCredentialsException.class, () -> provider.authenticate(auth));
        assertEquals(5, testUser.getFailedAttempts());
        assertNotNull(testUser.getLockTime());
        verify(userRepository).save(testUser);
    }

    @Test
    void testLockedUserThrowsException() {
        testUser.setLockTime(LocalDateTime.now().minusMinutes(5));
        when(userRepository.findByUsernameAndDeletedFalse("testuser")).thenReturn(Optional.of(testUser));

        Authentication auth = new TwoFactorAuthenticationToken("testuser", "password", null);

        assertThrows(LockedException.class, () -> provider.authenticate(auth));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void testLockedUserUnlockAfterTimeout() {
        testUser.setLockTime(LocalDateTime.now().minusMinutes(35));
        testUser.setFailedAttempts(5);
        when(userRepository.findByUsernameAndDeletedFalse("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "encodedpassword")).thenReturn(true);

        Authentication auth = new TwoFactorAuthenticationToken("testuser", "password", null);

        Authentication result = provider.authenticate(auth);

        assertNotNull(result);
        assertEquals(0, testUser.getFailedAttempts());
        assertNull(testUser.getLockTime());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void testLoginIpPolicyBlocksUnlistedIp() {
        when(userRepository.findByUsernameAndDeletedFalse("testuser")).thenReturn(Optional.of(testUser));
        when(loginIpPolicyService.isAllowed("127.0.0.1")).thenReturn(false);

        Authentication auth = new TwoFactorAuthenticationToken("testuser", "password", null);

        assertThrows(BadCredentialsException.class, () -> provider.authenticate(auth));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void testLoginIpPolicyAllowsListedIp() {
        when(userRepository.findByUsernameAndDeletedFalse("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "encodedpassword")).thenReturn(true);

        Authentication auth = new TwoFactorAuthenticationToken("testuser", "password", null);

        Authentication result = provider.authenticate(auth);
        assertNotNull(result);
    }

    @Test
    void testLoginIpPolicyAllowsNormalizedIpv6Address() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("2001:db8::1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(userRepository.findByUsernameAndDeletedFalse("testuser")).thenReturn(Optional.of(testUser));
        when(loginIpPolicyService.isAllowed("2001:db8:0:0:0:0:0:1")).thenReturn(true);
        when(passwordEncoder.matches("password", "encodedpassword")).thenReturn(true);

        Authentication auth = new TwoFactorAuthenticationToken("testuser", "password", null);

        Authentication result = provider.authenticate(auth);
        assertNotNull(result);
    }

    @Test
    void testTwoFactorRequiredButMissing() {
        testUser.setMfaEnabled(true);
        when(userRepository.findByUsernameAndDeletedFalse("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "encodedpassword")).thenReturn(true);

        Authentication auth = new TwoFactorAuthenticationToken("testuser", "password", null);

        assertThrows(TwoFactorRequiredException.class, () -> provider.authenticate(auth));
    }

    @Test
    void testTwoFactorInvalidCode() {
        testUser.setMfaEnabled(true);
        testUser.setMfaSecret("secret");
        when(userRepository.findByUsernameAndDeletedFalse("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "encodedpassword")).thenReturn(true);
        when(twoFactorAuthService.isCodeValid("secret", "123456")).thenReturn(false);

        Authentication auth = new TwoFactorAuthenticationToken("testuser", "password", "123456");

        assertThrows(InvalidTwoFactorCodeException.class, () -> provider.authenticate(auth));
    }

    @Test
    void testUserNotActivatedThrowsException() {
        testUser.setStatus(UserStatus.UNACTIVATED);
        when(userRepository.findByUsernameAndDeletedFalse("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "encodedpassword")).thenReturn(true);
        when(jwtUtil.generateSetupToken("testuser")).thenReturn("setup-token-123");

        Authentication auth = new TwoFactorAuthenticationToken("testuser", "password", null);

        UserNotActivatedException ex = assertThrows(UserNotActivatedException.class, () -> provider.authenticate(auth));
        assertEquals("setup-token-123", ex.getSetupToken());
    }
}
