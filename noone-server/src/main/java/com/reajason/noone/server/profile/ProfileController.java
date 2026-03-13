package com.reajason.noone.server.profile;

import com.reajason.noone.server.profile.dto.ProfileCreateRequest;
import com.reajason.noone.server.profile.dto.ProfileQueryRequest;
import com.reajason.noone.server.profile.dto.ProfileResponse;
import com.reajason.noone.server.profile.dto.ProfileUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping
    public ResponseEntity<ProfileResponse> create(@Valid @RequestBody ProfileCreateRequest request) {
        ProfileResponse response = profileService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(profileService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfileResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProfileUpdateRequest request) {
        ProfileResponse response = profileService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        profileService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<ProfileResponse>> query(ProfileQueryRequest request) {
        return ResponseEntity.ok(profileService.query(request));
    }
}


