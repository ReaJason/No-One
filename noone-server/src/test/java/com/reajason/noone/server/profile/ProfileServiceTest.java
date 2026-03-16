package com.reajason.noone.server.profile;

import com.reajason.noone.server.profile.config.HttpProtocolConfig;
import com.reajason.noone.server.profile.config.ProtocolType;
import com.reajason.noone.server.profile.dto.ProfileCreateRequest;
import com.reajason.noone.server.profile.dto.ProfileQueryRequest;
import com.reajason.noone.server.profile.dto.ProfileResponse;
import com.reajason.noone.server.profile.dto.ProfileUpdateRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private ProfileMapper profileMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ProfileService profileService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @Captor
    private ArgumentCaptor<ProfileCreateRequest> createRequestCaptor;

    @Captor
    private ArgumentCaptor<ProfileUpdateRequest> updateRequestCaptor;

    // --- Create ---

    @Test
    void shouldCreateProfile() {
        ProfileCreateRequest request = createRequest("new-profile");
        Profile entity = buildProfile(null, "new-profile");
        Profile saved = buildProfile(1L, "new-profile");
        ProfileResponse expectedResponse = buildResponse(1L, "new-profile");

        when(profileRepository.existsByNameAndDeletedFalse("new-profile")).thenReturn(false);
        when(passwordEncoder.encode("test-pass-123")).thenReturn("encoded-test-pass-123");
        when(profileMapper.toEntity(any(ProfileCreateRequest.class))).thenReturn(entity);
        when(profileRepository.save(entity)).thenReturn(saved);
        when(profileMapper.toResponse(saved)).thenReturn(expectedResponse);

        ProfileResponse response = profileService.create(request);

        assertThat(response).isEqualTo(expectedResponse);
        verify(passwordEncoder).encode("test-pass-123");
        verify(profileMapper).toEntity(createRequestCaptor.capture());
        assertThat(createRequestCaptor.getValue().getPassword()).isEqualTo("encoded-test-pass-123");
        verify(profileRepository).save(entity);
    }

    @Test
    void shouldThrowWhenCreatingDuplicateName() {
        when(profileRepository.existsByNameAndDeletedFalse("duplicate")).thenReturn(true);

        assertThatThrownBy(() -> profileService.create(createRequest("duplicate")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Profile name already exists：duplicate");

        verify(profileRepository, never()).save(any());
    }

    // --- GetById ---

    @Test
    void shouldGetProfileById() {
        Profile stored = buildProfile(10L, "get-test");
        ProfileResponse expectedResponse = buildResponse(10L, "get-test");

        when(profileRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(stored));
        when(profileMapper.toResponse(stored)).thenReturn(expectedResponse);

        ProfileResponse found = profileService.getById(10L);

        assertThat(found).isEqualTo(expectedResponse);
    }

    @Test
    void shouldThrowWhenGettingNonExistentProfile() {
        when(profileRepository.findByIdAndDeletedFalse(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getById(99999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Profile not found：99999");
    }

    // --- Update ---

    @Test
    void shouldUpdateProfileNameAndPassword() {
        Profile stored = buildProfile(20L, "before-update");
        Profile saved = buildProfile(20L, "after-update");
        ProfileResponse expectedResponse = buildResponse(20L, "after-update");

        when(profileRepository.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(stored));
        when(profileRepository.existsByNameAndIdNotAndDeletedFalse("after-update", 20L)).thenReturn(false);
        when(passwordEncoder.encode("new-pass-123")).thenReturn("encoded-new-pass-123");
        when(profileRepository.save(stored)).thenReturn(saved);
        when(profileMapper.toResponse(saved)).thenReturn(expectedResponse);

        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest();
        updateRequest.setName("after-update");
        updateRequest.setPassword("new-pass-123");

        ProfileResponse updated = profileService.update(20L, updateRequest);

        assertThat(updated).isEqualTo(expectedResponse);
        verify(passwordEncoder).encode("new-pass-123");
        verify(profileMapper).updateEntity(eq(stored), updateRequestCaptor.capture());
        assertThat(updateRequestCaptor.getValue().getName()).isEqualTo("after-update");
        assertThat(updateRequestCaptor.getValue().getPassword()).isEqualTo("encoded-new-pass-123");
        verify(profileRepository).save(stored);
    }

    @Test
    void shouldSkipNameCheckWhenNameIsNull() {
        Profile stored = buildProfile(21L, "keep-name");
        Profile saved = buildProfile(21L, "keep-name");
        ProfileResponse expectedResponse = buildResponse(21L, "keep-name");

        when(profileRepository.findByIdAndDeletedFalse(21L)).thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("new-pass-only")).thenReturn("encoded-new-pass-only");
        when(profileRepository.save(stored)).thenReturn(saved);
        when(profileMapper.toResponse(saved)).thenReturn(expectedResponse);

        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest();
        updateRequest.setPassword("new-pass-only");

        ProfileResponse updated = profileService.update(21L, updateRequest);

        assertThat(updated).isEqualTo(expectedResponse);
        verify(passwordEncoder).encode("new-pass-only");
        verify(profileRepository, never()).existsByNameAndIdNotAndDeletedFalse(any(), any());
        verify(profileMapper).updateEntity(eq(stored), updateRequestCaptor.capture());
        assertThat(updateRequestCaptor.getValue().getPassword()).isEqualTo("encoded-new-pass-only");
    }

    @Test
    void shouldAllowUpdatingSameName() {
        Profile stored = buildProfile(31L, "keep-same");
        Profile saved = buildProfile(31L, "keep-same");
        ProfileResponse expectedResponse = buildResponse(31L, "keep-same");

        when(profileRepository.findByIdAndDeletedFalse(31L)).thenReturn(Optional.of(stored));
        when(profileRepository.existsByNameAndIdNotAndDeletedFalse("keep-same", 31L)).thenReturn(false);
        when(profileRepository.save(stored)).thenReturn(saved);
        when(profileMapper.toResponse(saved)).thenReturn(expectedResponse);

        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest();
        updateRequest.setName("keep-same");

        ProfileResponse updated = profileService.update(31L, updateRequest);

        assertThat(updated).isEqualTo(expectedResponse);
    }

    @Test
    void shouldThrowWhenUpdatingToExistingName() {
        when(profileRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(buildProfile(30L, "name-b")));
        when(profileRepository.existsByNameAndIdNotAndDeletedFalse("name-a", 30L)).thenReturn(true);

        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest();
        updateRequest.setName("name-a");

        assertThatThrownBy(() -> profileService.update(30L, updateRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Profile name already exists：name-a");

        verify(profileRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentProfile() {
        when(profileRepository.findByIdAndDeletedFalse(99999L)).thenReturn(Optional.empty());

        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest();
        updateRequest.setName("irrelevant");

        assertThatThrownBy(() -> profileService.update(99999L, updateRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Profile not found：99999");
    }

    // --- Delete ---

    @Test
    void shouldDeleteProfile() {
        Profile stored = buildProfile(40L, "to-delete");
        when(profileRepository.findByIdAndDeletedFalse(40L)).thenReturn(Optional.of(stored));

        profileService.delete(40L);

        assertThat(stored.getDeleted()).isTrue();
        verify(profileRepository).save(stored);
        verify(profileRepository, never()).deleteById(any());
    }

    @Test
    void shouldThrowWhenDeletingNonExistentProfile() {
        when(profileRepository.findByIdAndDeletedFalse(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.delete(99999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Profile not found：99999");

        verify(profileRepository, never()).save(any());
    }

    @Test
    void shouldTreatDeletedProfileAsMissingWhenDeleting() {
        when(profileRepository.findByIdAndDeletedFalse(41L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.delete(41L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Profile not found：41");

        verify(profileRepository, never()).save(any());
    }

    // --- Query ---

    @Test
    void shouldQueryWithFiltersAndAscSort() {
        Profile alpha = buildProfile(50L, "alpha");
        Profile beta = buildProfile(51L, "beta");
        ProfileResponse alphaResp = buildResponse(50L, "alpha");
        ProfileResponse betaResp = buildResponse(51L, "beta");

        Page<Profile> repositoryPage = new PageImpl<>(List.of(alpha, beta));
        when(profileRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(repositoryPage);
        when(profileMapper.toResponse(alpha)).thenReturn(alphaResp);
        when(profileMapper.toResponse(beta)).thenReturn(betaResp);

        ProfileQueryRequest query = new ProfileQueryRequest();
        query.setName("a");
        query.setProtocolType("HTTP");
        query.setPage(1);
        query.setPageSize(5);
        query.setSortBy("createdAt");
        query.setSortOrder("asc");

        Page<ProfileResponse> page = profileService.query(query);

        verify(profileRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().toList())
                .extracting(order -> order.getProperty() + ":" + order.getDirection().name())
                .containsExactly("createdAt:ASC", "name:ASC");

        assertThat(page.getContent()).containsExactly(alphaResp, betaResp);
    }

    @Test
    void shouldQueryWithDescSort() {
        Profile alpha = buildProfile(50L, "alpha");
        ProfileResponse alphaResp = buildResponse(50L, "alpha");

        Page<Profile> repositoryPage = new PageImpl<>(List.of(alpha));
        when(profileRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(repositoryPage);
        when(profileMapper.toResponse(alpha)).thenReturn(alphaResp);

        ProfileQueryRequest query = new ProfileQueryRequest();
        query.setSortBy("updatedAt");
        query.setSortOrder("desc");

        Page<ProfileResponse> page = profileService.query(query);

        verify(profileRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getSort().toList())
                .extracting(order -> order.getProperty() + ":" + order.getDirection().name())
                .containsExactly("updatedAt:DESC", "name:ASC");

        assertThat(page.getContent()).containsExactly(alphaResp);
    }

    @Test
    void shouldQueryWithDefaultParameters() {
        Page<Profile> emptyPage = new PageImpl<>(List.of());
        when(profileRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        Page<ProfileResponse> page = profileService.query(new ProfileQueryRequest());

        verify(profileRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().toList())
                .extracting(order -> order.getProperty() + ":" + order.getDirection().name())
                .containsExactly("id:DESC", "name:ASC");

        assertThat(page.getContent()).isEmpty();
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

    private Profile buildProfile(Long id, String name) {
        Profile profile = new Profile();
        profile.setId(id);
        profile.setName(name);
        profile.setPassword("encoded-pass");
        profile.setProtocolType(ProtocolType.HTTP);
        HttpProtocolConfig config = new HttpProtocolConfig();
        config.setRequestMethod("GET");
        config.setResponseStatusCode(200);
        profile.setProtocolConfig(config);
        profile.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        profile.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 12, 0));
        profile.setDeleted(Boolean.FALSE);
        return profile;
    }

    private ProfileResponse buildResponse(Long id, String name) {
        ProfileResponse response = new ProfileResponse();
        response.setId(id);
        response.setName(name);
        response.setProtocolType(ProtocolType.HTTP);
        response.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        response.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 12, 0));
        return response;
    }
}
