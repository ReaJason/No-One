package com.reajason.noone.server.profile;

import com.reajason.noone.server.profile.dto.ProfileCreateRequest;
import com.reajason.noone.server.profile.dto.ProfileQueryRequest;
import com.reajason.noone.server.profile.dto.ProfileResponse;
import com.reajason.noone.server.profile.dto.ProfileUpdateRequest;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfileService {

    @Resource
    private ProfileRepository profileRepository;

    @Resource
    private ProfileMapper profileMapper;

    public ProfileResponse create(ProfileCreateRequest request) {
        if (profileRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("配置名称已存在：" + request.getName());
        }
        Profile profile = profileMapper.toEntity(request);
        Profile saved = profileRepository.save(profile);
        return profileMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getById(Long id) {
        Profile profile = profileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在：" + id));
        return profileMapper.toResponse(profile);
    }

    public ProfileResponse update(Long id, ProfileUpdateRequest request) {
        Profile profile = profileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在：" + id));

        if (request.getName() != null && profileRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new IllegalArgumentException("配置名称已存在：" + request.getName());
        }

        profileMapper.updateEntity(profile, request);
        Profile saved = profileRepository.save(profile);
        return profileMapper.toResponse(saved);
    }

    public void delete(Long id) {
        if (!profileRepository.existsById(id)) {
            throw new IllegalArgumentException("配置不存在：" + id);
        }
        profileRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<ProfileResponse> query(ProfileQueryRequest request) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy()
        ).and(Sort.by(Sort.Direction.ASC, "name"));

        Pageable pageable = PageRequest.of(request.getPage(), request.getPageSize(), sort);

        Specification<Profile> spec = ProfileSpecifications.hasName(request.getName())
                .and(ProfileSpecifications.hasProtocolType(request.getProtocolType()));

        return profileRepository.findAll(spec, pageable).map(profileMapper::toResponse);
    }
}

