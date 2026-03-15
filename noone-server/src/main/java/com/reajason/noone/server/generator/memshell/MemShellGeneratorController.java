package com.reajason.noone.server.generator.memshell;

import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.ServerFactory;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.packer.*;
import com.reajason.noone.Constants;
import com.reajason.noone.core.generator.JavaMemShellGenerator;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.server.generator.memshell.dto.MemShellGenerateRequest;
import com.reajason.noone.server.generator.memshell.dto.MemShellGenerateResponse;
import com.reajason.noone.server.profile.ProfileRepository;
import lombok.Data;
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
        ShellToolConfig shellToolConfig = toNoOneConfig(request);
        InjectorConfig injectorConfig = request.getInjectorConfig();
        MemShellResult generateResult = javaMemShellGenerator.generate(shellConfig, injectorConfig, shellToolConfig);
        Packers packers = Packers.fromName(request.getPackerSpec().getName());
        Packer<?> packer = packers.getInstance();
        if (packer instanceof JarPacker) {
            JarPackerConfig<?> jarPackerConfig = generateResult.toJarPackerConfig();
            return new MemShellGenerateResponse(generateResult, Base64.getEncoder().encodeToString(((JarPacker) packer).packBytes(jarPackerConfig)));
        }
        ClassPackerConfig classPackerConfig = generateResult.toClassPackerConfig();
        classPackerConfig.setCustomConfig(packer.resolveCustomConfig(request.getPackerSpec().getConfig()));
        return new MemShellGenerateResponse(generateResult, packer.pack(classPackerConfig));
    }


    public NoOneConfig toNoOneConfig(MemShellGenerateRequest request) {
        MemShellGenerateRequest.ShellToolConfigDTO shellToolConfig = request.getShellToolConfig();
        if (shellToolConfig.getStaging()) {
            request.getShellConfig().setShellTool(Constants.NO_ONE_STAGING);
            return NoOneConfig.builder()
                    .shellClassName(shellToolConfig.getShellClassName())
                    .loaderProfile(profileRepository.findById(shellToolConfig.getLoaderProfileId()).get())
                    .build();
        }
        request.getShellConfig().setShellTool(Constants.NO_ONE);
        return NoOneConfig.builder()
                .shellClassName(shellToolConfig.getShellClassName())
                .coreProfile(profileRepository.findById(shellToolConfig.getCoreProfileId()).get())
                .build();
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
    public List<PackerCategoryDTO> getPackers() {
        List<PackerCategoryDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<Packers>> entry : Packers.groupedPackers().entrySet()) {
            PackerCategoryDTO category = new PackerCategoryDTO();
            category.setName(entry.getKey());
            List<PackerOptionDTO> options = new ArrayList<>();
            for (Packers packer : entry.getValue()) {
                PackerOptionDTO option = new PackerOptionDTO();
                option.setName(packer.name());
                option.setOutputKind(packer.getOutputKind());
                option.setSchema(packer.getSchema());
                if(!packer.hasChildren()){
                    options.add(option);
                }
            }
            category.setPackers(options);
            result.add(category);
        }
        return result;
    }

    @RequestMapping("/config")
    public Map<String, Map<?, ?>> config() {
        return javaMemShellGenerator.getConfigMap();
    }

    @Data
    public static class PackerCategoryDTO {
        private String name;
        private List<PackerOptionDTO> packers;
    }

    @Data
    public static class PackerOptionDTO {
        private String name;
        private String outputKind;
        private boolean categoryAnchor;
        private Object schema;
    }
}