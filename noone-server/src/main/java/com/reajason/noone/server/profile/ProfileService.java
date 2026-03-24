package com.reajason.noone.server.profile;

import com.reajason.noone.server.api.ResourceNotFoundException;
import com.reajason.noone.server.audit.AuditAction;
import com.reajason.noone.server.audit.AuditLog;
import com.reajason.noone.server.audit.AuditModule;
import com.reajason.noone.server.profile.dto.ProfileCreateRequest;
import com.reajason.noone.server.profile.dto.ProfileQueryRequest;
import com.reajason.noone.server.profile.dto.ProfileResponse;
import com.reajason.noone.server.profile.dto.ProfileUpdateRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(ProfileRepository profileRepository, ProfileMapper profileMapper, PasswordEncoder passwordEncoder) {
        this.profileRepository = profileRepository;
        this.profileMapper = profileMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @AuditLog(module = AuditModule.PROFILE, action = AuditAction.CREATE, targetType = "Profile", targetId = "#result.id")
    public ProfileResponse create(ProfileCreateRequest request) {
        if (profileRepository.existsByNameAndDeletedFalse(request.getName())) {
            throw new IllegalArgumentException("Profile name already exists：" + request.getName());
        }
        if (StringUtils.isNoneBlank(request.getPassword())) {
            request.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        ProfileEntity profile = profileMapper.toEntity(request);
        ProfileEntity saved = profileRepository.save(profile);
        return profileMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getById(Long id) {
        ProfileEntity profile = profileRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found：" + id));
        return profileMapper.toResponse(profile);
    }

    @AuditLog(module = AuditModule.PROFILE, action = AuditAction.UPDATE, targetType = "Profile", targetId = "#id")
    public ProfileResponse update(Long id, ProfileUpdateRequest request) {
        ProfileEntity profile = profileRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found：" + id));

        if (request.getName() != null && profileRepository.existsByNameAndIdNotAndDeletedFalse(request.getName(), id)) {
            throw new IllegalArgumentException("Profile name already exists：" + request.getName());
        }

        if (StringUtils.isNoneBlank(request.getPassword())) {
            request.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        profileMapper.updateEntity(profile, request);
        ProfileEntity saved = profileRepository.save(profile);
        return profileMapper.toResponse(saved);
    }

    @AuditLog(module = AuditModule.PROFILE, action = AuditAction.DELETE, targetType = "Profile", targetId = "#id")
    public void delete(Long id) {
        ProfileEntity profile = profileRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found：" + id));
        profile.setDeleted(Boolean.TRUE);
        profileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public Page<ProfileResponse> query(ProfileQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy()
        ).and(Sort.by(Sort.Direction.ASC, "name"));

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<ProfileEntity> spec = ProfileSpecifications.hasName(request.getName())
                .and(ProfileSpecifications.notDeleted())
                .and(ProfileSpecifications.hasProtocolType(request.getProtocolType()));

        return profileRepository.findAll(spec, pageable).map(profileMapper::toResponse);
    }
}
