package com.reajason.noone.server.shell;

import com.reajason.noone.core.DotNetConnection;
import com.reajason.noone.core.JavaConnection;
import com.reajason.noone.core.NodeJsConnection;
import com.reajason.noone.core.ShellConnection;
import com.reajason.noone.core.client.Client;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.server.profile.ProfileEntity;
import com.reajason.noone.server.profile.ProfileMapper;
import com.reajason.noone.server.profile.ProfileRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ShellConnectionPool {

    @Resource
    private ProfileRepository profileRepository;

    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    private final ProfileMapper profileMapper;

    public ShellConnectionPool(ProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    public ShellConnection getOrCreateCached(Shell shell) {
        if (shell.getId() == null) {
            return createUncached(shell);
        }

        ProfileEntity profile = loadProfile(shell.getProfileId());
        ProfileEntity loaderProfile;
        if (shell.getStaging()) {
            loaderProfile = loadProfile(shell.getLoaderProfileId());
        } else {
            loaderProfile = null;
        }

        String signature = signature(shell, profile, loaderProfile);

        CacheEntry entry = cache.compute(shell.getId(), (id, existing) -> {
            if (existing != null && Objects.equals(existing.signature(), signature)) {
                return existing;
            }

            ShellConnection next = createConnection(shell, profile, loaderProfile);
            if (existing != null) {
                safeDisconnect(existing.connection());
            }
            return new CacheEntry(signature, next);
        });

        return entry.connection();
    }

    public ShellConnection createUncached(Shell shell) {
        ProfileEntity profile = loadProfile(shell.getProfileId());
        ProfileEntity loaderProfile = null;
        if (shell.getStaging()) {
            loaderProfile = loadProfile(shell.getLoaderProfileId());
        }
        return createConnection(shell, profile, loaderProfile);
    }

    public void evict(Long shellId) {
        if (shellId == null) {
            return;
        }
        CacheEntry removed = cache.remove(shellId);
        if (removed != null) {
            safeDisconnect(removed.connection());
        }
    }

    private ProfileEntity loadProfile(Long profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));
    }

    private ShellConnection createConnection(Shell shell, ProfileEntity profile, ProfileEntity loaderProfile) {
        Profile coreProfile = profileMapper.toProfile(profile);
        Client coreClient = ClientFactory.create(shell, coreProfile);
        ShellLanguage language = effectiveLanguage(shell);
        ShellConnection conn;
        if (shell.getStaging()) {
            Profile loaderProfileMapped = profileMapper.toProfile(loaderProfile);
            Client loaderClient = ClientFactory.create(shell, loaderProfileMapped);
            conn = switch (language) {
                case JAVA ->
                        new JavaConnection(coreClient, coreProfile, loaderClient, loaderProfileMapped, shell.getShellType());
                case NODEJS ->
                        new NodeJsConnection(coreClient, coreProfile, loaderClient, loaderProfileMapped, shell.getShellType());
                case DOTNET ->
                        new DotNetConnection(coreClient, coreProfile, loaderClient, loaderProfileMapped, shell.getShellType());
            };
        } else {
            conn = switch (language) {
                case JAVA -> new JavaConnection(coreClient, coreProfile);
                case NODEJS -> new NodeJsConnection(coreClient, coreProfile);
                case DOTNET -> new DotNetConnection(coreClient, coreProfile);
            };
        }
        return conn;
    }

    private ShellLanguage effectiveLanguage(Shell shell) {
        return shell.getLanguage() != null ? shell.getLanguage() : ShellLanguage.JAVA;
    }

    private String signature(Shell shell, ProfileEntity profile, ProfileEntity loaderProfile) {
        final String SIG_DELIMITER = "\n";
        Map<String, Object> effectiveConfig = ShellClientConfigCompat.effectiveConfig(shell);
        StringBuilder sb = new StringBuilder();
        sb.append("url=").append(shell.getUrl()).append(SIG_DELIMITER);
        sb.append("language=").append(effectiveLanguage(shell).getValue()).append(SIG_DELIMITER);
        sb.append("staging=").append(Boolean.TRUE.equals(shell.getStaging())).append(SIG_DELIMITER);
        sb.append("shellType=").append(shell.getShellType()).append(SIG_DELIMITER);
        sb.append("profileId=").append(shell.getProfileId()).append(SIG_DELIMITER);
        sb.append("protocolType=").append(profile.getProtocolType()).append(SIG_DELIMITER);
        sb.append("profileUpdatedAt=").append(profile.getUpdatedAt()).append(SIG_DELIMITER);
        sb.append("clientConfig=").append(ShellClientConfigCompat.stableConfigSignature(effectiveConfig)).append(SIG_DELIMITER);
        if (loaderProfile != null) {
            sb.append("loaderProfileId=").append(loaderProfile.getId()).append(SIG_DELIMITER);
            sb.append("loaderProfileUpdatedAt=").append(loaderProfile.getUpdatedAt()).append(SIG_DELIMITER);
        }
        return sb.toString();
    }

    private void safeDisconnect(ShellConnection connection) {
        try {
            connection.disconnect();
        } catch (Exception e) {
            log.debug("Failed to disconnect ShellConnection", e);
        }
    }

    private record CacheEntry(String signature, ShellConnection connection) {
    }
}
