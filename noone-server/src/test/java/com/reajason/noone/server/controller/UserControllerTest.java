package com.reajason.noone.server.controller;

import com.reajason.noone.NooneApplication;
import com.reajason.noone.server.TestPGContainerConfiguration;
import com.reajason.noone.server.admin.role.Role;
import com.reajason.noone.server.admin.role.RoleRepository;
import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.admin.user.dto.ResetPasswordRequest;
import com.reajason.noone.server.admin.user.dto.UserCreateRequest;
import com.reajason.noone.server.admin.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = NooneApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestPGContainerConfiguration.class)
@ActiveProfiles("test")
class UserControllerTest {
    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        roleRepository.deleteAll();
    }

    @Test
    void createUser_ShouldReturnCreated() {
        // Given
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setEnabled(true);

        // When
        ResponseEntity<UserResponse> response = restTemplate.postForEntity(
                "/api/users", request, UserResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserResponse userResponse = response.getBody();
        assertThat(userResponse).isNotNull();
        assertThat(userResponse.getUsername()).isEqualTo("testuser");
        assertThat(userResponse.isEnabled()).isTrue();
        assertThat(userResponse.getId()).isNotNull();
    }

    @Test
    void create_WithInvalidData_ShouldReturnBadRequest() {
        // Given
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("ab"); // Too short
        request.setPassword("123"); // Too short
        ResponseEntity<UserResponse> responseEntity = restTemplate.postForEntity(
                "/api/users", request, UserResponse.class);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_WithDuplicateUsername_ShouldReturnBadRequest() {
        // Given
        User existingUser = User.builder()
                .username("testuser")
                .password("password")
                .enabled(true)
                .build();
        userRepository.save(existingUser);

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        ResponseEntity<UserResponse> responseEntity = restTemplate.postForEntity(
                "/api/users", request, UserResponse.class);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    }

    @Test
    void getUserById_ShouldReturn() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("password")
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        // When
        ResponseEntity<UserResponse> response = restTemplate.getForEntity(
                "/api/users/" + savedUser.getId(), UserResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse userResponse = response.getBody();
        assertThat(userResponse).isNotNull();
        assertThat(userResponse.getUsername()).isEqualTo("testuser");
        assertThat(userResponse.isEnabled()).isTrue();
        assertThat(userResponse.getId()).isEqualTo(savedUser.getId());
    }

    @Test
    void getById_WithNonExistentId_ShouldReturnNotFound() {
        ResponseEntity<UserResponse> responseEntity = restTemplate.getForEntity(
                "/api/users/999", UserResponse.class);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/api/users/" + savedUser.getId() + "/reset-password",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                UserResponse.class);
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse userResponse = response.getBody();
        assertThat(userResponse).isNotNull();
        assertThat(userResponse.getId()).isEqualTo(savedUser.getId());
        assertThat(userResponse.getUsername()).isEqualTo("testuser");
    }

    @Test
    void resetPassword_WithIncorrectOldPassword_ShouldReturnBadRequest() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("oldpassword")
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("wrongpassword");
        request.setNewPassword("newpassword123");

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users/" + savedUser.getId() + "/reset-password",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resetPassword_WithSameOldAndNewPassword_ShouldReturnBadRequest() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("oldpassword")
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("oldpassword");
        request.setNewPassword("oldpassword");

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users/" + savedUser.getId() + "/reset-password",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resetPassword_WithNonExistentId_ShouldReturnBadRequest() {
        // Given
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("oldpassword");
        request.setNewPassword("newpassword123");

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users/999/reset-password",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resetPassword_WithInvalidRequest_ShouldReturnBadRequest() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("oldpassword")
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword(""); // Invalid: empty old password
        request.setNewPassword("newpassword123");

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users/" + savedUser.getId() + "/reset-password",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resetPassword_WithShortNewPassword_ShouldReturnBadRequest() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("oldpassword")
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("oldpassword");
        request.setNewPassword("123"); // Too short

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users/" + savedUser.getId() + "/reset-password",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resetPassword_WithLongNewPassword_ShouldReturnBadRequest() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("oldpassword")
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setOldPassword("oldpassword");
        request.setNewPassword("thispasswordistoolongandexceeds20characters"); // Too long

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users/" + savedUser.getId() + "/reset-password",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteUser_ShouldDelete() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("password")
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        // When
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/users/" + savedUser.getId(),
                HttpMethod.DELETE,
                null,
                Void.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userRepository.existsById(savedUser.getId())).isFalse();
    }

    @Test
    void delete_WithNonExistentId_ShouldReturnNotFound() {
        ResponseEntity<Void> responseEntity = restTemplate.exchange(
                "/api/users/999",
                HttpMethod.DELETE,
                null,
                Void.class);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void queryUsers_ShouldReturnPaginated() {
        // Given
        User user1 = User.builder()
                .username("user1")
                .password("password")
                .enabled(true)
                .build();
        User user2 = User.builder()
                .username("user2")
                .password("password")
                .enabled(false)
                .build();
        userRepository.save(user1);
        userRepository.save(user2);

        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/users?page=0&size=10", String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify the response contains expected content
        assertThat(response.getBody()).contains("user1");
        assertThat(response.getBody()).contains("user2");
        // Verify pagination metadata is present
        assertThat(response.getBody()).contains("totalElements");
        assertThat(response.getBody()).contains("totalPages");
    }

    @Test
    void queryUsers_WithFilters_ShouldReturnFiltered() {
        // Given
        User enabledUser = User.builder()
                .username("enableduser")
                .password("password")
                .enabled(true)
                .build();
        User disabledUser = User.builder()
                .username("disableduser")
                .password("password")
                .enabled(false)
                .build();
        userRepository.save(enabledUser);
        userRepository.save(disabledUser);

        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/users?enabled=true&username=enabled", String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify the response contains the filtered user
        assertThat(response.getBody()).contains("enableduser");
        // Verify the response doesn't contain the disabled user
        assertThat(response.getBody()).doesNotContain("disableduser");
    }

    @Test
    void toggleUserStatus_ShouldToggleUserStatus() {
        // Given
        User user = User.builder()
                .username("testuser")
                .password("password")
                .enabled(true)
                .build();
        User savedUser = userRepository.save(user);

        // When
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/api/users/" + savedUser.getId() + "/toggle-status",
                HttpMethod.PATCH,
                null,
                UserResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse userResponse = response.getBody();
        assertThat(userResponse).isNotNull();
        assertThat(userResponse.isEnabled()).isFalse(); // Should be toggled from true to false
    }

    @Test
    void toggleUserStatus_WithNonExistentId_ShouldReturnNotFound() {
        ResponseEntity<UserResponse> responseEntity = restTemplate.exchange(
                "/api/users/999/toggle-status",
                HttpMethod.PATCH,
                null,
                UserResponse.class);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createUser_WithRoles_ShouldCreateWithRoles() {
        // Given
        Role role = new Role();
        role.setName("ROLE_USER");
        Role savedRole = roleRepository.save(role);

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setEnabled(true);
        request.setRoleIds(Set.of(savedRole.getId()));

        // When
        ResponseEntity<UserResponse> response = restTemplate.postForEntity(
                "/api/users", request, UserResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserResponse userResponse = response.getBody();
        assertThat(userResponse).isNotNull();
        assertThat(userResponse.getRoles()).hasSize(1);
        assertThat(userResponse.getRoles().iterator().next().getName()).isEqualTo("ROLE_USER");
    }
}
