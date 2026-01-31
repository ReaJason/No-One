package com.reajason.noone.server.generator.memshell;

import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.ServerFactory;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.packer.AggregatePacker;
import com.reajason.javaweb.packer.JarPacker;
import com.reajason.javaweb.packer.Packer;
import com.reajason.javaweb.packer.Packers;
import com.reajason.noone.core.generator.JavaMemShellGenerator;
import com.reajason.noone.core.generator.NoOneConfig;
import com.reajason.noone.server.generator.memshell.dto.MemShellGenerateRequest;
import com.reajason.noone.server.generator.memshell.dto.MemShellGenerateResponse;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.ProfileRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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

    public MemShellGeneratorController(JavaMemShellGenerator javaMemShellGenerator, ProfileRepository profileRepository) {
        this.javaMemShellGenerator = javaMemShellGenerator;
        this.profileRepository = profileRepository;
    }

    @PostMapping("/generate")
    public MemShellGenerateResponse generate(@RequestBody MemShellGenerateRequest request) {
        ShellConfig shellConfig = request.getShellConfig();
        Optional<Profile> profile = profileRepository.findById(request.getShellToolConfig().getProfileId());
        ShellToolConfig shellToolConfig = new NoOneConfig(profile.get());
        InjectorConfig injectorConfig = request.getInjectorConfig();
        MemShellResult generateResult = javaMemShellGenerator.generate(shellConfig, injectorConfig, shellToolConfig);
        Packer packer = request.getPacker().getInstance();
        if (packer instanceof AggregatePacker) {
            return new MemShellGenerateResponse(generateResult, ((AggregatePacker) packer).packAll(generateResult.toClassPackerConfig()));
        }
        if (packer instanceof JarPacker) {
            return new MemShellGenerateResponse(generateResult, Base64.getEncoder().encodeToString(((JarPacker) packer).packBytes(generateResult.toJarPackerConfig())));
        }
        return new MemShellGenerateResponse(generateResult, packer.pack(generateResult.toClassPackerConfig()));
    }

    @RequestMapping("/config/servers")
    public Map<String, List<String>> getServers() {
        Map<String, List<String>> servers = new LinkedHashMap<>();
        List<String> supportedServers = ServerFactory.getSupportedServers();
        for (String supportedServer : supportedServers) {
            Set<String> supportedShellTypes = ServerFactory.getServer(supportedServer)
                    .getShellInjectorMapping().getSupportedShellTypes();
            servers.put(supportedServer, supportedShellTypes.stream().toList());
        }
        return servers;
    }

    @RequestMapping("/config/packers")
    public List<String> getPackers() {
        return Arrays.stream(Packers.values())
                .filter(packers -> packers.getParentPacker() == null)
                .map(Packers::name).toList();
    }

    @RequestMapping("/config")
    public Map<String, Map<?, ?>> config() {
        return javaMemShellGenerator.getConfigMap();
    }
}