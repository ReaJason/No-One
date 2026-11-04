package com.reajason.noone.server.generator.memshell;

import com.reajason.javaweb.InvalidInputException;
import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.PackerRegistry;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.packer.PackerCategory;
import com.reajason.javaweb.packer.PackerConstraints;
import com.reajason.noone.core.Constants;
import com.reajason.noone.core.generator.JavaMemShellGenerator;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.server.generator.memshell.dto.MemShellGenerateRequest;
import com.reajason.noone.server.generator.memshell.dto.MemShellGenerateResponse;
import com.reajason.noone.server.profile.ProfileEntity;
import com.reajason.noone.server.profile.ProfileMapper;
import com.reajason.noone.server.profile.ProfileRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author ReaJason
 * @since 2024/12/18
 */
@RestController
@RequestMapping("/api/memshell")
@CrossOrigin("*")
public class MemShellGeneratorController {

    private final JavaMemShellGenerator javaMemShellGenerator;
    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;

    public MemShellGeneratorController(ProfileRepository profileRepository, ProfileMapper profileMapper) {
        this.javaMemShellGenerator = new JavaMemShellGenerator();
        this.profileRepository = profileRepository;
        this.profileMapper = profileMapper;
    }

    @PostMapping("/generate")
    public MemShellGenerateResponse generate(@RequestBody MemShellGenerateRequest request) {
        ShellConfig shellConfig = request.getShellConfig();
        if (shellConfig == null) {
            throw new InvalidInputException("shellConfig is required");
        }
        NoOneConfig shellToolConfig = toNoOneConfig(request);
        InjectorConfig injectorConfig = request.getInjectorConfig();

        PackerConstraints constraints = PackerConstraints.forPacker(request.getPacker());
        constraints.constrainObfuscation(shellConfig.getObfuscation());
        String requestedClassName = injectorConfig == null ? null : injectorConfig.getInjectorClassName();
        String requiredClassName = constraints.enforceClassName(requestedClassName);
        if (!Objects.equals(requestedClassName, requiredClassName)) {
            if (injectorConfig == null) {
                injectorConfig = InjectorConfig.builder().build();
            }
            injectorConfig.setInjectorClassName(requiredClassName);
        }

        MemShellResult generateResult = javaMemShellGenerator.generate(shellConfig, injectorConfig, shellToolConfig);
        String packResult = PackerRegistry.getInstance().pack(constraints.getPackerName(), generateResult);
        return new MemShellGenerateResponse(generateResult, packResult);
    }

    public NoOneConfig toNoOneConfig(MemShellGenerateRequest request) {
        MemShellGenerateRequest.ShellToolConfigDTO shellToolConfig = request.getShellToolConfig();
        if (shellToolConfig == null) {
            throw new InvalidInputException("shellToolConfig is required");
        }
        if (Boolean.TRUE.equals(shellToolConfig.getStaging())) {
            request.getShellConfig().setShellTool(Constants.NO_ONE_STAGING);
            ProfileEntity profileEntity = findProfile(shellToolConfig.getLoaderProfileId());
            return NoOneConfig.builder()
                    .shellClassName(shellToolConfig.getShellClassName())
                    .loaderProfile(profileMapper.toProfile(profileEntity))
                    .build();
        }
        request.getShellConfig().setShellTool(Constants.NO_ONE);
        ProfileEntity profileEntity = findProfile(shellToolConfig.getCoreProfileId());
        return NoOneConfig.builder()
                .shellClassName(shellToolConfig.getShellClassName())
                .coreProfile(profileMapper.toProfile(profileEntity))
                .build();
    }

    private ProfileEntity findProfile(Long profileId) {
        if (profileId == null) {
            throw new InvalidInputException("profile id is required");
        }
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new InvalidInputException("Profile not found: " + profileId));
    }

    @RequestMapping("/config/servers")
    public Map<String, List<String>> getServers() {
        Map<String, List<String>> servers = new LinkedHashMap<>();
        List<String> supportedServers = javaMemShellGenerator.getSupportedServers();
        for (String supportedServer : supportedServers) {
            Set<String> supportedShellTypes = javaMemShellGenerator.getServer(supportedServer)
                    .getShellInjectorMapping().getSupportedShellTypes();
            servers.put(supportedServer, supportedShellTypes.stream().toList());
        }
        return servers;
    }

    @RequestMapping("/config/packers")
    public List<PackerCategory> getPackers() {
        return PackerRegistry.getInstance().getTree();
    }

    @RequestMapping("/config")
    public Map<String, Map<?, ?>> config() {
        return javaMemShellGenerator.getConfigMap();
    }
}
