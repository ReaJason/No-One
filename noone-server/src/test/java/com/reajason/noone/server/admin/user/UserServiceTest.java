package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.user.dto.LoginLogQueryRequest;
import com.reajason.noone.server.admin.user.dto.LoginLogResponse;
import com.reajason.noone.server.admin.user.dto.ResetPasswordRequest;
import com.reajason.noone.server.admin.user.dto.UserCreateRequest;
import com.reajason.noone.server.api.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.reajason.noone.server.admin.auth.TwoFactorAuthService twoFactorAuthService;

    @Mock
    private LoginLogRepository loginLogRepository;

    @Mock
    private UserAuthorityResolver userAuthorityResolver;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldRejectResetPasswordWhenNewPasswordMatchesExistingPassword() {
        User user = User.builder()
                .id(10L)
                .username("target-user")
                .password("encoded-old")
                .email("target@example.com")
                .status(UserStatus.ENABLED)
                .build();
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("OldPass1!");

        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass1!", "encoded-old")).thenReturn(true);

        assertThatThrownBy(() -> userService.resetPassword(10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("新密码不能与旧密码相同");

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNotExposeWritableStatusOnCreateRequest() {
        assertThat(UserCreateRequest.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("status");

        assertThat(UserCreateRequest.class.getMethods())
                .extracting(Method::getName)
                .doesNotContain("getStatus", "setStatus");
    }

    @Test
    void shouldNotExposeDeviceInfoOnLoginLogResponse() {
        assertThat(LoginLogResponse.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("deviceInfo");
    }

    @Test
    void shouldExposePagedLoginLogQueryMethod() {
        assertThat(Arrays.stream(UserService.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("getLoginLogs") && method.getParameterCount() == 2))
                .isTrue();
    }

    @Test
    void shouldRejectMissingUserWhenLoadingLoginLogs() {
        LoginLogQueryRequest request = new LoginLogQueryRequest();
        when(userRepository.findByIdAndDeletedFalse(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getLoginLogs(404L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("用户不存在：404");

        verifyNoInteractions(loginLogRepository);
    }

    @Test
    void shouldQueryPagedLoginLogsAndMapResponse() {
        User user = User.builder()
                .id(10L)
                .username("target-user")
                .status(UserStatus.ENABLED)
                .build();
        LoginLog log = LoginLog.builder()
                .id(100L)
                .userId(10L)
                .username("target-user")
                .sessionId("session-1")
                .ipAddress("10.0.0.1")
                .userAgent("Mozilla/5.0")
                .browser("Chrome")
                .os("Linux")
                .status(LoginLog.LoginStatus.SUCCESS)
                .failReason(null)
                .loginTime(LocalDateTime.of(2026, 3, 19, 12, 0))
                .build();
        LoginLogQueryRequest request = new LoginLogQueryRequest();
        request.setStatus(LoginLog.LoginStatus.SUCCESS);
        request.setIpAddress("10.0.0.1");
        request.setSessionId("session-1");
        request.setLoginTimeAfter(LocalDateTime.of(2026, 3, 19, 0, 0));
        request.setLoginTimeBefore(LocalDateTime.of(2026, 3, 20, 0, 0));
        request.setPage(1);
        request.setPageSize(5);

        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(user));
        when(loginLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<LoginLogResponse> response = userService.getLoginLogs(10L, request);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getSessionId()).isEqualTo("session-1");
        assertThat(response.getContent().get(0).getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(response.getContent().get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getContent().get(0).getBrowser()).isEqualTo("Chrome");
        assertThat(response.getContent().get(0).getOs()).isEqualTo("Linux");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(loginLogRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("loginTime")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("loginTime").isDescending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("id").isDescending()).isTrue();
    }
}
