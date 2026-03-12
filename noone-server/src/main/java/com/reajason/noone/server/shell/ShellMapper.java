package com.reajason.noone.server.shell;

import com.reajason.noone.server.profile.ProfileRepository;
import com.reajason.noone.server.shell.dto.ShellCreateRequest;
import com.reajason.noone.server.shell.dto.ShellResponse;
import com.reajason.noone.server.shell.dto.ShellUpdateRequest;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Shell entities and DTOs
 *
 * @author ReaJason
 * @since 2025/12/27
 */
@Component
public class ShellMapper {

    @Resource
    private ProfileRepository profileRepository;

    public Shell toEntity(ShellCreateRequest request) {
        Shell shell = new Shell();
        shell.setName(request.getName());
        shell.setUrl(request.getUrl());
        shell.setLanguage(request.getLanguage() != null ? request.getLanguage() : ShellLanguage.JAVA);
        shell.setProjectId(request.getProjectId());
        shell.setStatus(ShellStatus.DISCONNECTED);

        // New fields
        shell.setProfileId(request.getProfileId());
        shell.setProxyUrl(request.getProxyUrl());
        shell.setCustomHeaders(request.getCustomHeaders());
        shell.setConnectTimeoutMs(request.getConnectTimeoutMs());
        shell.setReadTimeoutMs(request.getReadTimeoutMs());
        shell.setSkipSslVerify(request.getSkipSslVerify());
        shell.setMaxRetries(request.getMaxRetries());
        shell.setRetryDelayMs(request.getRetryDelayMs());

        return shell;
    }

    public void updateEntity(Shell shell, ShellUpdateRequest request) {
        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            shell.setUrl(request.getUrl());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            shell.setName(request.getName());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            shell.setStatus(ShellStatus.valueOf(request.getStatus().toUpperCase()));
        }
        if (request.getProjectId() != null) {
            shell.setProjectId(request.getProjectId());
        }

        if (request.getLanguage() != null) {
            shell.setLanguage(request.getLanguage());
        } else if (shell.getLanguage() == null) {
            shell.setLanguage(ShellLanguage.JAVA);
        }

        // New fields - profileId is required
        shell.setProfileId(request.getProfileId());
        if (request.getProxyUrl() != null) {
            shell.setProxyUrl(request.getProxyUrl());
        }
        if (request.getCustomHeaders() != null) {
            shell.setCustomHeaders(request.getCustomHeaders());
        }
        if (request.getConnectTimeoutMs() != null) {
            shell.setConnectTimeoutMs(request.getConnectTimeoutMs());
        }
        if (request.getReadTimeoutMs() != null) {
            shell.setReadTimeoutMs(request.getReadTimeoutMs());
        }
        if (request.getSkipSslVerify() != null) {
            shell.setSkipSslVerify(request.getSkipSslVerify());
        }
        if (request.getMaxRetries() != null) {
            shell.setMaxRetries(request.getMaxRetries());
        }
        if (request.getRetryDelayMs() != null) {
            shell.setRetryDelayMs(request.getRetryDelayMs());
        }
    }

    public ShellResponse toResponse(Shell shell) {
        ShellResponse response = new ShellResponse();
        response.setId(shell.getId());
        response.setUrl(shell.getUrl());
        response.setLanguage(shell.getLanguage() != null ? shell.getLanguage() : ShellLanguage.JAVA);
        response.setStatus(shell.getStatus().name());
        response.setProjectId(shell.getProjectId());
        response.setCreateTime(shell.getCreateTime());
        response.setConnectTime(shell.getConnectTime());
        response.setUpdateTime(shell.getUpdateTime());

        // New fields
        response.setProfileId(shell.getProfileId());
        response.setProxyUrl(shell.getProxyUrl());
        response.setCustomHeaders(shell.getCustomHeaders());
        response.setConnectTimeoutMs(shell.getConnectTimeoutMs());
        response.setReadTimeoutMs(shell.getReadTimeoutMs());
        response.setSkipSslVerify(shell.getSkipSslVerify());
        response.setMaxRetries(shell.getMaxRetries());
        response.setRetryDelayMs(shell.getRetryDelayMs());
        response.setBasicInfo(shell.getBasicInfo());

        // Fetch profile name for display
        if (shell.getProfileId() != null) {
            profileRepository.findById(shell.getProfileId())
                    .ifPresent(profile -> response.setProfileName(profile.getName()));
        }

        return response;
    }
}
