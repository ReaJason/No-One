package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.api.ResourceNotFoundException;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.user.dto.UserCreateRequest;
import com.reajason.noone.server.admin.user.dto.UserQueryRequest;
import com.reajason.noone.server.admin.user.dto.UserResponse;
import com.reajason.noone.server.admin.user.dto.UserUpdateRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;

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

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @Test
    void shouldCreateUser() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setPassword("secret123");
        request.setEmail("alice@example.com");
        request.setRoleIds(Set.of(10L));

        User entity = buildUser(1L, "alice");
        User saved = buildUser(1L, "alice");
        Role role = Role.builder().id(10L).name("Operator").build();
        UserResponse response = buildResponse(1L, "alice");

        when(userRepository.existsByUsernameAndDeletedFalse("alice")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(twoFactorAuthService.generateSecret()).thenReturn("mfa-secret");
        when(userMapper.toEntity(request)).thenReturn(entity);
        when(roleRepository.findAllById(Set.of(10L))).thenReturn(List.of(role));
        when(userRepository.save(entity)).thenReturn(saved);
        when(userMapper.toResponse(saved)).thenReturn(response);

        UserResponse created = userService.create(request);

        assertThat(created).isEqualTo(response);
        assertThat(entity.getPassword()).isEqualTo("encoded-secret");
        assertThat(entity.getRoles()).containsExactly(role);
        assertThat(entity.getStatus()).isEqualTo(UserStatus.UNACTIVATED);
        verify(userRepository).save(entity);
    }

    @Test
    void shouldThrowWhenCreatingDuplicateUsername() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");

        when(userRepository.existsByUsernameAndDeletedFalse("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名已存在：alice");
    }

    @Test
    void shouldGetUserById() {
        User user = buildUser(1L, "alice");
        UserResponse response = buildResponse(1L, "alice");

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        assertThat(userService.getById(1L)).isEqualTo(response);
    }

    @Test
    void shouldThrowWhenUserMissing() {
        when(userRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("用户不存在：99");
    }

    @Test
    void shouldUpdateUserBaseFieldsWithoutChangingRoles() {
        User user = buildUser(1L, "alice");
        Role role = Role.builder().id(10L).name("Operator").build();
        user.setRoles(Set.of(role));
        UserResponse response = buildResponse(1L, "alice");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("new@example.com");
        request.setStatus(UserStatus.DISABLED);

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse updated = userService.update(1L, request);

        assertThat(updated).isEqualTo(response);
        verify(userMapper).updateEntity(user, request);
        assertThat(user.getRoles()).containsExactly(role);
    }

    @Test
    void shouldSoftDeleteUser() {
        User user = buildUser(1L, "alice");
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));

        userService.delete(1L);

        assertThat(user.getDeleted()).isTrue();
        verify(userRepository).save(user);
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void shouldSyncRoles() {
        User user = buildUser(1L, "alice");
        Role admin = Role.builder().id(11L).name("Admin").deleted(false).build();
        Role deletedRole = Role.builder().id(12L).name("Deleted").deleted(true).build();
        UserResponse response = buildResponse(1L, "alice");

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(Set.of(11L, 12L))).thenReturn(List.of(admin, deletedRole));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse synced = userService.syncRoles(1L, Set.of(11L, 12L));

        assertThat(synced).isEqualTo(response);
        assertThat(user.getRoles()).containsExactly(admin);
    }

    @Test
    void shouldQueryUsersWithStableSort() {
        User user = buildUser(1L, "alice");
        UserResponse response = buildResponse(1L, "alice");

        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(userMapper.toResponse(user)).thenReturn(response);

        UserQueryRequest request = new UserQueryRequest();
        request.setPage(1);
        request.setPageSize(5);
        request.setSortBy("createdAt");
        request.setSortOrder("desc");

        Page<UserResponse> page = userService.query(request);

        verify(userRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(page.getContent()).containsExactly(response);
    }

    private User buildUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("encoded");
        user.setEmail(username + "@example.com");
        user.setStatus(UserStatus.ENABLED);
        user.setDeleted(Boolean.FALSE);
        user.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        user.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 1, 0));
        return user;
    }

    private UserResponse buildResponse(Long id, String username) {
        UserResponse response = new UserResponse();
        response.setId(id);
        response.setUsername(username);
        response.setEmail(username + "@example.com");
        response.setStatus(UserStatus.ENABLED);
        return response;
    }
}
