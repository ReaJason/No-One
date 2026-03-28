package com.reajason.noone.server.shell;

import com.reajason.noone.server.profile.ProfileRepository;
import com.reajason.noone.server.shell.dto.ShellCreateRequest;
import com.reajason.noone.server.shell.dto.ShellResponse;
import com.reajason.noone.server.shell.dto.ShellUpdateRequest;
import jakarta.annotation.Resource;
import org.mapstruct.*;

/**
 * Mapper for converting between Shell entities and DTOs
 *
 * @author ReaJason
 * @since 2025/12/27
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class ShellMapper {

    @Resource
    protected ProfileRepository profileRepository;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "remark", ignore = true)
    @Mapping(target = "lastOnlineAt", ignore = true)
    @Mapping(target = "lastOperatorId", ignore = true)
    @Mapping(target = "basicInfo", ignore = true)
    @Mapping(target = "os", ignore = true)
    @Mapping(target = "arch", ignore = true)
    @Mapping(target = "runtimeVersion", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "staging", ignore = true)
    @Mapping(target = "language", ignore = true)
    @Mapping(target = "clientConfig", ignore = true)
    public abstract Shell toEntity(ShellCreateRequest request);

    @AfterMapping
    protected void afterToEntity(@MappingTarget Shell shell, ShellCreateRequest request) {
        shell.setStaging(Boolean.TRUE.equals(request.getStaging()));
        shell.setLanguage(request.getLanguage() != null ? request.getLanguage() : ShellLanguage.JAVA);
        shell.setStatus(ShellStatus.DISCONNECTED);
        shell.setClientConfig(ShellClientConfigCompat.normalize(
                request.getClientConfig(),
                request.getProxyUrl(),
                request.getCustomHeaders(),
                request.getConnectTimeoutMs(),
                request.getReadTimeoutMs(),
                request.getSkipSslVerify(),
                request.getMaxRetries(),
                request.getRetryDelayMs()
        ));
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "remark", ignore = true)
    @Mapping(target = "lastOnlineAt", ignore = true)
    @Mapping(target = "lastOperatorId", ignore = true)
    @Mapping(target = "basicInfo", ignore = true)
    @Mapping(target = "os", ignore = true)
    @Mapping(target = "arch", ignore = true)
    @Mapping(target = "runtimeVersion", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "language", ignore = true)
    @Mapping(target = "loaderProfileId", ignore = true)
    @Mapping(target = "clientConfig", ignore = true)
    public abstract void updateEntity(@MappingTarget Shell shell, ShellUpdateRequest request);

    @AfterMapping
    protected void afterUpdateEntity(@MappingTarget Shell shell, ShellUpdateRequest request) {
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            shell.setStatus(ShellStatus.valueOf(request.getStatus().toUpperCase()));
        }
        if (request.getLanguage() != null) {
            shell.setLanguage(request.getLanguage());
        } else if (shell.getLanguage() == null) {
            shell.setLanguage(ShellLanguage.JAVA);
        }
        boolean staging = request.getStaging() != null
                ? request.getStaging()
                : Boolean.TRUE.equals(shell.getStaging());
        if (!staging) {
            shell.setLoaderProfileId(null);
        } else if (request.getLoaderProfileId() != null) {
            shell.setLoaderProfileId(request.getLoaderProfileId());
        }
        shell.setClientConfig(ShellClientConfigCompat.merge(
                shell.getClientConfig(),
                request.getClientConfig(),
                request.getProxyUrl(),
                request.getCustomHeaders(),
                request.getConnectTimeoutMs(),
                request.getReadTimeoutMs(),
                request.getSkipSslVerify(),
                request.getMaxRetries(),
                request.getRetryDelayMs()
        ));
    }

    @Mapping(target = "profileName", ignore = true)
    @Mapping(target = "proxyUrl", ignore = true)
    @Mapping(target = "customHeaders", ignore = true)
    @Mapping(target = "connectTimeoutMs", ignore = true)
    @Mapping(target = "readTimeoutMs", ignore = true)
    @Mapping(target = "skipSslVerify", ignore = true)
    @Mapping(target = "maxRetries", ignore = true)
    @Mapping(target = "retryDelayMs", ignore = true)
    public abstract ShellResponse toResponse(Shell shell);

    @AfterMapping
    protected void afterToResponse(@MappingTarget ShellResponse response, Shell shell) {
        if (response.getLanguage() == null) {
            response.setLanguage(ShellLanguage.JAVA);
        }
        ShellClientConfigCompat.populateLegacyFields(response, ShellClientConfigCompat.effectiveConfig(shell));
        if (shell.getProfileId() != null) {
            profileRepository.findById(shell.getProfileId())
                    .ifPresent(profile -> response.setProfileName(profile.getName()));
        }
    }
}
