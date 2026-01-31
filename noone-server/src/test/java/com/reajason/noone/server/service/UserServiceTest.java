package com.reajason.noone.server.service;

import com.reajason.noone.NooneApplication;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.admin.user.UserService;
import com.reajason.noone.server.admin.user.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = NooneApplication.class)
@Import(TestPGContainerConfiguration.class)
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Role adminRole;
    private Role userRole;

    @BeforeEach
    void setUp() {
        // Clean up existing data
        userRepository.deleteAll();
        roleRepository.deleteAll();

        // Create test roles
        adminRole = new Role();
        adminRole.setName("ADMIN");
        adminRole = roleRepository.save(adminRole);

        userRole = new Role();
        userRole.setName("USER");
        userRole = roleRepository.save(userRole);
    }

    @Test
    void createUser_ShouldCreateSuccessfully() {
        // Given
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setEnabled(true);

        // When
        UserResponse response = userService.create(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getId()).isNotNull();

        // Verify user is saved in database
        User savedUser = userRepository.findById(response.getId()).orElse(null);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getUsername()).isEqualTo("testuser");
        assertThat(passwordEncoder.matches("password123", savedUser.getPassword())).isTrue();
    }

    @Test
    void createUser_WithRoles_ShouldCreateWithRoles() {
        // Given
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setEnabled(true);
        request.setRoleIds(Set.of(adminRole.getId(), userRole.getId()));

        // When
        UserResponse response = userService.create(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).hasSize(2);
    }

    @Test
    void create_WithDuplicateUsername_ShouldThrowException() {
        // Given
        UserCreateRequest request1 = new UserCreateRequest();
        request1.setUsername("testuser");
        request1.setPassword("password123");
        userService.create(request1);

        UserCreateRequest request2 = new UserCreateRequest();
        request2.setUsername("testuser");
        request2.setPassword("password456");

        // When & Then
        assertThatThrownBy(() -> userService.create(request2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名已存在：testuser");
    }

    @Test
    void getUserById_ShouldReturnSuccessfully() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("encodedpassword")
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        // When
        UserResponse response = userService.getById(savedUser.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(savedUser.getId());
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.isEnabled()).isTrue();
    }

    @Test
    void getById_WithNonExistentId_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> userService.getById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户不存在：999");
    }

    @Test
    void updateUser_ShouldUpdateSuccessfully() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("oldpassword")
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEnabled(false);
        request.setRoleIds(Set.of(adminRole.getId()));

        // When
        UserResponse response = userService.update(savedUser.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.isEnabled()).isFalse();
        assertThat(response.getRoles()).hasSize(1);
        assertThat(response.getRoles().iterator().next().getName()).isEqualTo("ADMIN");

        // Verify password is updated
        User updatedUser = userRepository.findById(savedUser.getId()).orElse(null);
        assertThat(updatedUser).isNotNull();
    }

    @Test
    void updateUser_WithEmptyPassword_ShouldNotUpdatePassword() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password(passwordEncoder.encode("oldpassword"))
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setEnabled(false);

        // When
        UserResponse response = userService.update(savedUser.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isEnabled()).isFalse();

        // Verify password is not updated
        User updatedUser = userRepository.findById(savedUser.getId()).orElse(null);
        assertThat(updatedUser).isNotNull();
        assertThat(passwordEncoder.matches("oldpassword", updatedUser.getPassword())).isTrue();
    }

    @Test
    void update_WithNonExistentId_ShouldThrowException() {
        // Given
        UserUpdateRequest request = new UserUpdateRequest();

        // When & Then
        assertThatThrownBy(() -> userService.update(999L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户不存在：999");
    }

    @Test
    void deleteUser_ShouldDeleteUserSuccessfully() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("password")
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        // When
        userService.deleteUser(savedUser.getId());

        // Then
        assertThat(userRepository.existsById(savedUser.getId())).isFalse();
    }

    @Test
    void deleteUser_WithNonExistentId_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> userService.deleteUser(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户不存在：999");
    }

    @Test
    void query_ShouldReturnPaginatedResults() {
        // Given
        User user1 = User.builder()
                .username("user1")
                .password("password1")
                .enabled(true)
                .build();
        User user2 = User.builder()
                .username("user2")
                .password("password2")
                .enabled(false)
                .build();
        userRepository.save(user1);
        userRepository.save(user2);

        UserQueryRequest request = new UserQueryRequest();
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<UserResponse> result = userService.query(request);
        System.out.println(result.getClass().getName());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void query_WithUsernameFilter_ShouldReturnFilteredResults() {
        // Given
        User user1 = User.builder()
                .username("admin")
                .password("password1")
                .enabled(true)
                .build();
        User user2 = User.builder()
                .username("user")
                .password("password2")
                .enabled(true)
                .build();
        userRepository.save(user1);
        userRepository.save(user2);

        UserQueryRequest request = new UserQueryRequest();
        request.setUsername("admin");
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<UserResponse> result = userService.query(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("admin");
    }

    @Test
    void query_WithEnabledFilter_ShouldReturnFilteredResults() {
        // Given
        User user1 = User.builder()
                .username("user1")
                .password("password1")
                .enabled(true)
                .build();
        User user2 = User.builder()
                .username("user2")
                .password("password2")
                .enabled(false)
                .build();
        userRepository.save(user1);
        userRepository.save(user2);

        UserQueryRequest request = new UserQueryRequest();
        request.setEnabled(false);
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<UserResponse> result = userService.query(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("user2");
        assertThat(result.getContent().get(0).isEnabled()).isFalse();
    }

    @Test
    void query_WithRoleFilter_ShouldReturnFilteredResults() {
        // Given
        User user1 = User.builder()
                .username("admin")
                .password("password1")
                .enabled(true)
                .roles(Set.of(adminRole))
                .build();
        User user2 = User.builder()
                .username("user")
                .password("password2")
                .enabled(true)
                .roles(Set.of(userRole))
                .build();
        userRepository.save(user1);
        userRepository.save(user2);

        UserQueryRequest request = new UserQueryRequest();
        request.setRoleId(adminRole.getId());
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<UserResponse> result = userService.query(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("admin");
    }

    @Test
    void query_WithDateFilter_ShouldReturnFilteredResults() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        User user1 = User.builder()
                .username("user1")
                .password("password1")
                .enabled(true)
                .build();
        userRepository.save(user1);

        // Wait a bit to ensure different timestamps
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        User user2 = User.builder()
                .username("user2")
                .password("password2")
                .enabled(true)
                .build();
        userRepository.save(user2);

        UserQueryRequest request = new UserQueryRequest();
        request.setCreatedAfter(now);
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<UserResponse> result = userService.query(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void query_WithSorting_ShouldReturnSortedResults() {
        // Given
        User user1 = User.builder()
                .username("user1")
                .password("password1")
                .enabled(true)
                .build();
        User user2 = User.builder()
                .username("user2")
                .password("password2")
                .enabled(true)
                .build();
        userRepository.save(user1);
        userRepository.save(user2);

        UserQueryRequest request = new UserQueryRequest();
        request.setSortBy("username");
        request.setSortOrder("asc");
        request.setPage(0);
        request.setPageSize(10);

        // When
        Page<UserResponse> result = userService.query(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("user1");
        assertThat(result.getContent().get(1).getUsername()).isEqualTo("user2");
    }

    @Test
    void createUser_WithEmptyRoleIds_ShouldCreateWithoutRoles() {
        // Given
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setEnabled(true);
        request.setRoleIds(Set.of()); // Empty set

        // When
        UserResponse response = userService.create(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getRoles()).isEmpty();
    }

    @Test
    void updateUser_WithNullRoleIds_ShouldNotUpdateRoles() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("password")
                .enabled(true)
                .roles(Set.of(adminRole))
                .build();
        User savedUser = userRepository.save(user);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setRoleIds(null); // Null role IDs

        // When
        UserResponse response = userService.update(savedUser.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).hasSize(1); // Should keep existing roles
        assertThat(response.getRoles().iterator().next().getName()).isEqualTo("ADMIN");
    }

    @Test
    void updateUser_WithRoleIds_ShouldUpdateRoles() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("password")
                .enabled(true)
                .roles(Set.of(adminRole))
                .build();
        User savedUser = userRepository.save(user);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setRoleIds(Set.of(userRole.getId()));

        // When
        UserResponse response = userService.update(savedUser.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).hasSize(1);
        assertThat(response.getRoles().iterator().next().getName()).isEqualTo("USER");
    }

    @Test
    void resetPassword_ShouldResetPasswordSuccessfully() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password(passwordEncoder.encode("oldpassword"))
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("oldpassword");
        request.setNewPassword("newpassword123");

        // When
        UserResponse response = userService.resetPassword(savedUser.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(savedUser.getId());
        assertThat(response.getUsername()).isEqualTo("testuser");

        // Verify password is updated in database
        User updatedUser = userRepository.findById(savedUser.getId()).orElse(null);
        assertThat(updatedUser).isNotNull();
        assertThat(passwordEncoder.matches("newpassword123", updatedUser.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("oldpassword", updatedUser.getPassword())).isFalse();
    }

    @Test
    void resetPassword_WithIncorrectOldPassword_ShouldThrowException() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password(passwordEncoder.encode("oldpassword"))
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("wrongpassword");
        request.setNewPassword("newpassword123");

        // When & Then
        assertThatThrownBy(() -> userService.resetPassword(savedUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("旧密码不正确");
    }

    @Test
    void resetPassword_WithSameOldAndNewPassword_ShouldThrowException() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password(passwordEncoder.encode("oldpassword"))
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("oldpassword");
        request.setNewPassword("oldpassword");

        // When & Then
        assertThatThrownBy(() -> userService.resetPassword(savedUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("新密码不能与旧密码相同");
    }

    @Test
    void resetPassword_WithNonExistentId_ShouldThrowException() {
        // Given
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("oldpassword");
        request.setNewPassword("newpassword123");

        // When & Then
        assertThatThrownBy(() -> userService.resetPassword(999L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户不存在：999");
    }

    @Test
    void resetPassword_WithDisabledUser_ShouldStillAllowPasswordReset() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password(passwordEncoder.encode("oldpassword"))
                .enabled(false)
                .build();
        User savedUser = userRepository.save(user);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("oldpassword");
        request.setNewPassword("newpassword123");

        // When
        UserResponse response = userService.resetPassword(savedUser.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(savedUser.getId());
        assertThat(response.isEnabled()).isFalse(); // User should remain disabled

        // Verify password is updated
        User updatedUser = userRepository.findById(savedUser.getId()).orElse(null);
        assertThat(updatedUser).isNotNull();
        assertThat(passwordEncoder.matches("newpassword123", updatedUser.getPassword())).isTrue();
    }
}
